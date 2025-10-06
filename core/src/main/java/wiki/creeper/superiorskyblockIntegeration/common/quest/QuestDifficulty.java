package wiki.creeper.superiorskyblockIntegeration.common.quest;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Difficulty presets used by the quest selection menus.
 */
public enum QuestDifficulty {

    DAILY_EASY(QuestType.DAILY, 3),
    DAILY_MEDIUM(QuestType.DAILY, 4),
    DAILY_HARD(QuestType.DAILY, 5),
    WEEKLY_EASY(QuestType.WEEKLY, 5),
    WEEKLY_MEDIUM(QuestType.WEEKLY, 6),
    WEEKLY_HARD(QuestType.WEEKLY, 7);

    private final QuestType type;
    private final int questCount;

    QuestDifficulty(QuestType type, int questCount) {
        this.type = type;
        this.questCount = questCount;
    }

    public QuestType type() {
        return type;
    }

    public int questCount() {
        return questCount;
    }

    public static Optional<QuestDifficulty> of(QuestType type, int questCount) {
        return Arrays.stream(values())
                .filter(difficulty -> difficulty.type == type && difficulty.questCount == questCount)
                .findFirst();
    }

    public static Optional<QuestDifficulty> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalised = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        return Arrays.stream(values())
                .filter(difficulty -> difficulty.name().equals(normalised))
                .findFirst();
    }
}

