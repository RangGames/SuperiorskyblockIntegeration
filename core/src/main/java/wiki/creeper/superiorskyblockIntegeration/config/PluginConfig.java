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
    private final QuestSettings quest;

    private PluginConfig(
            Mode mode,
            RedisSettings redis,
            ChannelSettings channels,
            TimeoutSettings timeouts,
            SecuritySettings security,
            LoggingSettings logging,
            GatewaySettings gateway,
            ClientSettings client,
            QuestSettings quest
    ) {
        this.mode = mode;
        this.redis = redis;
        this.channels = channels;
        this.timeouts = timeouts;
        this.security = security;
        this.logging = logging;
        this.gateway = gateway;
        this.client = client;
        this.quest = quest;
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

    public QuestSettings quest() {
        return quest;
    }

    public static PluginConfig from(FileConfiguration config) {
        Mode mode = Mode.byName(config.getString("mode", "CLIENT"));
        RedisSettings redis = new RedisSettings(
                config.getString("redis.host", "127.0.0.1"),
                Math.max(1, config.getInt("redis.port", 6379)),
                config.getString("redis.password", ""),
                config.getBoolean("redis.ssl", false),
                Math.max(0, config.getInt("redis.database", 0)),
                Math.max(4096, config.getInt("redis.compressionThreshold", 16384))
        );
        ChannelSettings channels = new ChannelSettings(config.getString("channels.prefix", "ssb.v1"));
        TimeoutSettings timeouts = new TimeoutSettings(
                Math.max(10, config.getInt("timeouts.requestMs", 3000)),
                Math.max(50, config.getInt("timeouts.responseMaxWaitMs", 5000))
        );
        SecuritySettings security = new SecuritySettings(config.getString("security.hmacSecret", ""));
        LoggingSettings logging = new LoggingSettings(
                config.getBoolean("logging.requestBodies", false),
                config.getBoolean("logging.redis", false));
        DatabaseSettings database = new DatabaseSettings(
                config.getString("gateway.database.type", "mysql"),
                config.getString("gateway.database.host", "127.0.0.1"),
                Math.max(1, config.getInt("gateway.database.port", 3306)),
                config.getString("gateway.database.name", "ssb_integration"),
                config.getString("gateway.database.username", "root"),
                config.getString("gateway.database.password", ""),
                new HikariSettings(
                        Math.max(1, config.getInt("gateway.database.hikari.maximumPoolSize", 10)),
                        Math.max(0, config.getInt("gateway.database.hikari.minimumIdle", 2)),
                        Math.max(1000L, config.getLong("gateway.database.hikari.connectionTimeoutMs", 5000L)),
                        Math.max(0L, config.getLong("gateway.database.hikari.idleTimeoutMs", 600_000L))
                )
        );
        GatewaySettings gateway = new GatewaySettings(
                new ConcurrencySettings(Math.max(1, config.getInt("gateway.concurrency.workers", 16))),
                new LockSettings(Math.max(100, config.getInt("gateway.locks.islandLockTtlMs", 2000))),
                new SuperiorSkyblockSettings(Math.max(100, config.getInt("gateway.superiorSkyblock.apiHookTimeoutMs", 1000))),
                database,
                RewardSettings.parse(config),
                ShopSettings.parse(config)
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
        QuestSettings quest = QuestSettings.parse(config);

        if (security.hmacSecret() == null || security.hmacSecret().isBlank()) {
            throw new IllegalStateException("security.hmacSecret must be configured");
        }
        if (channels.prefix() == null || channels.prefix().isBlank()) {
            throw new IllegalStateException("channels.prefix must be configured");
        }

        return new PluginConfig(mode, redis, channels, timeouts, security, logging, gateway, client, quest);
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

    public record RedisSettings(String host, int port, String password, boolean ssl, int database, int messageCompressionThreshold) { }

    public record ChannelSettings(String prefix) { }

    public record TimeoutSettings(int requestMs, int responseMaxWaitMs) { }

    public record SecuritySettings(String hmacSecret) { }

    public record LoggingSettings(boolean requestBodies, boolean redisDebug) { }

    public record GatewaySettings(ConcurrencySettings concurrency,
                                  LockSettings locks,
                                  SuperiorSkyblockSettings superiorSkyblock,
                                  DatabaseSettings database,
                                  RewardSettings rewards,
                                  ShopSettings shop) { }

    public record ConcurrencySettings(int workers) { }

    public record LockSettings(int islandLockTtlMs) { }

    public record SuperiorSkyblockSettings(int apiHookTimeoutMs) { }

    public record DatabaseSettings(String type,
                                   String host,
                                   int port,
                                   String name,
                                   String username,
                                   String password,
                                   HikariSettings hikari) { }

    public record HikariSettings(int maximumPoolSize,
                                 int minimumIdle,
                                 long connectionTimeoutMs,
                                 long idleTimeoutMs) { }

    public record RewardSettings(java.util.List<wiki.creeper.superiorskyblockIntegeration.common.model.FarmRankingReward> farmRanking) {

        static RewardSettings parse(FileConfiguration config) {
            java.util.List<java.util.Map<?, ?>> rawList = config.getMapList("gateway.rewards.farmRanking");
            java.util.List<wiki.creeper.superiorskyblockIntegeration.common.model.FarmRankingReward> rewards = new java.util.ArrayList<>();
            for (java.util.Map<?, ?> map : rawList) {
                int minRank = asInt(map.get("minRank"), 1);
                int maxRank = asInt(map.get("maxRank"), minRank);
                String title = asString(map.get("title"), "&f랭킹 보상");
                String icon = asString(map.get("icon"), "CHEST");
                int moonlight = asInt(map.get("moonlight"), 0);
                int farmPoints = asInt(map.get("farmPoints"), 0);
                java.util.List<String> lore = asStringList(map.get("lore"));
                rewards.add(new wiki.creeper.superiorskyblockIntegeration.common.model.FarmRankingReward(
                        minRank, maxRank, title, icon, moonlight, farmPoints, lore));
            }
            return new RewardSettings(java.util.List.copyOf(rewards));
        }

        private static int asInt(Object value, int fallback) {
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value instanceof String string) {
                try {
                    return Integer.parseInt(string);
                } catch (NumberFormatException ignored) {
                }
            }
            return fallback;
        }

        private static String asString(Object value, String fallback) {
            return value != null ? value.toString() : fallback;
        }

        @SuppressWarnings("unchecked")
        private static java.util.List<String> asStringList(Object value) {
            if (value instanceof java.util.List<?> list) {
                java.util.List<String> result = new java.util.ArrayList<>(list.size());
                for (Object element : list) {
                    if (element != null) {
                        result.add(element.toString());
                    }
                }
                return result;
            }
            return java.util.Collections.emptyList();
        }
    }

    public record ShopSettings(java.util.List<ShopItem> items) {

        static ShopSettings parse(FileConfiguration config) {
            java.util.List<java.util.Map<?, ?>> rawList = config.getMapList("gateway.shop.items");
            java.util.List<ShopItem> items = new java.util.ArrayList<>();
            for (java.util.Map<?, ?> map : rawList) {
                int slot = asInt(map.get("slot"), -1);
                String title = asString(map.get("title"), "&f아이템");
                String icon = asString(map.get("icon"), "BARRIER");
                String currency = asString(map.get("currency"), "none");
                int price = asInt(map.get("price"), 0);
                String command = asString(map.get("command"), "");
                java.util.List<String> lore = asStringList(map.get("lore"));
                boolean enabled = asBoolean(map.get("enabled"), true);
                items.add(new ShopItem(slot, title, icon, currency, price, lore, command, enabled));
            }
            return new ShopSettings(java.util.List.copyOf(items));
        }

        private static int asInt(Object value, int fallback) {
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value instanceof String string) {
                try {
                    return Integer.parseInt(string);
                } catch (NumberFormatException ignored) {
                }
            }
            return fallback;
        }

        private static String asString(Object value, String fallback) {
            return value != null ? value.toString() : fallback;
        }

        @SuppressWarnings("unchecked")
        private static java.util.List<String> asStringList(Object value) {
            if (value instanceof java.util.List<?> list) {
                java.util.List<String> result = new java.util.ArrayList<>(list.size());
                for (Object element : list) {
                    if (element != null) {
                        result.add(element.toString());
                    }
                }
                return result;
            }
            return java.util.Collections.emptyList();
        }

        private static boolean asBoolean(Object value, boolean fallback) {
            if (value instanceof Boolean bool) {
                return bool;
            }
            if (value instanceof String string) {
                return Boolean.parseBoolean(string);
            }
            return fallback;
        }
    }

    public record ShopItem(int slot,
                           String title,
                           String icon,
                           String currency,
                           int price,
                           java.util.List<String> lore,
                           String command,
                           boolean enabled) { }

    public record ClientSettings(CacheSettings cache, UiSettings ui, RetrySettings retry, VelocitySettings velocity) { }

    public record CacheSettings(boolean enabled, int ttlSeconds, long maximumSize) { }

    public record UiSettings(String localeDefault) { }

    public record RetrySettings(int attempts, int backoffMs) { }

    public record VelocitySettings(boolean enabled, String targetServer) { }

    public record QuestSettings(FarmPointSettings farmPoints, FurnaceSettings furnace) {

        public static QuestSettings parse(FileConfiguration config) {
            FarmPointSettings farmPoints = FarmPointSettings.from(config);
            FurnaceSettings furnace = FurnaceSettings.from(config);
            return new QuestSettings(farmPoints, furnace);
        }
    }

    public record FarmPointSettings(int dailyDefault,
                                    int weeklyDefault,
                                    String timezoneId,
                                    java.util.List<SeasonalFarmPoint> seasonal) {

        private static FarmPointSettings from(FileConfiguration config) {
            org.bukkit.configuration.ConfigurationSection section = config.getConfigurationSection("quest.farmPoints");
            int dailyDefault = section != null ? section.getInt("dailyDefault", 20) : 20;
            int weeklyDefault = section != null ? section.getInt("weeklyDefault", 200) : 200;
            String timezone = section != null ? section.getString("timezone", java.time.ZoneId.systemDefault().getId())
                    : java.time.ZoneId.systemDefault().getId();
            java.util.List<java.util.Map<?, ?>> rawSeasonal = section != null ? section.getMapList("seasonal") : java.util.Collections.emptyList();
            java.util.List<SeasonalFarmPoint> seasonal = new java.util.ArrayList<>();
            for (java.util.Map<?, ?> entry : rawSeasonal) {
                if (entry == null) {
                    continue;
                }
                String name = stringValue(entry.get("name"), "season");
                java.time.LocalDate start = parseDate(entry.get("start"));
                java.time.LocalDate end = parseDate(entry.get("end"));
                if (start == null || end == null || end.isBefore(start)) {
                    continue;
                }
                int daily = intValue(entry.get("daily"), dailyDefault);
                int weekly = intValue(entry.get("weekly"), weeklyDefault);
                seasonal.add(new SeasonalFarmPoint(name, start, end, daily, weekly));
            }
            return new FarmPointSettings(dailyDefault, weeklyDefault, timezone, java.util.Collections.unmodifiableList(seasonal));
        }

        private static java.time.LocalDate parseDate(Object raw) {
            if (raw == null) {
                return null;
            }
            try {
                return java.time.LocalDate.parse(raw.toString());
            } catch (Exception ignored) {
                return null;
            }
        }

        private static String stringValue(Object raw, String fallback) {
            return raw != null ? raw.toString() : fallback;
        }

        private static int intValue(Object raw, int fallback) {
            if (raw == null) {
                return fallback;
            }
            try {
                return Integer.parseInt(raw.toString());
            } catch (NumberFormatException ex) {
                return fallback;
            }
        }
    }

    public record SeasonalFarmPoint(String name,
                                    java.time.LocalDate startDate,
                                    java.time.LocalDate endDate,
                                    int dailyValue,
                                    int weeklyValue) { }

    public record FurnaceSettings(String burnUpgrade,
                                  String speedUpgrade,
                                  double burnBonusPerLevel,
                                  double cookReductionPerLevel) {

        private static FurnaceSettings from(FileConfiguration config) {
            org.bukkit.configuration.ConfigurationSection section = config.getConfigurationSection("quest.furnace");
            if (section == null) {
                return new FurnaceSettings("", "", 0.1D, 0.1D);
            }
            String burn = section.getString("burnUpgrade", "");
            String speed = section.getString("speedUpgrade", "");
            double burnBonus = section.getDouble("burnBonusPerLevel", 0.1D);
            double cookReduction = section.getDouble("cookReductionPerLevel", 0.1D);
            return new FurnaceSettings(burn != null ? burn : "", speed != null ? speed : "", burnBonus, cookReduction);
        }
    }
}
