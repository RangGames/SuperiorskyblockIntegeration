package wiki.creeper.superiorskyblockIntegeration.gateway;

import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import wiki.creeper.superiorskyblockIntegeration.api.NetworkSkyblockService;
import wiki.creeper.superiorskyblockIntegeration.api.PlayerIslandService;
import wiki.creeper.superiorskyblockIntegeration.api.PlayerMetadataService;
import wiki.creeper.superiorskyblockIntegeration.api.PlayerProfileService;
import wiki.creeper.superiorskyblockIntegeration.client.cache.ClientCache;
import wiki.creeper.superiorskyblockIntegeration.client.commands.FarmCommand;
import wiki.creeper.superiorskyblockIntegeration.client.listeners.ChestSortIntegrationListener;
import wiki.creeper.superiorskyblockIntegeration.client.listeners.QuestProgressListener;
import wiki.creeper.superiorskyblockIntegeration.client.menu.IslandMenuManager;
import wiki.creeper.superiorskyblockIntegeration.client.services.FarmHistoryService;
import wiki.creeper.superiorskyblockIntegeration.client.services.FarmRankingService;
import wiki.creeper.superiorskyblockIntegeration.client.services.FarmRewardService;
import wiki.creeper.superiorskyblockIntegeration.client.services.FarmShopService;
import wiki.creeper.superiorskyblockIntegeration.client.services.QuestProgressService;
import wiki.creeper.superiorskyblockIntegeration.client.lang.Messages;
import wiki.creeper.superiorskyblockIntegeration.common.ComponentLifecycle;
import wiki.creeper.superiorskyblockIntegeration.config.PluginConfig;
import wiki.creeper.superiorskyblockIntegeration.gateway.cache.PlayerIslandCache;
import wiki.creeper.superiorskyblockIntegeration.gateway.bridge.SuperiorSkyblockBridge;
import wiki.creeper.superiorskyblockIntegeration.gateway.bridge.SuperiorSkyblockBridgeFactory;
import wiki.creeper.superiorskyblockIntegeration.gateway.data.GatewayDataService;
import wiki.creeper.superiorskyblockIntegeration.gateway.data.GatewayDatabase;
import wiki.creeper.superiorskyblockIntegeration.gateway.data.GatewayHeadDataService;
import wiki.creeper.superiorskyblockIntegeration.gateway.data.GatewayPlayerMetadataService;
import wiki.creeper.superiorskyblockIntegeration.gateway.data.GatewayPlayerProfileService;
import wiki.creeper.superiorskyblockIntegeration.gateway.data.GatewayQuestService;
import wiki.creeper.superiorskyblockIntegeration.gateway.data.GatewayRankingService;
import wiki.creeper.superiorskyblockIntegeration.gateway.data.SqlGatewayDataService;
import wiki.creeper.superiorskyblockIntegeration.gateway.idempotency.IdempotencyService;
import wiki.creeper.superiorskyblockIntegeration.gateway.GatewayBusListener;
import wiki.creeper.superiorskyblockIntegeration.redis.HmacSigner;
import wiki.creeper.superiorskyblockIntegeration.redis.MessageSecurity;
import wiki.creeper.superiorskyblockIntegeration.redis.RedisChannels;
import wiki.creeper.superiorskyblockIntegeration.redis.RedisManager;

/**
 * Gateway stands between Redis and the SuperiorSkyblock2 API on the authoritative server.
 */
public final class GatewayApplication implements ComponentLifecycle {

    private final JavaPlugin plugin;
    private final PluginConfig config;
    private final RedisManager redisManager;
    private final Messages messages;

    private RedisChannels channels;
    private MessageSecurity security;
    private ExecutorService workerPool;
    private GatewaySubscriber subscriber;
    private StatefulRedisPubSubConnection<String, String> subscriptionConnection;
    private GatewayRequestRouter requestRouter;
    private IdempotencyService idempotency;
    private SuperiorSkyblockBridge bridge;
    private GatewayEventPublisher eventPublisher;
    private GatewaySuperiorSkyblockEventListener ssbListener;
    private wiki.creeper.superiorskyblockIntegeration.gateway.listeners.GatewayFurnaceListener furnaceListener;
    private PlayerIslandCache islandCache;
    private boolean islandServiceRegistered;
    private GatewayDataService dataService;
    private GatewayDatabase database;
    private GatewayQuestService questService;
    private GatewayRankingService rankingService;
    private PlayerMetadataService metadataService;
    private GatewayHeadDataService headDataService;
    private GatewayBusListener busListener;
    private StatefulRedisPubSubConnection<String, String> busConnection;
    private wiki.creeper.superiorskyblockIntegeration.api.PlayerProfileService profileService;
    private NetworkSkyblockService networkService;
    private ClientCache cache;
    private IslandMenuManager menuManager;
    private FarmRankingService farmRankingService;
    private FarmHistoryService farmHistoryService;
    private FarmRewardService farmRewardService;
    private FarmShopService farmShopService;
    private QuestProgressService questProgressService;
    private QuestProgressListener questProgressListener;
    private ChestSortIntegrationListener chestSortListener;

    public GatewayApplication(JavaPlugin plugin, PluginConfig config, RedisManager redisManager, Messages messages) {
        this.plugin = plugin;
        this.config = config;
        this.redisManager = redisManager;
        this.messages = messages;
    }

    @Override
    public void start() {
        wiki.creeper.superiorskyblockIntegeration.common.quest.QuestRewards.configure(config.quest());
        this.channels = new RedisChannels(config.channels().prefix());
        this.security = new MessageSecurity(new HmacSigner(config.security().hmacSecret()));
        this.bridge = SuperiorSkyblockBridgeFactory.create(plugin);
        this.islandCache = new PlayerIslandCache(bridge);
        if (bridge.isAvailable()) {
            try {
                this.islandCache.loadSnapshot(bridge.snapshotPlayerIslands());
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to load initial player→island snapshot", ex);
            }
            plugin.getServer().getServicesManager().register(PlayerIslandService.class, islandCache, plugin, ServicePriority.Normal);
            islandServiceRegistered = true;
        } else {
            plugin.getLogger().warning("SuperiorSkyblock bridge not available; PlayerIslandService will not be registered");
        }
        this.database = new GatewayDatabase(plugin, config.gateway().database());
        this.idempotency = new IdempotencyService(Duration.ofMinutes(10), plugin.getLogger());
        this.eventPublisher = new GatewayEventPublisher(plugin, redisManager, channels, security, config.redis().messageCompressionThreshold(), messages, config.logging().redisDebug());
        this.dataService = new SqlGatewayDataService(database, plugin.getLogger());
        this.rankingService = new GatewayRankingService(plugin, database, bridge, plugin.getLogger());
        this.questService = new GatewayQuestService(dataService, rankingService, bridge);
        this.metadataService = new GatewayPlayerMetadataService(dataService);
        this.profileService = new GatewayPlayerProfileService(dataService);
        this.headDataService = new GatewayHeadDataService(redisManager, channels, plugin.getLogger(), metadataService);
        this.requestRouter = new GatewayRequestRouter(plugin, redisManager, channels, security, idempotency, config, bridge, eventPublisher, islandCache, dataService, rankingService, questService, metadataService);
        this.busListener = new GatewayBusListener(plugin, plugin.getLogger(), redisManager, channels, headDataService);
        this.networkService = new GatewayNetworkService(plugin, config, requestRouter);
        plugin.getServer().getServicesManager().register(NetworkSkyblockService.class, networkService, plugin, ServicePriority.High);
        this.cache = new ClientCache(config.client().cache());
        this.menuManager = new IslandMenuManager(plugin, networkService, cache);
        this.farmRankingService = new FarmRankingService(networkService);
        this.menuManager.setFarmRankingService(farmRankingService);
        this.farmHistoryService = new FarmHistoryService(networkService);
        this.menuManager.setFarmHistoryService(farmHistoryService);
        this.farmRewardService = new FarmRewardService(networkService);
        this.menuManager.setFarmRewardService(farmRewardService);
        this.farmShopService = new FarmShopService(networkService);
        this.menuManager.setFarmShopService(farmShopService);
        this.menuManager.setMetadataService(metadataService);
        this.questProgressService = new QuestProgressService(plugin, networkService);
        this.menuManager.setQuestProgressService(questProgressService);
        this.questProgressListener = new QuestProgressListener(questProgressService);
        plugin.getServer().getPluginManager().registerEvents(questProgressListener, plugin);
        ChestSortIntegrationListener chestSort = new ChestSortIntegrationListener(plugin);
        if (chestSort.isEnabled()) {
            this.chestSortListener = chestSort;
            plugin.getServer().getPluginManager().registerEvents(chestSortListener, plugin);
        }
        plugin.getServer().getServicesManager().register(PlayerMetadataService.class, metadataService, plugin, ServicePriority.Normal);
        plugin.getServer().getServicesManager().register(wiki.creeper.superiorskyblockIntegeration.api.PlayerProfileService.class, profileService, plugin, ServicePriority.Normal);
        this.furnaceListener = new wiki.creeper.superiorskyblockIntegeration.gateway.listeners.GatewayFurnaceListener(plugin, config.quest());
        if (furnaceListener != null && furnaceListener.isEnabled()) {
            plugin.getServer().getPluginManager().registerEvents(furnaceListener, plugin);
        } else {
            furnaceListener = null;
        }

        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "SSB2-Gateway-Worker-" + counter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        };

        this.workerPool = Executors.newFixedThreadPool(config.gateway().concurrency().workers(), factory);
        this.subscriptionConnection = redisManager.connectPubSub();
        this.subscriber = new GatewaySubscriber(plugin, security, requestRouter, workerPool);
        subscriber.register(subscriptionConnection, channels.requestPattern());
        this.busConnection = redisManager.connectPubSub();
        busListener.register(busConnection);

        if (bridge.isAvailable()) {
            this.ssbListener = new GatewaySuperiorSkyblockEventListener(plugin, bridge, eventPublisher, islandCache);
            plugin.getServer().getPluginManager().registerEvents(ssbListener, plugin);
        }

        registerCommands();

        plugin.getLogger().info("Gateway component started; waiting for requests on pattern " + channels.requestPattern());
    }

    private void registerCommands() {
        if (networkService == null || menuManager == null) {
            return;
        }

        FarmCommand farmCommand = new FarmCommand(plugin, networkService, cache, menuManager, config.client().velocity(), messages);
        PluginCommand mainCommand = plugin.getServer().getPluginCommand("is");
        if (mainCommand == null) {
            plugin.getLogger().warning("Command '/is' is not registered; farm command bridge unavailable.");
        } else {
            mainCommand.setExecutor(farmCommand);
            mainCommand.setTabCompleter(farmCommand);
        }
    }

    @Override
    public void stop() {
        if (questProgressListener != null) {
            HandlerList.unregisterAll(questProgressListener);
            questProgressListener = null;
        }
        if (chestSortListener != null) {
            HandlerList.unregisterAll(chestSortListener);
            chestSortListener = null;
        }
        if (menuManager != null) {
            menuManager.shutdown();
            menuManager = null;
        }
        questProgressService = null;
        farmRewardService = null;
        farmShopService = null;
        farmHistoryService = null;
        farmRankingService = null;
        cache = null;
        if (subscriber != null) {
            try {
                subscriber.gracefulShutdown();
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Error while shutting down gateway subscriber", ex);
            }
            subscriber = null;
        }
        if (workerPool != null) {
            workerPool.shutdownNow();
        }
        if (ssbListener != null) {
            ssbListener.shutdown();
        }
        if (furnaceListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(furnaceListener);
            furnaceListener = null;
        }
        if (islandServiceRegistered && islandCache != null) {
            plugin.getServer().getServicesManager().unregister(islandCache);
            islandServiceRegistered = false;
        }
        if (networkService != null) {
            plugin.getServer().getServicesManager().unregister(networkService);
            networkService = null;
        }
        if (metadataService != null) {
            plugin.getServer().getServicesManager().unregister(metadataService);
            metadataService = null;
        }
        if (profileService != null) {
            plugin.getServer().getServicesManager().unregister(profileService);
            profileService = null;
        }
        if (rankingService != null) {
            rankingService.shutdown();
            rankingService = null;
        }
        if (database != null) {
            database.close();
            database = null;
        }
        if (busConnection != null) {
            try {
                if (busListener != null) {
                    busConnection.removeListener(busListener);
                }
                busConnection.close();
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Error while closing bus pub/sub connection", ex);
            }
            busConnection = null;
            busListener = null;
        }
        subscriptionConnection = null;
    }
}
