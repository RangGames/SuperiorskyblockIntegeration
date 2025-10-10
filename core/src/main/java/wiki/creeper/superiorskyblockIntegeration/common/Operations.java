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
    MEMBERS_KICK("members.kick"),
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
    FARM_SHOP_TABLE("farm.shop.table"),
    FARM_POINTS_INFO("farm.points.info"),
    FARM_HOPPER_INFO("farm.hopper.info"),
    FARM_RATING_UPDATE("farm.rating.update"),
    FARM_WARP_HOME_LIST("farm.warp.home.list"),
    FARM_WARP_HOME_SET("farm.warp.home.set"),
    FARM_WARP_HOME_DELETE("farm.warp.home.delete"),
    FARM_WARP_HOME_RENAME("farm.warp.home.rename"),
    FARM_WARP_HOME_TOGGLE("farm.warp.home.toggle"),
    FARM_WARP_PLAYER_LIST("farm.warp.player.list"),
    FARM_WARP_GLOBAL_LIST("farm.warp.global.list"),
    FARM_WARP_VISIT("farm.warp.visit"),
    FARM_RULE_LIST("farm.rule.list"),
    FARM_RULE_ADD("farm.rule.add"),
    FARM_RULE_REMOVE("farm.rule.remove"),
    FARM_COOP_LIST("farm.coop.list"),
    FARM_COOP_ADD("farm.coop.add"),
    FARM_COOP_REMOVE("farm.coop.remove"),
    FARM_BAN_LIST("farm.ban.list"),
    FARM_BAN_ADD("farm.ban.add"),
    FARM_BAN_REMOVE("farm.ban.remove"),
    FARM_CHAT_SEND("farm.chat.send"),
    ROLE_PERMISSIONS_LIST("roles.permissions.list"),
    ROLE_PERMISSIONS_UPDATE("roles.permissions.update"),
    BANK_STATE("bank.state"),
    BANK_DEPOSIT("bank.deposit"),
    BANK_WITHDRAW("bank.withdraw"),
    BANK_HISTORY("bank.history"),
    BANK_LOCK_SET("bank.lock.set"),
    ADMIN_RESET_PERMISSIONS("admin.reset.permissions"),
    ADMIN_LOOKUP_ISLAND_UUID("admin.lookup.island.uuid"),
    ADMIN_LOOKUP_ISLAND_OWNER("admin.lookup.island.owner"),
    ADMIN_TOGGLE_GAMBLING("admin.toggle.gambling"),
    ADMIN_LOAD_POWER_REWARD("admin.reward.power.load"),
    ADMIN_SAVE_POWER_REWARD("admin.reward.power.save"),
    ADMIN_LOAD_TOP_REWARD("admin.reward.top.load"),
    ADMIN_SAVE_TOP_REWARD("admin.reward.top.save"),
    ADMIN_GIVE_TOP_REWARD("admin.reward.top.give");

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
