package wiki.creeper.superiorskyblockIntegeration.common.quest;

/**
 * Helper for computing quest rewards based on quest count.
 */
public final class QuestRewards {

    private static final RewardPolicy DEFAULT_POLICY = new RewardPolicy(java.time.ZoneId.systemDefault(),
            java.util.Collections.emptyList(), 20, 200);
    private static volatile RewardPolicy policy = DEFAULT_POLICY;

    private QuestRewards() {
    }

    public static void configure(java.time.ZoneId zoneId,
                                 int dailyDefault,
                                 int weeklyDefault,
                                 java.util.List<SeasonalWindow> seasonalWindows) {
        java.util.List<SeasonalWindow> windows = seasonalWindows == null
                ? java.util.Collections.emptyList()
                : java.util.List.copyOf(seasonalWindows);
        int daily = Math.max(0, dailyDefault);
        int weekly = Math.max(0, weeklyDefault);
        policy = new RewardPolicy(zoneId != null ? zoneId : java.time.ZoneId.systemDefault(), windows, daily, weekly);
    }

    public static void resetConfiguration() {
        policy = DEFAULT_POLICY;
    }

    public static void configure(wiki.creeper.superiorskyblockIntegeration.config.PluginConfig.QuestSettings questSettings) {
        if (questSettings == null || questSettings.farmPoints() == null) {
            resetConfiguration();
            return;
        }
        wiki.creeper.superiorskyblockIntegeration.config.PluginConfig.FarmPointSettings farmPoints = questSettings.farmPoints();
        java.time.ZoneId zone = safeZoneId(farmPoints.timezoneId());
        java.util.List<SeasonalWindow> windows = farmPoints.seasonal() == null
                ? java.util.Collections.emptyList()
                : farmPoints.seasonal().stream()
                .map(entry -> new SeasonalWindow(entry.startDate(), entry.endDate(), entry.dailyValue(), entry.weeklyValue()))
                .toList();
        configure(zone, farmPoints.dailyDefault(), farmPoints.weeklyDefault(), windows);
    }

    public static int farmPointReward(QuestType type, int completedQuests) {
        return switch (type) {
            case DAILY -> switch (completedQuests) {
                case 3 -> 10_000;
                case 4 -> 20_000;
                case 5 -> 30_000;
                default -> 0;
            };
            case WEEKLY -> switch (completedQuests) {
                case 5 -> 100_000;
                case 6 -> 300_000;
                case 7 -> 500_000;
                default -> 0;
            };
        };
    }

    public static int farmScoreReward(QuestType type) {
        RewardPolicy current = policy;
        int base = type.isDaily() ? current.dailyDefault : current.weeklyDefault;
        java.time.LocalDate today = java.time.LocalDate.now(current.zoneId);
        for (SeasonalWindow window : current.seasonalWindows) {
            if (window.contains(today)) {
                return type.isDaily() ? window.dailyValue : window.weeklyValue;
            }
        }
        return base;
    }

    private record RewardPolicy(java.time.ZoneId zoneId,
                                java.util.List<SeasonalWindow> seasonalWindows,
                                int dailyDefault,
                                int weeklyDefault) { }

    public record SeasonalWindow(java.time.LocalDate start,
                                 java.time.LocalDate end,
                                 int dailyValue,
                                 int weeklyValue) {

        public boolean contains(java.time.LocalDate date) {
            return (date.isEqual(start) || date.isAfter(start))
                    && (date.isEqual(end) || date.isBefore(end));
        }
    }

    private static java.time.ZoneId safeZoneId(String raw) {
        if (raw == null || raw.isBlank()) {
            return java.time.ZoneId.systemDefault();
        }
        try {
            return java.time.ZoneId.of(raw);
        } catch (Exception ex) {
            return java.time.ZoneId.systemDefault();
        }
    }
}
