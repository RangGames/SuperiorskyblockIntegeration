package wiki.creeper.superiorskyblockIntegeration.common;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Supported SSB2 network operations.
 */
public enum Operations {
    INVITE_CREATE("invite.create"),
    INVITE_ACCEPT("invite.accept"),
    INVITE_DENY("invite.deny"),
    INVITES_PENDING("invites.pending"),
    ISLAND_DISBAND("island.disband"),
    ISLAND_GET("island.get"),
    MEMBERS_LIST("members.list"),
    DATA_PUT("data.put"),
    DATA_GET("data.get"),
    DATA_DELETE("data.delete"),
    PLAYER_PROFILE_REGISTER("player.profile.register"),
    PLAYER_PROFILE_LOOKUP("player.profile.lookup"),
    PLAYER_ISLAND_LOOKUP("player.island.lookup"),
    QUEST_STATE("quest.state"),
    QUEST_ASSIGN("quest.assign"),
    QUEST_PROGRESS("quest.progress"),
    FARM_RANKING_TOP("farm.ranking.top"),
    FARM_RANKING_MEMBERS("farm.ranking.members"),
    FARM_RANKING_INCREMENT("farm.ranking.increment"),
    FARM_RANKING_SNAPSHOT("farm.ranking.snapshot"),
    FARM_HISTORY_LIST("farm.history.list"),
    FARM_HISTORY_DETAIL("farm.history.detail"),
    FARM_BORDER_TOGGLE("farm.border.toggle"),
    FARM_BORDER_COLOR("farm.border.color"),
    FARM_BORDER_STATE("farm.border.state"),
    FARM_REWARD_TABLE("farm.reward.table"),
    FARM_SHOP_TABLE("farm.shop.table");

    private final String op;

    Operations(String op) {
        this.op = op;
    }

    public String op() {
        return op;
    }

    public static Optional<Operations> from(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalised = raw.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(op -> op.op.equals(normalised))
                .findFirst();
    }
}
