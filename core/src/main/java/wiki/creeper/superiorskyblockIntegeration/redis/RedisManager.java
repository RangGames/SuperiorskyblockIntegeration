package wiki.creeper.superiorskyblockIntegeration.redis;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Objects;
import java.util.logging.Level;

import wiki.creeper.superiorskyblockIntegeration.config.PluginConfig;

/**
 * Lazily initialised Redis client for pub/sub and short-lived commands.
 */
public final class RedisManager {

    private final JavaPlugin plugin;
    private final PluginConfig.RedisSettings settings;

    private JedisPool pool;

    public RedisManager(JavaPlugin plugin, PluginConfig.RedisSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    public void start() {
        GenericObjectPoolConfig<Jedis> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(32);
        poolConfig.setMaxIdle(16);
        poolConfig.setMinIdle(2);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);

        DefaultJedisClientConfig.Builder builder = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(2000)
                .socketTimeoutMillis(2000)
                .database(settings.database())
                .ssl(settings.ssl());

        if (settings.password() != null && !settings.password().isBlank()) {
            builder.password(settings.password());
        }

        try {
            this.pool = new JedisPool(poolConfig, new HostAndPort(settings.host(), settings.port()), builder.build());
            plugin.getLogger().info("Connected to Redis @ " + settings.host() + ":" + settings.port());
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialise Redis connection", ex);
            throw ex;
        }
    }

    public void stop() {
        if (pool != null) {
            try {
                pool.close();
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Error while closing Redis client", ex);
            }
        }
    }

    public JedisPool pool() {
        return Objects.requireNonNull(pool, "RedisManager not started");
    }

    public void publish(String channel, String message) {
        try (Jedis jedis = pool().getResource()) {
            jedis.publish(channel, message);
        }
    }
}
