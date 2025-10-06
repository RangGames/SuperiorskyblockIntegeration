package wiki.creeper.superiorskyblockIntegeration.common.quest;

/**
 * Represents the two quest rotations supported by the network.
 */
public enum QuestType {

    DAILY,
    WEEKLY;

    public boolean isDaily() {
        return this == DAILY;
    }

    public boolean isWeekly() {
        return this == WEEKLY;
    }
}

