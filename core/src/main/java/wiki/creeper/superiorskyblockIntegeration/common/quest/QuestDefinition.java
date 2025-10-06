package wiki.creeper.superiorskyblockIntegeration.common.quest;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Static lookup table for quest identifiers used across the daily / weekly rotations.
 */
public enum QuestDefinition {

    WHEAT_HARVEST(1, "[농사] 밀 수확", 60, 450),
    POTATO_HARVEST(3, "[농사] 감자 수확", 120, 900),
    CARROT_HARVEST(4, "[농사] 당근 수확", 120, 900),
    PUMPKIN_HARVEST(5, "[농사] 호박 수확", 80, 600),
    MELON_HARVEST(6, "[농사] 수박 수확", 160, 1150),
    IRON_SMELT(7, "[광물] 철 굽기", 100, 800),
    GOLD_SMELT(8, "[광물] 금 굽기", 40, 350),
    DIAMOND_MINE(9, "[광물] 다이아몬드 캐기", 15, 120),
    EMERALD_MINE(10, "[광물] 에메랄드 캐기", 7, 60),
    FISH(11, "[낚시] 물고기 잡기", 10, 80),
    LOG_CHOP(12, "[벌목] 벌목 하기", 40, 290);

    private static final Map<Integer, QuestDefinition> BY_ID = new ConcurrentHashMap<>();

    static {
        Arrays.stream(values()).forEach(definition -> BY_ID.put(definition.id, definition));
    }

    private final int id;
    private final String displayName;
    private final int dailyTarget;
    private final int weeklyTarget;

    QuestDefinition(int id, String displayName, int dailyTarget, int weeklyTarget) {
        this.id = id;
        this.displayName = displayName;
        this.dailyTarget = dailyTarget;
        this.weeklyTarget = weeklyTarget;
    }

    public int id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public int targetFor(QuestType type) {
        return type.isDaily() ? dailyTarget : weeklyTarget;
    }

    public static Optional<QuestDefinition> byId(int id) {
        return Optional.ofNullable(BY_ID.get(id));
    }
}

