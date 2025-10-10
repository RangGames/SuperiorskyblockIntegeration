package wiki.creeper.superiorskyblockIntegeration.client;

import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import wiki.creeper.superiorskyblockIntegeration.api.NetworkSkyblockService;
import wiki.creeper.superiorskyblockIntegeration.api.PlayerMetadataService;
import wiki.creeper.superiorskyblockIntegeration.api.PlayerProfileService;
import wiki.creeper.superiorskyblockIntegeration.client.cache.ClientCache;
import wiki.creeper.superiorskyblockIntegeration.client.commands.FarmCommand;
import wiki.creeper.superiorskyblockIntegeration.client.listeners.ClientPlayerListener;
import wiki.creeper.superiorskyblockIntegeration.client.listeners.ChestSortIntegrationListener;
import wiki.creeper.superiorskyblockIntegeration.client.listeners.QuestProgressListener;
import wiki.creeper.superiorskyblockIntegeration.client.messaging.ClientPendingRequests;
import wiki.creeper.superiorskyblockIntegeration.client.messaging.ClientRedisListener;
import wiki.creeper.superiorskyblockIntegeration.client.messaging.ClientRequestDispatcher;
import wiki.creeper.superiorskyblockIntegeration.client.menu.IslandMenuManager;
import wiki.creeper.superiorskyblockIntegeration.client.lang.Messages;
import wiki.creeper.superiorskyblockIntegeration.client.services.ClientHeadDataService;
import wiki.creeper.superiorskyblockIntegeration.client.services.FarmRankingService;
import wiki.creeper.superiorskyblockIntegeration.client.services.FarmRewardService;
import wiki.creeper.superiorskyblockIntegeration.client.services.FarmShopService;
import wiki.creeper.superiorskyblockIntegeration.client.services.PlayerPresenceService;
import wiki.creeper.superiorskyblockIntegeration.client.services.NetworkPlayerMetadataService;
import wiki.creeper.superiorskyblockIntegeration.client.services.NetworkPlayerProfileService;
import wiki.creeper.superiorskyblockIntegeration.client.services.QuestProgressService;
import wiki.creeper.superiorskyblockIntegeration.client.services.FarmHistoryService;
import wiki.creeper.superiorskyblockIntegeration.common.ComponentLifecycle;
import wiki.creeper.superiorskyblockIntegeration.config.PluginConfig;
import wiki.creeper.superiorskyblockIntegeration.redis.HmacSigner;
import wiki.creeper.superiorskyblockIntegeration.redis.MessageSecurity;
import wiki.creeper.superiorskyblockIntegeration.redis.RedisChannels;
import wiki.creeper.superiorskyblockIntegeration.redis.RedisManager;

/**
 * Client component running on non-SSB servers that proxies player commands to the gateway.
 */
public final class ClientApplication implements ComponentLifecycle {

    private final JavaPlugin plugin;
    private final PluginConfig config;
    private final RedisManager redisManager;
    private final Messages messages;
    private PlayerPresenceService presenceService;

    private RedisChannels channels;
    private MessageSecurity security;
    private ClientPendingRequests pendingRequests;
    private ClientRedisListener listener;
    private ClientRequestDispatcher dispatcher;
    private ClientCache cache;
    private ClientNetworkService networkService;
    private IslandMenuManager menuManager;
    private ClientPlayerListener playerListener;
    private ChestSortIntegrationListener chestSortListener;
    private PlayerMetadataService metadataService;
    private ClientHeadDataService headDataService;
    private PlayerProfileService profileService;
    private QuestProgressService questProgressService;
    private QuestProgressListener questProgressListener;
    private FarmRewardService farmRewardService;
    private FarmShopService farmShopService;

    public ClientApplication(JavaPlugin plugin, PluginConfig config, RedisManager redisManager, Messages messages) {
        this.plugin = plugin;
        this.config = config;
        this.redisManager = redisManager;
        this.messages = messages;
    }

    @Override
    public void start() {
        wiki.creeper.superiorskyblockIntegeration.common.quest.QuestRewards.configure(config.quest());
        this.presenceService = new PlayerPresenceService(plugin);
        this.channels = new RedisChannels(config.channels().prefix());
        this.security = new MessageSecurity(new HmacSigner(config.security().hmacSecret()));
        this.cache = new ClientCache(config.client().cache());
        this.pendingRequests = new ClientPendingRequests(plugin);
        this.dispatcher = new ClientRequestDispatcher(plugin, config, redisManager, channels, security, pendingRequests);
        this.networkService = new ClientNetworkService(dispatcher);
        plugin.getServer().getServicesManager().register(NetworkSkyblockService.class, networkService, plugin, ServicePriority.Normal);
        this.menuManager = new IslandMenuManager(plugin, networkService, cache);
        FarmRankingService farmRankingService = new FarmRankingService(networkService);
        this.menuManager.setFarmRankingService(farmRankingService);
        FarmHistoryService farmHistoryService = new FarmHistoryService(networkService);
        this.menuManager.setFarmHistoryService(farmHistoryService);
        this.farmRewardService = new FarmRewardService(networkService);
        this.menuManager.setFarmRewardService(farmRewardService);
        this.farmShopService = new FarmShopService(networkService);
        this.menuManager.setFarmShopService(farmShopService);
        this.metadataService = new NetworkPlayerMetadataService(networkService);
        this.menuManager.setMetadataService(metadataService);
        this.profileService = new NetworkPlayerProfileService(networkService);
        this.headDataService = new ClientHeadDataService(plugin, redisManager, channels, metadataService);
        this.menuManager.setHeadDataService(headDataService);
        if (presenceService != null) {
            this.menuManager.setPresenceService(presenceService);
        }
        this.listener = new ClientRedisListener(plugin, security, channels, pendingRequests, cache, headDataService, menuManager, messages);
        this.playerListener = new ClientPlayerListener(plugin, networkService, headDataService);
        ChestSortIntegrationListener chestSort = new ChestSortIntegrationListener(plugin);
        if (chestSort.isEnabled()) {
            this.chestSortListener = chestSort;
        }
        this.questProgressService = new QuestProgressService(plugin, networkService);
        this.questProgressListener = new QuestProgressListener(questProgressService);
        this.menuManager.setQuestProgressService(questProgressService);

        plugin.getServer().getPluginManager().registerEvents(playerListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(questProgressListener, plugin);
        if (chestSortListener != null) {
            plugin.getServer().getPluginManager().registerEvents(chestSortListener, plugin);
        }
        plugin.getServer().getServicesManager().register(PlayerMetadataService.class, metadataService, plugin, ServicePriority.Normal);
        plugin.getServer().getServicesManager().register(PlayerProfileService.class, profileService, plugin, ServicePriority.Normal);

        StatefulRedisPubSubConnection<String, String> connection = redisManager.connectPubSub();
        listener.register(connection);

        registerCommands();
        plugin.getLogger().info("Client component ready; Redis prefix=" + channels.requestPattern());
    }

    private void registerCommands() {
        FarmCommand farmCommand = new FarmCommand(plugin,
                networkService,
                cache,
                menuManager,
                config.client().velocity(),
                messages);
        PluginCommand mainCommand = plugin.getCommand("is");
        if (mainCommand == null) {
            plugin.getLogger().severe("Command 'is' not defined in plugin.yml");
        } else {
            mainCommand.setExecutor(farmCommand);
            mainCommand.setTabCompleter(farmCommand);
        }
    }

    @Override
    public void stop() {
        if (listener != null) {
            listener.gracefulShutdown();
        }
        if (pendingRequests != null) {
            pendingRequests.failAll(new IllegalStateException("Plugin shutting down"));
        }
        if (menuManager != null) {
            menuManager.shutdown();
            menuManager = null;
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
        if (playerListener != null) {
            PlayerJoinEvent.getHandlerList().unregister(playerListener);
            playerListener = null;
        }
        if (questProgressListener != null) {
            HandlerList.unregisterAll(questProgressListener);
            questProgressListener = null;
        }
        if (chestSortListener != null) {
            HandlerList.unregisterAll(chestSortListener);
            chestSortListener = null;
        }
        questProgressService = null;
        farmRewardService = null;
        farmShopService = null;
    }
}
