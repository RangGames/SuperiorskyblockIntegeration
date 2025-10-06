package wiki.creeper.superiorskyblockIntegeration.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.Objects;
import java.util.logging.Level;

import wiki.creeper.superiorskyblockIntegeration.config.PluginConfig;

/**
 * Lazily initialised Redis client for pub/sub and command execution using Lettuce.
 */
public final class RedisManager {

    private final JavaPlugin plugin;
    private final PluginConfig.RedisSettings settings;

    private RedisClient client;
    private StatefulRedisConnection<String, String> commandConnection;

    public RedisManager(JavaPlugin plugin, PluginConfig.RedisSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    public void start() {
        RedisURI.Builder builder = RedisURI.builder()
                .withHost(settings.host())
                .withPort(settings.port())
                .withDatabase(settings.database())
                .withTimeout(Duration.ofSeconds(5));

        if (settings.ssl()) {
            builder.withSsl(true);
        }
        if (settings.password() != null && !settings.password().isBlank()) {
            builder.withPassword(settings.password().toCharArray());
        }

        try {
            this.client = RedisClient.create(builder.build());
            this.commandConnection = client.connect();
            plugin.getLogger().info("Connected to Redis @ " + settings.host() + ':' + settings.port());
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialise Redis connection", ex);
            throw ex;
        }
    }

    public void stop() {
        if (commandConnection != null) {
            try {
                commandConnection.close();
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Error while closing Redis connection", ex);
            }
        }
        if (client != null) {
            try {
                client.shutdown();
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Error while shutting down Redis client", ex);
            }
        }
    }

    public StatefulRedisPubSubConnection<String, String> connectPubSub() {
        ensureStarted();
        return client.connectPubSub();
    }

    public RedisCommands<String, String> sync() {
        ensureStarted();
        return commandConnection.sync();
    }

    public void publish(String channel, String message) {
        ensureStarted();
        commandConnection.async().publish(channel, message);
    }

    private void ensureStarted() {
        Objects.requireNonNull(client, "RedisManager not started");
        Objects.requireNonNull(commandConnection, "RedisManager not started");
    }
}
