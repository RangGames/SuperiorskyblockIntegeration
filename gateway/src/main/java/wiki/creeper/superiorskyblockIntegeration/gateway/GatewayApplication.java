package wiki.creeper.superiorskyblockIntegeration.gateway;

import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import wiki.creeper.superiorskyblockIntegeration.api.PlayerIslandService;
import wiki.creeper.superiorskyblockIntegeration.common.ComponentLifecycle;
import wiki.creeper.superiorskyblockIntegeration.config.PluginConfig;
import wiki.creeper.superiorskyblockIntegeration.gateway.cache.PlayerIslandCache;
import wiki.creeper.superiorskyblockIntegeration.gateway.bridge.SuperiorSkyblockBridge;
import wiki.creeper.superiorskyblockIntegeration.gateway.bridge.SuperiorSkyblockBridgeFactory;
import wiki.creeper.superiorskyblockIntegeration.gateway.idempotency.IdempotencyService;
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

    private RedisChannels channels;
    private MessageSecurity security;
    private ExecutorService workerPool;
    private GatewaySubscriber subscriber;
    private Thread subscriptionThread;
    private GatewayRequestRouter requestRouter;
    private IdempotencyService idempotency;
    private SuperiorSkyblockBridge bridge;
    private GatewayEventPublisher eventPublisher;
    private GatewaySuperiorSkyblockEventListener ssbListener;
    private PlayerIslandCache islandCache;
    private boolean islandServiceRegistered;

    public GatewayApplication(JavaPlugin plugin, PluginConfig config, RedisManager redisManager) {
        this.plugin = plugin;
        this.config = config;
        this.redisManager = redisManager;
    }

    @Override
    public void start() {
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
        this.idempotency = new IdempotencyService(redisManager, Duration.ofMinutes(10), plugin.getLogger());
        this.eventPublisher = new GatewayEventPublisher(plugin, redisManager, channels, security);
        this.requestRouter = new GatewayRequestRouter(plugin, redisManager, channels, security, idempotency, config, bridge, eventPublisher, islandCache);

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
        this.subscriber = new GatewaySubscriber(plugin, security, requestRouter, workerPool);

        this.subscriptionThread = new Thread(() -> requestRouter.runSubscription(subscriber, channels.requestPattern()),
                "SSB2-Gateway-Subscription");
        subscriptionThread.setDaemon(true);
        subscriptionThread.start();

        if (bridge.isAvailable()) {
            this.ssbListener = new GatewaySuperiorSkyblockEventListener(plugin, bridge, eventPublisher, islandCache);
            plugin.getServer().getPluginManager().registerEvents(ssbListener, plugin);
        }

        plugin.getLogger().info("Gateway component started; waiting for requests on pattern " + channels.requestPattern());
    }

    @Override
    public void stop() {
        if (subscriber != null) {
            try {
                subscriber.gracefulShutdown();
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Error while shutting down gateway subscriber", ex);
            }
        }
        if (workerPool != null) {
            workerPool.shutdownNow();
        }
        if (subscriptionThread != null) {
            subscriptionThread.interrupt();
        }
        if (ssbListener != null) {
            ssbListener.shutdown();
        }
        if (islandServiceRegistered && islandCache != null) {
            plugin.getServer().getServicesManager().unregister(islandCache);
            islandServiceRegistered = false;
        }
    }
}
