package wiki.creeper.superiorskyblockIntegeration.common.model;

import java.util.List;

/**
 * Immutable representation of a farm ranking reward tier.
 */
public record FarmRankingReward(int minRank,
                                int maxRank,
                                String title,
                                String icon,
                                int moonlight,
                                int farmPoints,
                                List<String> lore) {

    public FarmRankingReward {
        if (minRank <= 0) {
            minRank = 1;
        }
        if (maxRank < minRank) {
            maxRank = minRank;
        }
        lore = lore == null ? List.of() : List.copyOf(lore);
    }
}

