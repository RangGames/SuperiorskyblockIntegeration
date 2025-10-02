package wiki.creeper.superiorskyblockIntegeration.client;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.Jedis;

import java.util.logging.Level;

import wiki.creeper.superiorskyblockIntegeration.api.NetworkSkyblockService;
import wiki.creeper.superiorskyblockIntegeration.client.cache.ClientCache;
import wiki.creeper.superiorskyblockIntegeration.client.commands.IslandCommand;
import wiki.creeper.superiorskyblockIntegeration.client.messaging.ClientPendingRequests;
import wiki.creeper.superiorskyblockIntegeration.client.messaging.ClientRedisListener;
import wiki.creeper.superiorskyblockIntegeration.client.messaging.ClientRequestDispatcher;
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

    private RedisChannels channels;
    private MessageSecurity security;
    private ClientPendingRequests pendingRequests;
    private ClientRedisListener listener;
    private ClientRequestDispatcher dispatcher;
    private ClientCache cache;
    private Thread subscriptionThread;
    private ClientNetworkService networkService;

    public ClientApplication(JavaPlugin plugin, PluginConfig config, RedisManager redisManager) {
        this.plugin = plugin;
        this.config = config;
        this.redisManager = redisManager;
    }

    @Override
    public void start() {
        this.channels = new RedisChannels(config.channels().prefix());
        this.security = new MessageSecurity(new HmacSigner(config.security().hmacSecret()));
        this.cache = new ClientCache(config.client().cache());
        this.pendingRequests = new ClientPendingRequests(plugin);
        this.listener = new ClientRedisListener(plugin, security, channels, pendingRequests, cache);
        this.dispatcher = new ClientRequestDispatcher(plugin, config, redisManager, channels, security, pendingRequests);
        this.networkService = new ClientNetworkService(dispatcher);
        plugin.getServer().getServicesManager().register(NetworkSkyblockService.class, networkService, plugin, ServicePriority.Normal);

        this.subscriptionThread = new Thread(this::runSubscriptionLoop, "SSB2-Client-Subscription");
        subscriptionThread.setDaemon(true);
        subscriptionThread.start();

        registerCommands();
        plugin.getLogger().info("Client component ready; Redis prefix=" + channels.requestPattern());
    }

    private void runSubscriptionLoop() {
        try (Jedis jedis = redisManager.pool().getResource()) {
            jedis.psubscribe(listener, channels.responsePattern(), channels.eventPattern());
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Client subscription loop terminated", ex);
            pendingRequests.failAll(ex);
        }
    }

    private void registerCommands() {
        IslandCommand islandCommand = new IslandCommand(plugin, dispatcher, cache);
        PluginCommand command = plugin.getCommand("is");
        if (command == null) {
            plugin.getLogger().severe("Command 'is' not defined in plugin.yml");
            return;
        }
        command.setExecutor(islandCommand);
        command.setTabCompleter(islandCommand);
    }

    @Override
    public void stop() {
        if (listener != null) {
            listener.gracefulShutdown();
        }
        if (subscriptionThread != null) {
            subscriptionThread.interrupt();
        }
        if (pendingRequests != null) {
            pendingRequests.failAll(new IllegalStateException("Plugin shutting down"));
        }
        if (networkService != null) {
            plugin.getServer().getServicesManager().unregister(networkService);
            networkService = null;
        }
    }
}
