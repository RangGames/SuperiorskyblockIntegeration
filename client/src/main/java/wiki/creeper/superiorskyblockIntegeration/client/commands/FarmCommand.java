package wiki.creeper.superiorskyblockIntegeration.client.commands;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

import wiki.creeper.superiorskyblockIntegeration.api.NetworkOperationResult;
import wiki.creeper.superiorskyblockIntegeration.api.NetworkSkyblockService;
import wiki.creeper.superiorskyblockIntegeration.client.cache.ClientCache;
import wiki.creeper.superiorskyblockIntegeration.client.cache.ClientCacheKeys;
import wiki.creeper.superiorskyblockIntegeration.client.lang.Messages;
import wiki.creeper.superiorskyblockIntegeration.client.menu.IslandMenuManager;
import wiki.creeper.superiorskyblockIntegeration.common.quest.QuestType;
import wiki.creeper.superiorskyblockIntegeration.config.PluginConfig;

/**
 * Handles the unified farm command (/팜) using configurable messages.
 */
public final class FarmCommand implements CommandExecutor, TabCompleter {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final JavaPlugin plugin;
    private final NetworkSkyblockService network;
    private final ClientCache cache;
    private final IslandMenuManager menus;
    private final PluginConfig.VelocitySettings velocitySettings;
    private final Messages messages;
    private final String baseCommand;

    public FarmCommand(JavaPlugin plugin,
                       NetworkSkyblockService network,
                       ClientCache cache,
                       IslandMenuManager menus,
                       PluginConfig.VelocitySettings velocitySettings,
                       Messages messages) {
        this.plugin = plugin;
        this.network = network;
        this.cache = cache;
        this.menus = menus;
        this.velocitySettings = velocitySettings;
        this.messages = messages;
        this.baseCommand = messages.commandLabel();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.player-only");
            return true;
        }

        if (args.length == 0 || matches(args[0], "메뉴", "주메뉴", "메인", "허브")) {
            if (menus != null) {
                menus.openMainMenu(player);
            } else {
                showHelp(player, 1);
            }
            return true;
        }

        String sub = args[0];
        switch (sub) {
            case "도움말" -> showHelp(player, parsePageArgument(args, 1));
            case "퀘스트" -> handleQuest(player, args);
            case "초대" -> handleInvite(player, args);
            case "초대목록" -> handlePendingInvites(player, args);
            case "수락" -> handleAccept(player, args);
            case "거절" -> handleDeny(player, args);
            case "구성원" -> handleMembers(player, args);
            case "정보" -> handleInfo(player, args);
            case "순위" -> handleRanking(player, args);
            case "히스토리" -> handleHistory(player, args);
            case "보상" -> handleRewards(player, args);
            case "상점" -> handleShop(player, args);
            case "경계" -> handleBorder(player, args);
            case "해체", "disband" -> handleDisband(player, args);
            case "관리자" -> handleAdmin(player, args);
            default -> {
                messages.send(player, "general.unknown-subcommand", sub);
                showHelp(player, 1);
            }
        }
        return true;
    }

    private void handleQuest(Player player, String[] args) {
        if (menus == null) {
            messages.send(player, "quest.menu-disabled");
            return;
        }

        if (args.length == 1 || matches(args[1], "메뉴")) {
            menus.openQuestHub(player);
            return;
        }

        if (matches(args[1], "발급")) {
            if (args.length < 4) {
                messages.send(player, "quest.assign-usage", baseCommand);
                return;
            }
            QuestType type = parseQuestType(args[2]);
            if (type == null) {
                messages.send(player, "quest.assign-invalid-type");
                return;
            }
            int count = parsePositiveInt(args[3], -1);
            if (count <= 0) {
                messages.send(player, "quest.assign-invalid-count");
                return;
            }
            menus.assignQuest(player, type, count);
            return;
        }

        if (matches(args[1], "목록")) {
            if (args.length < 3) {
                messages.send(player, "quest.list-usage", baseCommand);
                return;
            }
            QuestType type = parseQuestType(args[2]);
            if (type == null) {
                messages.send(player, "quest.list-invalid-type");
                return;
            }
            menus.openQuestList(player, type, null);
            return;
        }

        messages.send(player, "quest.subcommands");
    }

    private void handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            messages.send(player, "invite.usage", baseCommand);
            return;
        }
        String target = args[1];
        execute(player,
                () -> network.invite(player, target),
                result -> messages.send(player, "invite.sent"),
                messages.format("invite.failure-prefix"),
                true);
    }

    private void handlePendingInvites(Player player, String[] args) {
        boolean wantMenu = args.length == 1 || matches(args[1], "메뉴");
        int page = parsePageArgument(args, 1);

        if (menus != null && wantMenu) {
            menus.openPendingInvites(player);
            return;
        }

        execute(player,
                () -> network.pendingInvites(player),
                result -> displayInviteList(player, result.data(), page),
                messages.format("invites.failure-prefix"),
                !wantMenu);
    }

    private void handleAccept(Player player, String[] args) {
        String inviteId = args.length > 1 ? args[1] : null;
        execute(player,
                () -> network.acceptInvite(player, inviteId),
                result -> {
                    messages.send(player, "accept.success");
                    maybeConnectToIslandServer(player);
                },
                messages.format("accept.failure-prefix"),
                true);
    }

    private void handleDeny(Player player, String[] args) {
        String inviteId = args.length > 1 ? args[1] : null;
        execute(player,
                () -> network.denyInvite(player, inviteId),
                result -> messages.send(player, "deny.success"),
                messages.format("deny.failure-prefix"),
                true);
    }

    private void handleMembers(Player player, String[] args) {
        boolean wantMenu = args.length == 1 || matches(args[1], "메뉴");
        int page = parsePageArgument(args, 1);

        if (menus != null && wantMenu) {
            menus.openMembersMenu(player);
            return;
        }

        String cacheKey = ClientCacheKeys.members(player.getUniqueId().toString());
        if (cache.enabled() && wantMenu && args.length == 1) {
            cache.get(cacheKey).ifPresent(data -> displayMemberList(player, data, page));
        }

        execute(player,
                () -> network.listMembers(player, null),
                result -> {
                    cache.put(cacheKey, result.data());
                    displayMemberList(player, result.data(), page);
                },
                messages.format("members.failure-prefix"),
                !wantMenu);
    }

    private void handleInfo(Player player, String[] args) {
        String ownerKey = args.length > 1 ? args[1] : player.getUniqueId().toString();
        String cacheKey = ClientCacheKeys.island(ownerKey);
        if (cache.enabled()) {
            cache.get(cacheKey).ifPresent(data -> {
                if (data.has("name")) {
                    messages.send(player, "info.cache", data.get("name").getAsString());
                }
            });
        }
        String ownerIdentifier = args.length > 1 ? args[1] : null;
        execute(player,
                () -> network.islandInfo(player, ownerIdentifier),
                result -> {
                    cache.put(cacheKey, result.data());
                    messages.send(player, "info.success", result.data().toString());
                },
                messages.format("info.failure-prefix"),
                true);
    }

    private void handleRanking(Player player, String[] args) {
        if (args.length == 1 || matches(args[1], "메뉴")) {
            if (menus != null) {
                menus.openFarmRanking(player);
            } else {
                messages.send(player, "ranking.menu-unavailable");
            }
            return;
        }

        if (matches(args[1], "기여도")) {
            if (menus != null && args.length == 2) {
                menus.openFarmMemberRanking(player, null);
                return;
            }
            if (args.length < 3) {
                messages.send(player, "ranking.contribution-usage", baseCommand);
                return;
            }
            String islandId = args[2];
            int page = parsePageArgument(args, 3);
            int limit = resolvePageLimit(page);
            execute(player,
                    () -> network.farmRankingMembers(player, islandId, limit),
                    result -> displayRankingMembers(player, result.data(), page),
                    messages.format("ranking.contribution-failure-prefix"),
                    true);
            return;
        }

        if (matches(args[1], "스냅샷")) {
            if (!player.hasPermission("ssb2.command.farm.admin")) {
                messages.send(player, "ranking.snapshot-permission");
                return;
            }
            if (args.length < 3) {
                messages.send(player, "ranking.snapshot-usage", baseCommand);
                return;
            }
            String periodId = args[2];
            String displayName = args.length >= 4 ? args[3] : periodId;
            int limit = args.length >= 5 ? parsePositiveInt(args[4], 10) : 10;
            execute(player,
                    () -> network.farmRankingSnapshot(player, periodId, displayName, limit),
                    result -> messages.send(player, "ranking.snapshot-success"),
                    messages.format("ranking.snapshot-failure-prefix"),
                    true);
            return;
        }

        if (matches(args[1], "페이지")) {
            int page = args.length >= 3 ? parsePositiveInt(args[2], 1) : 1;
            int limit = resolvePageLimit(page);
            execute(player,
                    () -> network.farmRankingTop(player, limit),
                    result -> displayRankingTop(player, result.data(), page),
                    messages.format("ranking.page-failure-prefix"),
                    true);
            return;
        }

        if (isNumeric(args[1])) {
            int page = parsePositiveInt(args[1], 1);
            int limit = resolvePageLimit(page);
            execute(player,
                    () -> network.farmRankingTop(player, limit),
                    result -> displayRankingTop(player, result.data(), page),
                    messages.format("ranking.page-failure-prefix"),
                    true);
            return;
        }

        messages.send(player, "ranking.subcommands");
    }

    private void handleHistory(Player player, String[] args) {
        if (menus != null && (args.length == 1 || matches(args[1], "메뉴"))) {
            menus.openFarmHistory(player, menus.getHistoryPage(player));
            return;
        }

        if (matches(args[1], "페이지")) {
            int page = args.length >= 3 ? parsePositiveInt(args[2], 1) : 1;
            fetchHistoryPage(player, page);
            return;
        }

        if (matches(args[1], "상세")) {
            if (args.length < 3) {
                messages.send(player, "history.detail-usage", baseCommand);
                return;
            }
            String periodId = args[2];
            execute(player,
                    () -> network.farmHistoryDetail(player, periodId),
                    result -> displayHistoryDetail(player, result.data()),
                messages.format("history.detail-failure-prefix"),
                true);
            return;
        }

        if (args.length >= 2 && isNumeric(args[1])) {
            fetchHistoryPage(player, parsePositiveInt(args[1], 1));
            return;
        }

        messages.send(player, "history.subcommands");
    }

    private void handleRewards(Player player, String[] args) {
        boolean wantMenu = args.length == 1 || matches(args[1], "메뉴");
        if (menus != null && wantMenu) {
            menus.openFarmRewards(player);
            return;
        }

        execute(player,
                () -> network.farmRewardTable(player),
                result -> displayRewardTable(player, result.data()),
                messages.format("rewards.failure-prefix"),
                !wantMenu);
    }

    private void handleShop(Player player, String[] args) {
        boolean wantMenu = args.length == 1 || matches(args[1], "메뉴");
        if (menus != null && wantMenu) {
            menus.openFarmShop(player);
            return;
        }

        execute(player,
                () -> network.farmShopTable(player),
                result -> displayShopTable(player, result.data()),
                messages.format("shop.failure-prefix"),
                !wantMenu);
    }

    private void handleBorder(Player player, String[] args) {
        if (menus != null && (args.length == 1 || matches(args[1], "메뉴"))) {
            menus.openBorderMenu(player);
            return;
        }

        if (matches(args[1], "토글")) {
            execute(player,
                    () -> network.toggleWorldBorder(player),
                    result -> {
                        JsonObject data = result.data();
                        boolean enabled = data != null && data.has("enabled") && data.get("enabled").getAsBoolean();
                        messages.send(player, "border.toggle-success", enabled ? "활성화" : "비활성화");
                    },
                    messages.format("border.failure-toggle-prefix"),
                    true);
            return;
        }

        if (matches(args[1], "색상")) {
            if (args.length < 3) {
                messages.send(player, "border.color-usage", baseCommand);
                return;
            }
            String mapped = mapBorderColor(args[2]);
            execute(player,
                    () -> network.setWorldBorderColor(player, mapped),
                    result -> {
                        JsonObject data = result.data();
                        String applied = data != null && data.has("color") ? data.get("color").getAsString() : mapped;
                        messages.send(player, "border.color-success", applied.toUpperCase(Locale.ROOT));
                    },
                    messages.format("border.color-failure-prefix"),
                    true);
            return;
        }

        messages.send(player, "border.subcommands");
    }

    private void handleDisband(Player player, String[] args) {
        if (args.length == 1) {
            messages.send(player, "disband.confirm", baseCommand);
            return;
        }
        if (matches(args[1], "확인", "confirm")) {
            execute(player,
                    () -> network.disbandIsland(player),
                    result -> messages.send(player, "disband.success"),
                    messages.format("disband.failure-prefix"),
                    true);
        } else {
            messages.send(player, "disband.confirm", baseCommand);
        }
    }

    private void handleAdmin(Player player, String[] args) {
        if (!player.hasPermission("ssb2.command.farm.admin")) {
            messages.send(player, "admin.permission-required");
            return;
        }

        if (args.length == 1) {
            sendAdminHelp(player);
            return;
        }

        if (matches(args[1], "퀘스트")) {
            if (args.length < 4 || !matches(args[2], "발급")) {
                messages.send(player, "admin.quest-usage", baseCommand);
                return;
            }
            if (menus == null) {
                messages.send(player, "admin.quest-menu-unavailable");
                return;
            }
            QuestType type = parseQuestType(args[3]);
            if (type == null) {
                messages.send(player, "admin.quest-invalid-type");
                return;
            }
            if (args.length < 5) {
                messages.send(player, "admin.quest-usage", baseCommand);
                return;
            }
            int count = parsePositiveInt(args[4], -1);
            if (count <= 0) {
                messages.send(player, "admin.quest-invalid-count");
                return;
            }
            menus.assignQuest(player, type, count);
            return;
        }

        if (matches(args[1], "순위") && matches(args.length >= 3 ? args[2] : "", "스냅샷")) {
            if (args.length < 4) {
                messages.send(player, "admin.snapshot-usage", baseCommand);
                return;
            }
            String periodId = args[3];
            String displayName = args.length >= 5 ? args[4] : periodId;
            int limit = args.length >= 6 ? parsePositiveInt(args[5], 10) : 10;
            execute(player,
                    () -> network.farmRankingSnapshot(player, periodId, displayName, limit),
                    result -> messages.send(player, "admin.snapshot-success"),
                    messages.format("admin.snapshot-failure-prefix"),
                    true);
            return;
        }

        sendAdminHelp(player);
    }

    private void fetchHistoryPage(Player player, int page) {
        execute(player,
                () -> network.farmHistoryList(player, page, DEFAULT_PAGE_SIZE),
                result -> displayHistoryList(player, result.data(), page),
                messages.format("history.list-failure-prefix"),
                true);
    }

    private void displayInviteList(Player player, JsonObject data, int page) {
        JsonArray invites = data != null && data.has("invites") && data.get("invites").isJsonArray()
                ? data.getAsJsonArray("invites")
                : new JsonArray();
        List<String> lines = new ArrayList<>();
        for (JsonElement element : invites) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject invite = element.getAsJsonObject();
            String islandName = getString(invite, "islandName", "알 수 없음");
            String ownerName = getString(invite, "ownerName", "알 수 없음");
            int members = invite.has("membersCount") ? invite.get("membersCount").getAsInt() : 0;
            int limit = invite.has("membersLimit") ? invite.get("membersLimit").getAsInt() : 0;
            String inviteId = getString(invite, "inviteId", "-");
            lines.add(ChatColor.GRAY + "- " + ChatColor.AQUA + islandName + ChatColor.GRAY + " (팜장: " + ownerName + ", 인원 " + members + (limit > 0 ? "/" + limit : "") + ") " + ChatColor.DARK_GRAY + "[#" + inviteId + "]");
        }
        displayPagedList(player, lines, page,
                messages.format("invite.list-header"),
                messages.format("invite.list-empty"),
                messages.format("invite.list-footer", baseCommand));
    }

    private void displayMemberList(Player player, JsonObject data, int page) {
        JsonArray members = data != null && data.has("members") && data.get("members").isJsonArray()
                ? data.getAsJsonArray("members")
                : new JsonArray();
        List<String> lines = new ArrayList<>();
        for (JsonElement element : members) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject member = element.getAsJsonObject();
            String name = getString(member, "name", "알 수 없음");
            String role = getString(member, "role", "UNKNOWN");
            boolean online = member.has("online") && member.get("online").getAsBoolean();
            lines.add(ChatColor.GRAY + "- " + ChatColor.GREEN + name + ChatColor.GRAY + " (" + translateRole(role) + ", " + (online ? ChatColor.AQUA + "온라인" : ChatColor.DARK_GRAY + "오프라인") + ChatColor.GRAY + ")");
        }
        displayPagedList(player, lines, page,
                messages.format("members.header"),
                messages.format("members.empty"),
                messages.format("members.footer", baseCommand));
    }

    private void displayRankingTop(Player player, JsonObject data, int page) {
        JsonArray islands = data != null && data.has("islands") && data.get("islands").isJsonArray()
                ? data.getAsJsonArray("islands")
                : new JsonArray();
        List<String> lines = new ArrayList<>();
        int index = 1;
        for (JsonElement element : islands) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject island = element.getAsJsonObject();
            String islandName = getString(island, "islandName", "이름 없음");
            String ownerName = getString(island, "ownerName", "알 수 없음");
            long points = island.has("points") ? island.get("points").getAsLong() : 0L;
            lines.add(ChatColor.GRAY + "#" + (index++) + " " + ChatColor.GOLD + islandName + ChatColor.GRAY + " (" + ownerName + ", " + formatNumber(points) + "점)");
        }
        displayPagedList(player, lines, page,
                messages.format("ranking.top-header"),
                messages.format("ranking.top-empty"),
                messages.format("ranking.top-footer", baseCommand));
    }

    private void displayRankingMembers(Player player, JsonObject data, int page) {
        JsonArray members = data != null && data.has("members") && data.get("members").isJsonArray()
                ? data.getAsJsonArray("members")
                : new JsonArray();
        List<String> lines = new ArrayList<>();
        int index = 1;
        for (JsonElement element : members) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject member = element.getAsJsonObject();
            String name = getString(member, "playerName", "알 수 없음");
            long contribution = member.has("points") ? member.get("points").getAsLong() : 0L;
            lines.add(ChatColor.GRAY + "#" + (index++) + " " + ChatColor.GREEN + name + ChatColor.GRAY + " - " + formatNumber(contribution) + "점");
        }
        displayPagedList(player, lines, page,
                messages.format("ranking.members-header"),
                messages.format("ranking.members-empty"),
                messages.format("ranking.members-footer", baseCommand));
    }

    private void displayRewardTable(Player player, JsonObject data) {
        JsonArray rewards = data != null && data.has("rewards") && data.get("rewards").isJsonArray()
                ? data.getAsJsonArray("rewards")
                : new JsonArray();
        messages.send(player, "rewards.header");
        if (rewards.size() == 0) {
            messages.send(player, "rewards.empty");
            return;
        }
        for (JsonElement element : rewards) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject reward = element.getAsJsonObject();
            int minRank = reward.has("minRank") ? reward.get("minRank").getAsInt() : 1;
            int maxRank = reward.has("maxRank") ? reward.get("maxRank").getAsInt() : minRank;
            String title = getString(reward, "title", "보상");
            int moonlight = reward.has("moonlight") ? reward.get("moonlight").getAsInt() : 0;
            int farmPoints = reward.has("farmPoints") ? reward.get("farmPoints").getAsInt() : 0;
            player.sendMessage(ChatColor.YELLOW + "- 순위 " + minRank + (minRank == maxRank ? "" : "~" + maxRank) + ChatColor.GRAY + " : " + ChatColor.GOLD + title);
            player.sendMessage(ChatColor.GRAY + "  · 달빛 " + formatNumber(moonlight) + " / 팜 포인트 " + formatNumber(farmPoints));
        }
    }

    private void displayShopTable(Player player, JsonObject data) {
        JsonArray items = data != null && data.has("items") && data.get("items").isJsonArray()
                ? data.getAsJsonArray("items")
                : new JsonArray();
        messages.send(player, "shop.header");
        if (items.size() == 0) {
            messages.send(player, "shop.empty");
            return;
        }
        for (JsonElement element : items) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject item = element.getAsJsonObject();
            boolean enabled = !item.has("enabled") || item.get("enabled").getAsBoolean();
            if (!enabled) {
                continue;
            }
            String title = getString(item, "title", "아이템");
            String currency = translateCurrency(getString(item, "currency", "none"));
            int price = item.has("price") ? item.get("price").getAsInt() : 0;
            player.sendMessage(ChatColor.GRAY + "- " + ChatColor.AQUA + title + ChatColor.GRAY + " (" + currency + " " + formatNumber(price) + ")");
        }
    }

    private void displayHistoryList(Player player, JsonObject data, int page) {
        JsonArray periods = data != null && data.has("periods") && data.get("periods").isJsonArray()
                ? data.getAsJsonArray("periods")
                : new JsonArray();
        List<String> lines = new ArrayList<>();
        for (JsonElement element : periods) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject period = element.getAsJsonObject();
            String periodId = getString(period, "periodId", "");
            String displayName = getString(period, "displayName", periodId);
            long createdAt = period.has("createdAt") ? period.get("createdAt").getAsLong() : 0L;
            int entries = period.has("entries") ? period.get("entries").getAsInt() : 0;
            lines.add(ChatColor.GRAY + "- " + ChatColor.YELLOW + displayName + ChatColor.GRAY + " (" + DATE_FORMAT.format(Instant.ofEpochMilli(createdAt)) + ", 순위 " + entries + "개, ID: " + periodId + ")");
        }
        displayPagedList(player, lines, page,
                messages.format("history.list-header"),
                messages.format("history.list-empty"),
                messages.format("history.list-footer", baseCommand));
    }

    private void displayHistoryDetail(Player player, JsonObject data) {
        String displayName = getString(data, "displayName", getString(data, "periodId", ""));
        messages.send(player, "history.detail-header", displayName);
        if (!data.has("entries") || !data.get("entries").isJsonArray() || data.getAsJsonArray("entries").size() == 0) {
            messages.send(player, "history.detail-empty");
            return;
        }
        for (JsonElement element : data.getAsJsonArray("entries")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            int rank = entry.has("rank") ? entry.get("rank").getAsInt() : 0;
            String islandName = getString(entry, "islandName", "이름 없음");
            String ownerName = getString(entry, "ownerName", "알 수 없음");
            long points = entry.has("points") ? entry.get("points").getAsLong() : 0L;
            player.sendMessage(ChatColor.GRAY + "#" + rank + " " + ChatColor.AQUA + islandName + ChatColor.GRAY + " (" + ownerName + ", " + formatNumber(points) + "점)");
        }
    }

    private void displayPagedList(Player player,
                                  List<String> lines,
                                  int page,
                                  String headerTitle,
                                  String emptyMessage,
                                  String footer) {
        if (lines.isEmpty()) {
            if (emptyMessage != null && !emptyMessage.isBlank()) {
                player.sendMessage(emptyMessage);
            }
            return;
        }
        int totalPages = Math.max(1, (lines.size() + DEFAULT_PAGE_SIZE - 1) / DEFAULT_PAGE_SIZE);
        int currentPage = Math.min(Math.max(1, page), totalPages);
        int start = (currentPage - 1) * DEFAULT_PAGE_SIZE;
        int end = Math.min(start + DEFAULT_PAGE_SIZE, lines.size());

        player.sendMessage(messages.format("list.header-format", headerTitle, currentPage, totalPages));
        for (int i = start; i < end; i++) {
            player.sendMessage(lines.get(i));
        }
        if (footer != null && !footer.isBlank()) {
            player.sendMessage(footer);
        }
    }

    private void sendAdminHelp(Player player) {
        for (String line : messages.list("admin.help-lines", baseCommand)) {
            player.sendMessage(line);
        }
    }

    private void showHelp(Player player, int page) {
        List<String> lines = new ArrayList<>(messages.list("help.main", baseCommand));
        displayPagedList(player, lines, page,
                messages.format("help.header-title", baseCommand),
                messages.format("help.empty"),
                messages.format("help.footer", baseCommand));
    }

    private void execute(Player player,
                         Supplier<CompletableFuture<NetworkOperationResult>> action,
                         Consumer<NetworkOperationResult> onSuccess,
                         String failurePrefix,
                         boolean showProgress) {
        if (showProgress) {
            messages.send(player, "general.processing");
        }
        CompletableFuture<NetworkOperationResult> future;
        try {
            future = action.get();
        } catch (Exception ex) {
            messages.send(player, "general.error", ex.getMessage() != null ? ex.getMessage() : ex.toString());
            return;
        }
        future.whenComplete((result, throwable) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (throwable != null) {
                messages.send(player, "general.error", throwable.getMessage() != null ? throwable.getMessage() : throwable.toString());
                return;
            }
            if (result == null) {
                messages.send(player, "general.result-missing");
                return;
            }
            if (result.failed()) {
                String message = messages.format("general.failure-format", failurePrefix, describeError(result));
                player.sendMessage(message);
                return;
            }
            onSuccess.accept(result);
        }));
    }

    private String describeError(NetworkOperationResult result) {
        String code = result.errorCode() != null ? result.errorCode() : "UNKNOWN";
        String message = result.errorMessage() != null ? result.errorMessage() : "";
        return message.isEmpty() ? code : code + " (" + message + ")";
    }

    private QuestType parseQuestType(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim()) {
            case "일간" -> QuestType.DAILY;
            case "주간" -> QuestType.WEEKLY;
            default -> null;
        };
    }

    private int parsePositiveInt(String raw, int defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(raw);
            return value > 0 ? value : defaultValue;
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private int parsePageArgument(String[] args, int startIndex) {
        if (args.length <= startIndex) {
            return 1;
        }
        if (matches(args[startIndex], "페이지")) {
            return args.length > startIndex + 1 ? parsePositiveInt(args[startIndex + 1], 1) : 1;
        }
        if (isNumeric(args[startIndex])) {
            return parsePositiveInt(args[startIndex], 1);
        }
        return 1;
    }

    private int resolvePageLimit(int page) {
        return Math.max(1, page) * DEFAULT_PAGE_SIZE;
    }

    private boolean matches(String input, String... options) {
        if (input == null) {
            return false;
        }
        for (String option : options) {
            if (option != null && input.equalsIgnoreCase(option)) {
                return true;
            }
        }
        return false;
    }

    private boolean isNumeric(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        for (int i = 0; i < raw.length(); i++) {
            if (!Character.isDigit(raw.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private String translateRole(String role) {
        return switch (role.toUpperCase(Locale.ROOT)) {
            case "OWNER" -> "팜장";
            case "CO_OWNER", "MANAGER" -> "부팜장";
            case "MEMBER" -> "구성원";
            default -> role;
        };
    }

    private String translateCurrency(String currency) {
        return switch (currency.toLowerCase(Locale.ROOT)) {
            case "moonlight" -> "달빛";
            case "farmpoint", "farm_points", "farm" -> "팜 포인트";
            case "none" -> "무료";
            default -> currency;
        };
    }

    private String mapBorderColor(String raw) {
        String lower = raw.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "초록", "green" -> "GREEN";
            case "파랑", "파란", "blue" -> "BLUE";
            case "빨강", "red" -> "RED";
            default -> raw.toUpperCase(Locale.ROOT);
        };
    }

    private String getString(JsonObject obj, String key, String defaultValue) {
        if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return defaultValue;
        }
        return obj.get(key).getAsString();
    }

    private String formatNumber(long value) {
        return String.format(Locale.KOREA, "%,d", value);
    }

    private void maybeConnectToIslandServer(Player player) {
        if (velocitySettings == null || !velocitySettings.enabled()) {
            return;
        }
        String target = velocitySettings.targetServer();
        if (target == null || target.isBlank()) {
            return;
        }
        try {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Connect");
            out.writeUTF(target);
            player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to request server transfer for " + player.getName() + ": " + ex.getMessage());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("메뉴", "도움말", "퀘스트", "초대", "초대목록", "수락", "거절", "구성원", "정보", "순위", "히스토리", "보상", "상점", "경계", "해체", "disband", "관리자"), args[0]);
        }

        String sub = args[0];
        if (matches(sub, "도움말")) {
            if (args.length == 2) {
                return Collections.singletonList("<페이지>");
            }
            return Collections.emptyList();
        }
        if (matches(sub, "퀘스트")) {
            if (args.length == 2) {
                return filter(Arrays.asList("메뉴", "발급", "목록"), args[1]);
            }
            if (args.length == 3 && (matches(args[1], "발급") || matches(args[1], "목록"))) {
                return filter(Arrays.asList("일간", "주간"), args[2]);
            }
            if (args.length == 4 && matches(args[1], "발급")) {
                return Collections.singletonList("<개수>");
            }
            return Collections.emptyList();
        }

        if (matches(sub, "초대목록")) {
            if (args.length == 2) {
                return filter(Arrays.asList("메뉴", "페이지"), args[1]);
            }
            if (args.length == 3 && matches(args[1], "페이지")) {
                return Collections.singletonList("<번호>");
            }
            return Collections.emptyList();
        }

        if (matches(sub, "구성원")) {
            if (args.length == 2) {
                return filter(Arrays.asList("메뉴", "페이지"), args[1]);
            }
            if (args.length == 3 && matches(args[1], "페이지")) {
                return Collections.singletonList("<번호>");
            }
            return Collections.emptyList();
        }

        if (matches(sub, "순위")) {
            if (args.length == 2) {
                return filter(Arrays.asList("메뉴", "기여도", "스냅샷", "페이지"), args[1]);
            }
            if (args.length == 3 && matches(args[1], "기여도")) {
                return Collections.singletonList("<섬ID>");
            }
            if (args.length == 3 && matches(args[1], "스냅샷", "페이지")) {
                return Collections.singletonList("<값>");
            }
            if (args.length == 4 && matches(args[1], "스냅샷")) {
                return Collections.singletonList("[표시이름]");
            }
            if (args.length == 5 && matches(args[1], "스냅샷")) {
                return Collections.singletonList("[제한]");
            }
            return Collections.emptyList();
        }

        if (matches(sub, "히스토리")) {
            if (args.length == 2) {
                return filter(Arrays.asList("메뉴", "페이지", "상세"), args[1]);
            }
            if (args.length == 3 && matches(args[1], "페이지", "상세")) {
                return Collections.singletonList("<값>");
            }
            return Collections.emptyList();
        }

        if (matches(sub, "경계")) {
            if (args.length == 2) {
                return filter(Arrays.asList("메뉴", "토글", "색상"), args[1]);
            }
            if (args.length == 3 && matches(args[1], "색상")) {
                return filter(Arrays.asList("초록", "파랑", "빨강"), args[2]);
            }
            return Collections.emptyList();
        }

        if (matches(sub, "해체", "disband")) {
            if (args.length == 2) {
                return filter(Arrays.asList("확인", "confirm"), args[1]);
            }
            return Collections.emptyList();
        }

        if (matches(sub, "관리자")) {
            if (args.length == 2) {
                return filter(Arrays.asList("퀘스트", "순위"), args[1]);
            }
            if (args.length == 3 && matches(args[1], "퀘스트")) {
                return filter(Collections.singletonList("발급"), args[2]);
            }
            if (args.length == 4 && matches(args[1], "퀘스트") && matches(args[2], "발급")) {
                return filter(Arrays.asList("일간", "주간"), args[3]);
            }
            if (args.length == 5 && matches(args[1], "퀘스트") && matches(args[2], "발급")) {
                return Collections.singletonList("<개수>");
            }
            if (args.length == 3 && matches(args[1], "순위")) {
                return filter(Collections.singletonList("스냅샷"), args[2]);
            }
            if (args.length == 4 && matches(args[1], "순위") && matches(args[2], "스냅샷")) {
                return Collections.singletonList("<기간ID>");
            }
            if (args.length == 5 && matches(args[1], "순위") && matches(args[2], "스냅샷")) {
                return Collections.singletonList("[표시이름]");
            }
            if (args.length == 6 && matches(args[1], "순위") && matches(args[2], "스냅샷")) {
                return Collections.singletonList("[제한]");
            }
            return Collections.emptyList();
        }

        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String current) {
        if (current == null || current.isBlank()) {
            return options;
        }
        String lower = current.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(option);
            }
        }
        return matches;
    }
}
