package wiki.creeper.superiorskyblockIntegeration.common.quest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestGeneratorTest {

    @Test
    @DisplayName("Generator returns empty list when quest count is zero")
    void generatesEmptyListWhenQuestCountZero() {
        List<IslandQuestEntry> quests = QuestGenerator.generate(QuestType.DAILY, 0, 3);
        assertTrue(quests.isEmpty());
    }

    @Test
    @DisplayName("Daily quests scale with member count and remain unique")
    void dailyQuestsScaleWithMemberCount() {
        int questCount = 5;
        int memberCount = 4; // multiplier -> 1 + (3 * 0.3) = 1.9
        List<IslandQuestEntry> quests = QuestGenerator.generate(QuestType.DAILY, questCount, memberCount);

        assertEquals(questCount, quests.size());
        Set<Integer> uniqueIds = new HashSet<>();
        double multiplier = 1.0D + ((Math.max(memberCount, 1) - 1) * 0.3D);

        for (IslandQuestEntry quest : quests) {
            assertTrue(uniqueIds.add(quest.questId()), "Duplicate quest id generated: " + quest.questId());
            QuestDefinition definition = QuestDefinition.byId(quest.questId()).orElseThrow();
            int expectedTarget = Math.toIntExact(Math.round(definition.targetFor(QuestType.DAILY) * multiplier));
            assertEquals(expectedTarget, quest.target(), "Unexpected target for quest " + quest.questId());
        }
    }

    @Test
    @DisplayName("Weekly quests apply 0.4 multiplier per additional member")
    void weeklyQuestsScaleWithMemberCount() {
        int questCount = 4;
        int memberCount = 3; // multiplier -> 1 + (2 * 0.4) = 1.8
        List<IslandQuestEntry> quests = QuestGenerator.generate(QuestType.WEEKLY, questCount, memberCount);

        assertEquals(questCount, quests.size());
        double multiplier = 1.0D + ((Math.max(memberCount, 1) - 1) * 0.4D);

        for (IslandQuestEntry quest : quests) {
            QuestDefinition definition = QuestDefinition.byId(quest.questId()).orElseThrow();
            int expectedTarget = Math.toIntExact(Math.round(definition.targetFor(QuestType.WEEKLY) * multiplier));
            assertEquals(expectedTarget, quest.target());
        }
    }
}

