package wiki.creeper.superiorskyblockIntegeration.gateway;

import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

import wiki.creeper.superiorskyblockIntegeration.redis.MessageSecurity;
import wiki.creeper.superiorskyblockIntegeration.redis.RedisMessage;

final class GatewaySubscriber extends RedisPubSubAdapter<String, String> {

    private final JavaPlugin plugin;
    private final MessageSecurity security;
    private final GatewayRequestRouter router;

    private StatefulRedisPubSubConnection<String, String> connection;
    private String subscribedPattern;

    GatewaySubscriber(JavaPlugin plugin, MessageSecurity security, GatewayRequestRouter router) {
        this.plugin = plugin;
        this.security = security;
        this.router = router;
    }

    void register(StatefulRedisPubSubConnection<String, String> connection, String pattern) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.subscribedPattern = pattern;
        connection.addListener(this);
        connection.async().psubscribe(pattern);
    }

    public void message(String channel, String message) {
        // not used; we subscribe via patterns
    }

    @Override
    public void message(String pattern, String channel, String message) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                RedisMessage payload = RedisMessage.parse(message);
                if (!security.verify(payload)) {
                    plugin.getLogger().warning("Rejected request with invalid signature: " + payload.id());
                    return;
                }
                payload.decompressDataIfNeeded();
                router.handle(channel, payload);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to process gateway request", ex);
            }
        });
    }

    public void subscribed(String channel, long count) {
        // no-op
    }

    public void psubscribed(String pattern, long count) {
        // no-op
    }

    public void unsubscribed(String channel, long count) {
        // no-op
    }

    public void punsubscribed(String pattern, long count) {
        // no-op
    }

    void gracefulShutdown() {
        if (connection == null) {
            return;
        }
        try {
            if (subscribedPattern != null) {
                CompletableFuture<?> future = connection.async()
                        .punsubscribe(subscribedPattern)
                        .toCompletableFuture();
                future.join();
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Error while unsubscribing gateway listener", ex);
        } finally {
            try {
                connection.removeListener(this);
                connection.close();
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Error while closing gateway pub/sub connection", ex);
            }
        }
    }
}
