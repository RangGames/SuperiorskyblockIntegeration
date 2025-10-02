package wiki.creeper.superiorskyblockIntegeration.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;

/**
 * Type-safe representation of the plugin configuration file.
 */
public final class PluginConfig {

    private final Mode mode;
    private final RedisSettings redis;
    private final ChannelSettings channels;
    private final TimeoutSettings timeouts;
    private final SecuritySettings security;
    private final LoggingSettings logging;
    private final GatewaySettings gateway;
    private final ClientSettings client;

    private PluginConfig(
            Mode mode,
            RedisSettings redis,
            ChannelSettings channels,
            TimeoutSettings timeouts,
            SecuritySettings security,
            LoggingSettings logging,
            GatewaySettings gateway,
            ClientSettings client
    ) {
        this.mode = mode;
        this.redis = redis;
        this.channels = channels;
        this.timeouts = timeouts;
        this.security = security;
        this.logging = logging;
        this.gateway = gateway;
        this.client = client;
    }

    public Mode mode() {
        return mode;
    }

    public RedisSettings redis() {
        return redis;
    }

    public ChannelSettings channels() {
        return channels;
    }

    public TimeoutSettings timeouts() {
        return timeouts;
    }

    public SecuritySettings security() {
        return security;
    }

    public LoggingSettings logging() {
        return logging;
    }

    public GatewaySettings gateway() {
        return gateway;
    }

    public ClientSettings client() {
        return client;
    }

    public static PluginConfig from(FileConfiguration config) {
        Mode mode = Mode.byName(config.getString("mode", "CLIENT"));
        RedisSettings redis = new RedisSettings(
                config.getString("redis.host", "127.0.0.1"),
                Math.max(1, config.getInt("redis.port", 6379)),
                config.getString("redis.password", ""),
                config.getBoolean("redis.ssl", false),
                Math.max(0, config.getInt("redis.database", 0))
        );
        ChannelSettings channels = new ChannelSettings(config.getString("channels.prefix", "ssb.v1"));
        TimeoutSettings timeouts = new TimeoutSettings(
                Math.max(10, config.getInt("timeouts.requestMs", 3000)),
                Math.max(50, config.getInt("timeouts.responseMaxWaitMs", 5000))
        );
        SecuritySettings security = new SecuritySettings(config.getString("security.hmacSecret", ""));
        LoggingSettings logging = new LoggingSettings(config.getBoolean("logging.requestBodies", false));
        GatewaySettings gateway = new GatewaySettings(
                new ConcurrencySettings(Math.max(1, config.getInt("gateway.concurrency.workers", 16))),
                new LockSettings(Math.max(100, config.getInt("gateway.locks.islandLockTtlMs", 2000))),
                new SuperiorSkyblockSettings(Math.max(100, config.getInt("gateway.superiorSkyblock.apiHookTimeoutMs", 1000)))
        );
        ClientSettings client = new ClientSettings(
                new CacheSettings(
                        config.getBoolean("client.cache.enabled", true),
                        Math.max(1, config.getInt("client.cache.ttlSeconds", 10)),
                        Math.max(1L, config.getLong("client.cache.maximumSize", 5000L))
                ),
                new UiSettings(config.getString("client.ui.localeDefault", "ko_KR")),
                new RetrySettings(
                        Math.max(0, config.getInt("client.retry.attempts", 1)),
                        Math.max(0, config.getInt("client.retry.backoffMs", 250))
                ),
                new VelocitySettings(
                        config.getBoolean("client.velocity.enabled", false),
                        config.getString("client.velocity.targetServer", "skyblock")
                )
        );

        if (security.hmacSecret() == null || security.hmacSecret().isBlank()) {
            throw new IllegalStateException("security.hmacSecret must be configured");
        }
        if (channels.prefix() == null || channels.prefix().isBlank()) {
            throw new IllegalStateException("channels.prefix must be configured");
        }

        return new PluginConfig(mode, redis, channels, timeouts, security, logging, gateway, client);
    }

    public enum Mode {
        GATEWAY,
        CLIENT;

        public static Mode byName(String raw) {
            if (raw == null || raw.isBlank()) {
                return CLIENT;
            }
            try {
                return Mode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return CLIENT;
            }
        }
    }

    public record RedisSettings(String host, int port, String password, boolean ssl, int database) { }

    public record ChannelSettings(String prefix) { }

    public record TimeoutSettings(int requestMs, int responseMaxWaitMs) { }

    public record SecuritySettings(String hmacSecret) { }

    public record LoggingSettings(boolean requestBodies) { }

    public record GatewaySettings(ConcurrencySettings concurrency, LockSettings locks, SuperiorSkyblockSettings superiorSkyblock) { }

    public record ConcurrencySettings(int workers) { }

    public record LockSettings(int islandLockTtlMs) { }

    public record SuperiorSkyblockSettings(int apiHookTimeoutMs) { }

    public record ClientSettings(CacheSettings cache, UiSettings ui, RetrySettings retry, VelocitySettings velocity) { }

    public record CacheSettings(boolean enabled, int ttlSeconds, long maximumSize) { }

    public record UiSettings(String localeDefault) { }

    public record RetrySettings(int attempts, int backoffMs) { }

    public record VelocitySettings(boolean enabled, String targetServer) { }
}
