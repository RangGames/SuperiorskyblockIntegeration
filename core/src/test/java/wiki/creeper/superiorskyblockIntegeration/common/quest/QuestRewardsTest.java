package wiki.creeper.superiorskyblockIntegeration.common.quest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestRewardsTest {

    @AfterEach
    void resetConfiguration() {
        QuestRewards.resetConfiguration();
    }

    @Test
    @DisplayName("Moonlight rewards follow legacy Skript table")
    void moonlightRewardMatchesLegacyValues() {
        assertEquals(10_000, QuestRewards.moonlightReward(QuestType.DAILY, 3));
        assertEquals(20_000, QuestRewards.moonlightReward(QuestType.DAILY, 4));
        assertEquals(30_000, QuestRewards.moonlightReward(QuestType.DAILY, 5));

        assertEquals(100_000, QuestRewards.moonlightReward(QuestType.WEEKLY, 5));
        assertEquals(300_000, QuestRewards.moonlightReward(QuestType.WEEKLY, 6));
        assertEquals(500_000, QuestRewards.moonlightReward(QuestType.WEEKLY, 7));
    }

    @Test
    @DisplayName("Farm point rewards default to configured base values")
    void farmPointRewardUsesDefaultsOutsideSeason() {
        QuestRewards.configure(ZoneId.of("UTC"), 42, 420, List.of());

        assertEquals(42, QuestRewards.farmPointReward(QuestType.DAILY));
        assertEquals(420, QuestRewards.farmPointReward(QuestType.WEEKLY));
    }

    @Test
    @DisplayName("Seasonal window overrides farm point rewards inside active dates")
    void farmPointRewardUsesSeasonalWindow() {
        ZoneId zone = ZoneId.of("Asia/Seoul");
        LocalDate today = LocalDate.now(zone);
        QuestRewards.configure(zone,
                20,
                200,
                List.of(new QuestRewards.SeasonalWindow(
                        today.minusDays(1),
                        today.plusDays(1),
                        5,
                        50)));

        assertEquals(5, QuestRewards.farmPointReward(QuestType.DAILY));
        assertEquals(50, QuestRewards.farmPointReward(QuestType.WEEKLY));
    }
}

