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
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.math.RoundingMode;
import java.util.Base64;

import wiki.creeper.superiorskyblockIntegeration.api.NetworkOperationResult;
import wiki.creeper.superiorskyblockIntegeration.api.NetworkSkyblockService;
import wiki.creeper.superiorskyblockIntegeration.client.cache.ClientCache;
import wiki.creeper.superiorskyblockIntegeration.client.cache.ClientCacheKeys;
import wiki.creeper.superiorskyblockIntegeration.client.lang.Messages;
import wiki.creeper.superiorskyblockIntegeration.client.menu.IslandMenuManager;
import wiki.creeper.superiorskyblockIntegeration.client.model.PlayerSummary;
import wiki.creeper.superiorskyblockIntegeration.client.model.BankHistoryPage;
import wiki.creeper.superiorskyblockIntegeration.client.model.BankSnapshot;
import wiki.creeper.superiorskyblockIntegeration.client.model.RolePermissionSnapshot;
import wiki.creeper.superiorskyblockIntegeration.common.quest.QuestType;
import wiki.creeper.superiorskyblockIntegeration.config.PluginConfig;

/**
 * Handles the unified farm command (/팜) using configurable messages.
 */
public final class FarmCommand implements CommandExecutor, TabCompleter {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private static final Set<Integer> POWER_REWARD_TIERS = Set.of(
            5000, 10000, 50000, 100000, 500000, 750000, 1_000_000, 3_000_000, 5_000_000, 10_000_000
    );

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
            case "채팅", "chat" -> handleChat(player, args);
            case "추방", "kick", "cnqkd" -> handleKick(player, args);
            case "정보" -> handleInfo(player, args);
            case "포인트", "points" -> handleFarmPoints(player);
            case "호퍼", "hopper" -> handleFarmHopper(player);
            case "평가", "vudrk", "rating" -> handleFarmRating(player, args);
            case "홈", "home", "homes", "gha" -> handleHome(player, args);
            case "sethome" -> handleSetHome(player);
            case "워프", "warp", "warps", "dnjvm" -> handleWarp(player, args);
            case "규칙", "rule", "rules", "rbclr" -> handleRules(player, args);
            case "순위" -> handleRanking(player, args);
            case "히스토리" -> handleHistory(player, args);
            case "보상" -> handleRewards(player, args);
            case "상점" -> handleShop(player, args);
            case "금고", "bank", "safe", "account" -> handleBank(player, args);
            case "알바", "coop" -> handleCoop(player, args);
            case "차단", "ban" -> handleBan(player, args);
            case "권한" -> handlePermissions(player, args);
            case "경계" -> handleBorder(player, args);
            case "관리", "manage" -> handleManage(player, args);
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

    private void handleChat(Player player, String[] args) {
        if (args.length < 2) {
            messages.send(player, "chat.usage", baseCommand);
            return;
        }
        String raw = joinArgs(args, 1).replace('\n', ' ').replace('\r', ' ').trim();
        if (raw.isEmpty()) {
            messages.send(player, "chat.empty");
            return;
        }
        String message = raw.length() > 256 ? raw.substring(0, 256) : raw;
        String encoded = Base64.getEncoder().encodeToString(message.getBytes(StandardCharsets.UTF_8));
        execute(player,
                () -> network.farmChat(player, encoded),
                result -> {
                    // broadcast will deliver the formatted message to the sender as well
                },
                messages.format("chat.failure-prefix"),
                false);
    }

    private void handleKick(Player player, String[] args) {
        if (args.length < 3) {
            messages.send(player, "kick.usage", baseCommand);
            return;
        }
        String target = args[1];
        if (target.equalsIgnoreCase(player.getName())) {
            messages.send(player, "kick.self");
            return;
        }
        String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim();
        if (reason.isBlank()) {
            messages.send(player, "kick.reason-missing");
            return;
        }
        execute(player,
                () -> network.kickMember(player, target, reason),
                result -> {
                    String displayName = target;
                    JsonObject data = result.data();
                    if (data != null && data.has("targetName") && !data.get("targetName").isJsonNull()) {
                        displayName = data.get("targetName").getAsString();
                    }
                    messages.send(player, "kick.success", displayName);
                },
                messages.format("kick.failure-prefix"),
                true);
    }

    private void handlePermissions(Player player, String[] args) {
        if (menus != null && (args.length == 1 || matches(args[1], "메뉴"))) {
            menus.openRolePermissions(player);
            return;
        }

        if (args.length >= 2 && matches(args[1], "목록")) {
            String roleFilter = args.length >= 3 ? args[2] : null;
            execute(player,
                    () -> network.rolePermissions(player),
                    result -> displayRolePermissions(player, RolePermissionSnapshot.from(result.data()), roleFilter),
                    messages.format("permissions.failure-prefix"),
                    true);
            return;
        }

        if (args.length >= 2 && matches(args[1], "설정")) {
            if (args.length < 5) {
                messages.send(player, "permissions.update-usage", baseCommand);
                return;
            }
            String roleName = args[2];
            String rawPrivilege = args[3];
            String privilege = rawPrivilege.replace(' ', '_').replace('-', '_').toUpperCase(Locale.ROOT);
            Boolean enabled = parsePermissionState(args[4]);
            if (enabled == null) {
                messages.send(player, "permissions.invalid-state");
                return;
            }
            execute(player,
                    () -> network.updateRolePermission(player, roleName, privilege, enabled),
                    result -> {
                        JsonObject data = result.data();
                        String roleDisplay = getString(data, "displayName", roleName);
                        String privilegeDisplay = friendlyPrivilegeName(getString(data, "privilege", privilege));
                        String state = enabled ? "허용" : "차단";
                        messages.send(player, "permissions.update-success", roleDisplay, privilegeDisplay, state);
                    },
                    messages.format("permissions.failure-prefix"),
                    true);
            return;
        }

        messages.send(player, "permissions.subcommands");
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

    private void handleFarmPoints(Player player) {
        execute(player,
                () -> network.farmPoints(player),
                result -> {
                    JsonObject data = result.data();
                    if (data == null || !data.has("hasIsland") || !data.get("hasIsland").getAsBoolean()) {
                        messages.send(player, "points.no-island");
                        return;
                    }
                    String islandName = getString(data, "islandName", player.getName());
                    long total = data.has("totalPoints") ? data.get("totalPoints").getAsLong() : 0L;
                    long daily = data.has("dailyPoints") ? data.get("dailyPoints").getAsLong() : 0L;
                    long weekly = data.has("weeklyPoints") ? data.get("weeklyPoints").getAsLong() : 0L;
                    messages.send(player, "points.header", islandName);
                    player.sendMessage(messages.format("points.total", formatNumber(total)));
                    player.sendMessage(messages.format("points.daily", formatNumber(daily)));
                    player.sendMessage(messages.format("points.weekly", formatNumber(weekly)));
                },
                messages.format("points.failure-prefix"),
                true);
    }

    private void handleFarmHopper(Player player) {
        execute(player,
                () -> network.farmHopperState(player),
                result -> {
                    JsonObject data = result.data();
                    if (data == null || !data.has("hasIsland") || !data.get("hasIsland").getAsBoolean()) {
                        messages.send(player, "hopper.no-island");
                        return;
                    }
                    String currentRaw = getString(data, "current", "0");
                    long limit = data.has("limit") ? safeGetLong(data.get("limit")) : 0L;
                    messages.send(player, "hopper.summary",
                            formatBigNumber(currentRaw),
                            formatNumber(limit));
                },
                messages.format("hopper.failure-prefix"),
                true);
    }

    private void handleFarmRating(Player player, String[] args) {
        if (args.length < 2) {
            messages.send(player, "rating.usage", baseCommand);
            return;
        }
        int ratingValue;
        try {
            ratingValue = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            messages.send(player, "rating.invalid-number");
            return;
        }
        if (ratingValue < 0 || ratingValue > 5) {
            messages.send(player, "rating.invalid-range");
            return;
        }
        int finalRating = ratingValue;
        execute(player,
                () -> network.farmRateIsland(player, finalRating),
                result -> {
                    JsonObject data = result.data();
                    int applied = data != null && data.has("rating") ? data.get("rating").getAsInt() : -1;
                    int previous = data != null && data.has("previousRating") ? data.get("previousRating").getAsInt() : -1;
                    if (applied <= 0) {
                        messages.send(player, "rating.cleared");
                    } else {
                        messages.send(player, "rating.success", applied);
                        if (previous >= 0 && previous != applied) {
                            messages.send(player, "rating.previous", previous);
                        }
                    }
                },
                messages.format("rating.failure-prefix"),
                true);
    }

    private void handleHome(Player player, String[] args) {
        if (menus == null) {
            messages.send(player, "general.menu-disabled");
            return;
        }
        if (args.length > 1 && matches(args[1], "설정", "set")) {
            handleSetHome(player);
            return;
        }
        menus.openHomeWarps(player);
    }

    private void handleSetHome(Player player) {
        if (menus == null) {
            messages.send(player, "general.menu-disabled");
            return;
        }
        menus.requestHomeWarpCreation(player);
    }

    private void handleWarp(Player player, String[] args) {
        if (menus == null) {
            messages.send(player, "general.menu-disabled");
            return;
        }
        if (args.length == 1 || matches(args[1], "메뉴")) {
            menus.openGlobalWarpBrowser(player, 1);
            return;
        }
        if (matches(args[1], "페이지") && args.length >= 3) {
            int page = parsePositiveInt(args[2], 1);
            menus.openGlobalWarpBrowser(player, page);
            return;
        }
        if (isNumeric(args[1])) {
            int page = parsePositiveInt(args[1], 1);
            menus.openGlobalWarpBrowser(player, page);
            return;
        }
        menus.openPlayerWarps(player, args[1]);
    }

    private void handleRules(Player player, String[] args) {
        if (args.length == 1 || matches(args[1], "목록", "list")) {
            execute(player,
                    () -> network.listIslandRules(player),
                    result -> displayRules(player, result.data()),
                    messages.format("rules.failure-prefix"),
                    true);
            return;
        }

        if (matches(args[1], "추가", "add")) {
            if (args.length < 3) {
                messages.send(player, "rules.add-usage", baseCommand);
                return;
            }
            String ruleText = joinArgs(args, 2).trim();
            if (ruleText.isEmpty()) {
                messages.send(player, "rules.add-usage", baseCommand);
                return;
            }
            execute(player,
                    () -> network.addIslandRule(player, ruleText),
                    result -> {
                        JsonObject data = result.data();
                        String added = getString(data, "added", ruleText);
                        messages.send(player, "rules.add-success", added);
                        displayRules(player, data);
                    },
                    messages.format("rules.failure-prefix"),
                    true);
            return;
        }

        if (matches(args[1], "삭제", "remove")) {
            if (args.length < 3) {
                messages.send(player, "rules.remove-usage", baseCommand);
                return;
            }
            int index = parsePositiveInt(args[2], -1);
            if (index <= 0) {
                messages.send(player, "rules.remove-usage", baseCommand);
                return;
            }
            execute(player,
                    () -> network.removeIslandRule(player, index),
                    result -> {
                        JsonObject data = result.data();
                        int removedIndex = data != null && data.has("removedIndex") ? data.get("removedIndex").getAsInt() : index;
                        String removed = getString(data, "removed", "");
                        messages.send(player, "rules.remove-success", removedIndex, removed);
                        displayRules(player, data);
                    },
                    messages.format("rules.failure-prefix"),
                    true);
            return;
        }

        messages.send(player, "rules.subcommands", baseCommand);
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

    private void handleBank(Player player, String[] args) {
        boolean wantMenu = args.length == 1 || matches(args[1], "메뉴", "menu");
        if (menus != null && wantMenu) {
            menus.openBankMenu(player);
            return;
        }

        if (args.length == 1 || matches(args[1], "정보", "info", "state")) {
            requestBankState(player);
            return;
        }

        if (matches(args[1], "입금", "deposit")) {
            if (args.length < 3) {
                player.sendMessage(ChatColor.RED + "[Skyblock] 입금할 금액을 입력해주세요.");
                return;
            }
            BigDecimal amount = parseAmount(args[2]);
            if (amount == null) {
                player.sendMessage(ChatColor.RED + "[Skyblock] 양의 숫자로 금액을 입력해주세요.");
                return;
            }
            execute(player,
                    () -> network.bankDeposit(player, amount),
                    result -> {
                        BigDecimal deposited = getDecimal(result.data(), "amount");
                        BigDecimal balance = getDecimal(result.data(), "balance");
                        player.sendMessage(ChatColor.GREEN + "[Skyblock] " + formatMoney(deposited) + "원을 금고에 입금했습니다. 현재 잔액: " + formatMoney(balance) + "원");
                    },
                    "입금에 실패했습니다",
                    true);
            return;
        }

        if (matches(args[1], "출금", "withdraw")) {
            if (args.length < 3) {
                player.sendMessage(ChatColor.RED + "[Skyblock] 출금할 금액을 입력해주세요.");
                return;
            }
            BigDecimal amount = parseAmount(args[2]);
            if (amount == null) {
                player.sendMessage(ChatColor.RED + "[Skyblock] 양의 숫자로 금액을 입력해주세요.");
                return;
            }
            execute(player,
                    () -> network.bankWithdraw(player, amount),
                    result -> {
                        BigDecimal withdrawn = getDecimal(result.data(), "amount");
                        BigDecimal balance = getDecimal(result.data(), "balance");
                        player.sendMessage(ChatColor.GREEN + "[Skyblock] " + formatMoney(withdrawn) + "원을 금고에서 출금했습니다. 현재 잔액: " + formatMoney(balance) + "원");
                    },
                    "출금에 실패했습니다",
                    true);
            return;
        }

        if (matches(args[1], "기록", "history", "logs")) {
            int page = args.length >= 3 ? parsePageArgument(args, 1) : 1;
            execute(player,
                    () -> network.bankHistory(player, page),
                    result -> displayBankHistory(player, BankHistoryPage.from(result.data())),
                    "은행 기록을 불러오지 못했습니다",
                    true);
            return;
        }

        if (matches(args[1], "잠금", "lock")) {
            if (args.length < 3) {
                player.sendMessage(ChatColor.RED + "[Skyblock] /" + baseCommand + " 금고 잠금 [설정|해제]");
                return;
            }
            boolean lock;
            if (matches(args[2], "설정", "set", "enable")) {
                lock = true;
            } else if (matches(args[2], "해제", "unset", "disable")) {
                lock = false;
            } else {
                player.sendMessage(ChatColor.RED + "[Skyblock] /" + baseCommand + " 금고 잠금 [설정|해제]");
                return;
            }
            execute(player,
                    () -> network.bankSetLock(player, lock),
                    result -> {
                        boolean locked = result.data() != null && result.data().has("locked") && result.data().get("locked").getAsBoolean();
                        player.sendMessage(ChatColor.GREEN + "[Skyblock] 금고 잠금을 " + (locked ? "설정" : "해제") + "했습니다.");
                    },
                    lock ? "금고 잠금을 설정하지 못했습니다" : "금고 잠금을 해제하지 못했습니다",
                    true);
            return;
        }

        player.sendMessage(ChatColor.GRAY + "[Skyblock] /" + baseCommand + " 금고 [입금|출금|기록|잠금]");
    }

    private void handleCoop(Player player, String[] args) {
        if (menus != null && (args.length == 1 || matches(args[1], "메뉴", "menu"))) {
            menus.openCoopMenu(player);
            return;
        }

        if (args.length == 1 || matches(args[1], "목록", "list")) {
            execute(player,
                    () -> network.listCoopPlayers(player),
                    result -> displayCoopList(player, result.data()),
                    "알바 목록을 불러오지 못했습니다",
                    true);
            return;
        }

        if (matches(args[1], "추가", "add")) {
            if (args.length < 3) {
                player.sendMessage(ChatColor.RED + "[Skyblock] 고용할 닉네임을 입력해주세요.");
                return;
            }
            String target = args[2];
            execute(player,
                    () -> network.addCoopPlayer(player, target),
                    result -> {
                        String name = playerNameFrom(result.data(), target);
                        player.sendMessage(ChatColor.GREEN + "[Skyblock] " + name + "님을 알바로 고용했습니다.");
                    },
                    "알바 고용에 실패했습니다",
                    true);
            return;
        }

        if (matches(args[1], "삭제", "remove", "해고", "uncoop")) {
            if (args.length < 3) {
                player.sendMessage(ChatColor.RED + "[Skyblock] 해고할 닉네임을 입력해주세요.");
                return;
            }
            String target = args[2];
            execute(player,
                    () -> network.removeCoopPlayer(player, target),
                    result -> {
                        String name = playerNameFrom(result.data(), target);
                        player.sendMessage(ChatColor.GREEN + "[Skyblock] " + name + "님을 알바에서 해고했습니다.");
                    },
                    "알바 해고에 실패했습니다",
                    true);
            return;
        }

        player.sendMessage(ChatColor.GRAY + "[Skyblock] /" + baseCommand + " 알바 [목록|추가|삭제]");
    }

    private void handleBan(Player player, String[] args) {
        if (menus != null && (args.length == 1 || matches(args[1], "메뉴", "menu"))) {
            menus.openBanMenu(player);
            return;
        }

        if (args.length == 1 || matches(args[1], "목록", "list")) {
            execute(player,
                    () -> network.listBannedPlayers(player),
                    result -> displayBanList(player, result.data()),
                    "차단 목록을 불러오지 못했습니다",
                    true);
            return;
        }

        if (matches(args[1], "추가", "add")) {
            if (args.length < 3) {
                player.sendMessage(ChatColor.RED + "[Skyblock] 차단할 닉네임을 입력해주세요.");
                return;
            }
            String target = args[2];
            execute(player,
                    () -> network.addBannedPlayer(player, target),
                    result -> {
                        String name = playerNameFrom(result.data(), target);
                        player.sendMessage(ChatColor.GREEN + "[Skyblock] " + name + "님을 차단했습니다.");
                    },
                    "플레이어를 차단하지 못했습니다",
                    true);
            return;
        }

        if (matches(args[1], "삭제", "remove", "해제", "unban")) {
            if (args.length < 3) {
                player.sendMessage(ChatColor.RED + "[Skyblock] 차단을 해제할 닉네임을 입력해주세요.");
                return;
            }
            String target = args[2];
            execute(player,
                    () -> network.removeBannedPlayer(player, target),
                    result -> {
                        String name = playerNameFrom(result.data(), target);
                        player.sendMessage(ChatColor.GREEN + "[Skyblock] " + name + "님의 차단을 해제했습니다.");
                    },
                    "차단 해제에 실패했습니다",
                    true);
            return;
        }

        player.sendMessage(ChatColor.GRAY + "[Skyblock] /" + baseCommand + " 차단 [목록|추가|삭제]");
    }

    private void requestBankState(Player player) {
        execute(player,
                () -> network.bankState(player),
                result -> displayBankState(player, BankSnapshot.from(result.data())),
                "은행 정보를 불러오지 못했습니다",
                true);
    }

    private void displayBankState(Player player, BankSnapshot snapshot) {
        player.sendMessage(ChatColor.GOLD + "[Skyblock] 금고 정보");
        player.sendMessage(ChatColor.GRAY + " - 잔액: " + ChatColor.YELLOW + formatMoney(snapshot.balance()) + "원");
        if (snapshot.hasLimit()) {
            player.sendMessage(ChatColor.GRAY + " - 한도: " + ChatColor.AQUA + formatMoney(snapshot.limit()) + "원");
        }
        String lockState = snapshot.locked() ? ChatColor.RED + "잠금" : ChatColor.GREEN + "해제";
        player.sendMessage(ChatColor.GRAY + " - 잠금 상태: " + lockState);
        player.sendMessage(ChatColor.GRAY + "사용 가능한 명령어: " + ChatColor.YELLOW + "/" + baseCommand + " 금고 입금 [금액], 출금 [금액], 기록 [페이지], 잠금 [설정|해제]");
        if (menus != null) {
            player.sendMessage(ChatColor.GRAY + "GUI: " + ChatColor.YELLOW + "/" + baseCommand + " 금고 메뉴");
        }
    }

    private void displayBankHistory(Player player, BankHistoryPage history) {
        player.sendMessage(ChatColor.GOLD + "[Skyblock] 금고 거래 기록 " + ChatColor.GRAY + "(" + history.page() + "/" + history.totalPages() + ")");
        if (history.entries().isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "  기록이 존재하지 않습니다.");
        } else {
            for (BankHistoryPage.Entry entry : history.entries()) {
                String actor = entry.playerName() != null ? entry.playerName() : "팜 시스템";
                ChatColor actionColor = bankActionColor(entry.action());
                String action = describeBankAction(entry.action());
                String amount = formatMoney(entry.amount());
                String timestamp = DATE_FORMAT.format(Instant.ofEpochMilli(entry.time()));
                player.sendMessage(ChatColor.GRAY + "  - " + ChatColor.AQUA + actor + ChatColor.GRAY + " | " + actionColor + action + ChatColor.GRAY + " | " + ChatColor.YELLOW + amount + "원" + ChatColor.GRAY + " | " + ChatColor.DARK_GRAY + timestamp);
                if (entry.failureReason() != null && !entry.failureReason().isBlank()) {
                    player.sendMessage(ChatColor.DARK_RED + "    사유: " + entry.failureReason());
                }
            }
        }
        if (history.hasNext()) {
            player.sendMessage(ChatColor.GRAY + "  다음 페이지: " + ChatColor.YELLOW + "/" + baseCommand + " 금고 기록 " + (history.page() + 1));
        }
        if (history.hasPrevious()) {
            player.sendMessage(ChatColor.GRAY + "  이전 페이지: " + ChatColor.YELLOW + "/" + baseCommand + " 금고 기록 " + (history.page() - 1));
        }
    }

    private void displayCoopList(Player player, JsonObject data) {
        JsonArray array = data != null && data.has("players") && data.get("players").isJsonArray()
                ? data.getAsJsonArray("players")
                : new JsonArray();
        List<PlayerSummary> entries = PlayerSummary.from(array);
        int limit = data != null && data.has("limit") && data.get("limit").isJsonPrimitive()
                ? safeGetInt(data.get("limit"))
                : entries.size();
        player.sendMessage(ChatColor.GOLD + "[Skyblock] 알바 목록 " + ChatColor.GRAY + "(" + entries.size() + "/" + limit + ")");
        if (entries.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "  등록된 알바가 없습니다.");
            return;
        }
        for (PlayerSummary entry : entries) {
            player.sendMessage(ChatColor.GRAY + "  - " + ChatColor.AQUA + entry.name() + ChatColor.GRAY + " | " + formatPlayerStatus(entry));
        }
    }

    private void displayBanList(Player player, JsonObject data) {
        JsonArray array = data != null && data.has("players") && data.get("players").isJsonArray()
                ? data.getAsJsonArray("players")
                : new JsonArray();
        List<PlayerSummary> entries = PlayerSummary.from(array);
        player.sendMessage(ChatColor.GOLD + "[Skyblock] 차단 목록 " + ChatColor.GRAY + "(" + entries.size() + "명)");
        if (entries.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "  차단된 플레이어가 없습니다.");
            return;
        }
        for (PlayerSummary entry : entries) {
            player.sendMessage(ChatColor.GRAY + "  - " + ChatColor.AQUA + entry.name() + ChatColor.GRAY + " | " + formatPlayerStatus(entry));
        }
    }

    private String describeBankAction(String action) {
        if (action == null) {
            return "알 수 없음";
        }
        return switch (action) {
            case "DEPOSIT_COMPLETED" -> "입금";
            case "DEPOSIT_FAILED" -> "입금 실패";
            case "WITHDRAW_COMPLETED" -> "출금";
            case "WITHDRAW_FAILED" -> "출금 실패";
            default -> action;
        };
    }

    private ChatColor bankActionColor(String action) {
        if (action == null) {
            return ChatColor.GRAY;
        }
        return switch (action) {
            case "DEPOSIT_COMPLETED", "WITHDRAW_COMPLETED" -> ChatColor.GREEN;
            case "DEPOSIT_FAILED", "WITHDRAW_FAILED" -> ChatColor.RED;
            default -> ChatColor.GRAY;
        };
    }

    private BigDecimal parseAmount(String raw) {
        if (raw == null) {
            return null;
        }
        String normalised = raw.replace(",", "");
        try {
            BigDecimal value = new BigDecimal(normalised);
            return value.signum() > 0 ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String formatMoney(BigDecimal value) {
        BigDecimal safe = value != null ? value : BigDecimal.ZERO;
        if (menus != null) {
            return menus.formatMoney(safe);
        }
        NumberFormat formatter = NumberFormat.getInstance(Locale.KOREA);
        formatter.setGroupingUsed(true);
        formatter.setMaximumFractionDigits(0);
        formatter.setRoundingMode(RoundingMode.DOWN);
        return formatter.format(safe);
    }

    private BigDecimal getDecimal(JsonObject data, String key) {
        if (data == null || key == null || !data.has(key) || data.get(key).isJsonNull()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(data.get(key).getAsString());
        } catch (Exception ex) {
            return BigDecimal.ZERO;
        }
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

    private void handleManage(Player player, String[] args) {
        if (!player.hasPermission("ssb2.command.farm.admin")) {
            messages.send(player, "manage.permission-required");
            return;
        }

        if (args.length == 1 || matches(args[1], "도움말", "help")) {
            displayManageHelp(player);
            return;
        }

        String sub = args[1];
        if (matches(sub, "권한초기화", "resetperm", "resetperms", "resetpermissions")) {
            if (args.length < 3) {
                messages.send(player, "manage.reset-usage", baseCommand);
                return;
            }
            String target = args[2];
            execute(player,
                    () -> network.adminResetPermissions(player, target),
                    result -> {
                        JsonObject data = result.data();
                        String name = getString(data, "targetName", target);
                        String islandId = getString(data, "islandId", "UNKNOWN");
                        messages.send(player, "manage.reset-success", name, islandId);
                    },
                    messages.format("manage.reset-failure-prefix"),
                    true);
            return;
        }

        if (matches(sub, "uuid")) {
            if (args.length < 3) {
                messages.send(player, "manage.uuid-usage", baseCommand);
                return;
            }
            String target = args[2];
            execute(player,
                    () -> network.adminLookupIslandUuid(player, target),
                    result -> {
                        JsonObject data = result.data();
                        String islandId = getString(data, "islandId", "UNKNOWN");
                        String name = getString(data, "playerName", target);
                        messages.send(player, "manage.uuid-success", name, islandId);
                    },
                    messages.format("manage.uuid-failure-prefix"),
                    true);
            return;
        }

        if (matches(sub, "주인", "owner")) {
            if (args.length < 3) {
                messages.send(player, "manage.owner-usage", baseCommand);
                return;
            }
            String islandId = args[2];
            execute(player,
                    () -> network.adminLookupIslandOwner(player, islandId),
                    result -> {
                        JsonObject data = result.data();
                        String ownerName = getString(data, "ownerName", "알 수 없음");
                        String ownerUuid = getString(data, "ownerUuid", "알 수 없음");
                        messages.send(player, "manage.owner-success", ownerName, ownerUuid);
                    },
                    messages.format("manage.owner-failure-prefix"),
                    true);
            return;
        }

        if (matches(sub, "도박장")) {
            if (args.length < 3) {
                messages.send(player, "manage.gambling-usage", baseCommand);
                return;
            }
            String target = args[2];
            execute(player,
                    () -> network.adminToggleGambling(player, target),
                    result -> {
                        JsonObject data = result.data();
                        boolean enabled = data != null && data.has("enabled") && data.get("enabled").getAsBoolean();
                        String name = getString(data, "playerName", target);
                        messages.send(player,
                                enabled ? "manage.gambling-enabled" : "manage.gambling-disabled",
                                name);
                    },
                    messages.format("manage.gambling-failure-prefix"),
                    true);
            return;
        }

        if (matches(sub, "마력보상", "점수보상")) {
            if (menus == null) {
                messages.send(player, "manage.menu-disabled");
                return;
            }
            if (args.length < 3) {
                messages.send(player, "manage.power-usage", baseCommand);
                return;
            }
            int tier = parsePositiveInt(args[2], -1);
            if (!POWER_REWARD_TIERS.contains(tier)) {
                messages.send(player, "manage.power-invalid-tier");
                return;
            }
            menus.openPowerRewardEditor(player, tier);
            return;
        }

        if (matches(sub, "순위보상")) {
            if (menus == null) {
                messages.send(player, "manage.menu-disabled");
                return;
            }
            if (args.length < 3) {
                messages.send(player, "manage.top-usage", baseCommand);
                return;
            }
            int rank = parsePositiveInt(args[2], -1);
            if (rank <= 0) {
                messages.send(player, "manage.top-invalid-rank");
                return;
            }
            menus.openTopRewardEditor(player, rank);
            return;
        }

        if (matches(sub, "순위받기")) {
            if (menus == null) {
                messages.send(player, "manage.menu-disabled");
                return;
            }
            if (args.length < 3) {
                messages.send(player, "manage.top-usage", baseCommand);
                return;
            }
            int rank = parsePositiveInt(args[2], -1);
            if (rank <= 0) {
                messages.send(player, "manage.top-invalid-rank");
                return;
            }
            execute(player,
                    () -> network.adminGiveTopRewards(player, rank),
                    result -> {
                        JsonArray array = result.data() != null && result.data().has("items")
                                ? result.data().getAsJsonArray("items")
                                : new JsonArray();
                        List<ItemStack> items = menus != null ? menus.decodeRewardItems(array) : List.of();
                        if (items.isEmpty()) {
                            messages.send(player, "manage.top-give-empty");
                            return;
                        }
                        List<ItemStack> clones = items.stream().map(ItemStack::clone).collect(Collectors.toList());
                        ItemStack[] contents = clones.toArray(new ItemStack[0]);
                        int totalAmount = clones.stream().mapToInt(ItemStack::getAmount).sum();
                        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(contents);
                        int droppedAmount = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
                        leftovers.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
                        messages.send(player, "manage.top-give-success", rank, totalAmount, droppedAmount);
                    },
                    messages.format("manage.top-give-failure-prefix"),
                    true);
            return;
        }

        messages.send(player, "manage.unknown-subcommand", sub);
        displayManageHelp(player);
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

    private void displayManageHelp(Player player) {
        for (String line : messages.list("manage.help-lines", baseCommand)) {
            player.sendMessage(line);
        }
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
            String server = getString(member, "server", "");
            String status = online ? ChatColor.AQUA + "온라인" : ChatColor.DARK_GRAY + "오프라인";
            if (server != null && !server.isBlank()) {
                status += ChatColor.GRAY + " @" + ChatColor.YELLOW + server;
            }
            lines.add(ChatColor.GRAY + "- " + ChatColor.GREEN + name + ChatColor.GRAY + " (" + translateRole(role) + ", " + status + ChatColor.GRAY + ")");
        }
        displayPagedList(player, lines, page,
                messages.format("members.header"),
                messages.format("members.empty"),
                messages.format("members.footer", baseCommand));
    }

    private void displayRolePermissions(Player player, RolePermissionSnapshot snapshot, String roleFilter) {
        if (snapshot.roles().isEmpty()) {
            messages.send(player, "permissions.failure-prefix");
            return;
        }
        if (roleFilter == null || roleFilter.isBlank()) {
            for (String line : messages.list("permissions.list-header")) {
                player.sendMessage(line);
            }
            if (!snapshot.canManage()) {
                player.sendMessage(ChatColor.RED + "[Skyblock] 권한을 변경할 수 있는 권한이 없어 조회만 가능합니다.");
            }
            snapshot.roles().forEach(role ->
                    player.sendMessage(messages.format("permissions.list-line",
                            role.displayName(),
                            role.enabledCount(),
                            role.totalPrivileges())));
            player.sendMessage(messages.format("permissions.list-footer", baseCommand));
            return;
        }

        RolePermissionSnapshot.Role role = snapshot.findRole(roleFilter).orElse(null);
        if (role == null) {
            messages.send(player, "permissions.invalid-role", roleFilter);
            return;
        }
        player.sendMessage(messages.format("permissions.list-role-header",
                role.displayName(),
                role.enabledCount(),
                role.totalPrivileges()));
        for (RolePermissionSnapshot.Privilege privilege : role.privileges()) {
            String state = privilege.enabled() ? ChatColor.GREEN + "허용" : ChatColor.RED + "차단";
            player.sendMessage(messages.format("permissions.list-role-line",
                    friendlyPrivilegeName(privilege.name()),
                    state));
        }
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
            int total = Math.max(0, moonlight) + Math.max(0, farmPoints);
            player.sendMessage(ChatColor.YELLOW + "- 순위 " + minRank + (minRank == maxRank ? "" : "~" + maxRank) + ChatColor.GRAY + " : " + ChatColor.GOLD + title);
            player.sendMessage(ChatColor.GRAY + "  · 팜 포인트 " + formatNumber(total));
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

    private void displayRules(Player player, JsonObject data) {
        JsonArray rules = data != null && data.has("rules") && data.get("rules").isJsonArray()
                ? data.getAsJsonArray("rules")
                : new JsonArray();
        String islandName = getString(data, "islandName", player.getName());
        int max = data != null && data.has("max") ? data.get("max").getAsInt() : 5;
        int count = rules.size();
        player.sendMessage(messages.format("rules.header", islandName, count, max));
        if (count == 0) {
            messages.send(player, "rules.empty");
        } else {
            for (int i = 0; i < count; i++) {
                JsonElement element = rules.get(i);
                String ruleText;
                if (element == null || element.isJsonNull()) {
                    ruleText = "";
                } else {
                    try {
                        ruleText = element.getAsString();
                    } catch (Exception ex) {
                        ruleText = element.toString();
                    }
                }
                player.sendMessage(messages.format("rules.line", i + 1, ruleText));
            }
        }
        boolean editable = data != null && data.has("editable") && data.get("editable").getAsBoolean();
        if (editable) {
            messages.send(player, "rules.edit-hint", max, baseCommand);
        }
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

    private Boolean parsePermissionState(String raw) {
        if (raw == null) {
            return null;
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "허용", "allow", "true", "on", "enable", "enabled" -> Boolean.TRUE;
            case "차단", "deny", "false", "off", "disable", "disabled" -> Boolean.FALSE;
            default -> null;
        };
    }

    private String friendlyPrivilegeName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "알 수 없음";
        }
        String cleaned = raw.replace('-', '_').replace(' ', '_');
        String[] parts = cleaned.toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
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
            case "moonlight" -> "팜 포인트";
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

    private String playerNameFrom(JsonObject data, String fallback) {
        if (data == null || !data.has("player") || !data.get("player").isJsonObject()) {
            return fallback;
        }
        return getString(data.getAsJsonObject("player"), "name", fallback);
    }

    private int safeGetInt(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return 0;
        }
        try {
            return element.getAsInt();
        } catch (Exception ex) {
            try {
                return Integer.parseInt(element.getAsString());
            } catch (Exception ignored) {
                return 0;
            }
        }
    }

    private long safeGetLong(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return 0L;
        }
        try {
            return element.getAsLong();
        } catch (Exception ex) {
            try {
                return Long.parseLong(element.getAsString());
            } catch (Exception ignored) {
                return 0L;
            }
        }
    }

    private String formatPlayerStatus(PlayerSummary entry) {
        String status = entry.online() ? ChatColor.GREEN + "온라인" : ChatColor.RED + "오프라인";
        if (entry.server() != null && !entry.server().isBlank()) {
            status += ChatColor.GRAY + " @" + ChatColor.YELLOW + entry.server();
        }
        return status;
    }

    private String formatNumber(long value) {
        return String.format(Locale.KOREA, "%,d", value);
    }

    private String formatBigNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            return "0";
        }
        try {
            BigInteger value = new BigInteger(raw);
            NumberFormat formatter = NumberFormat.getInstance(Locale.KOREA);
            formatter.setGroupingUsed(true);
            return formatter.format(value);
        } catch (NumberFormatException ex) {
            return raw;
        }
    }

    private String joinArgs(String[] args, int start) {
        if (args == null || args.length <= start) {
            return "";
        }
        return String.join(" ", Arrays.copyOfRange(args, start, args.length));
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
            return filter(Arrays.asList("메뉴", "도움말", "퀘스트", "초대", "초대목록", "수락", "거절", "구성원", "채팅", "정보", "포인트", "호퍼", "평가", "홈", "sethome", "워프", "규칙", "순위", "히스토리", "보상", "상점", "금고", "알바", "차단", "권한", "경계", "해체", "관리자"), args[0]);
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

        if (matches(sub, "채팅", "chat")) {
            if (args.length == 2) {
                return Collections.singletonList("<메시지>");
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

        if (matches(sub, "홈", "home", "homes", "gha")) {
            if (args.length == 2) {
                return filter(Arrays.asList("설정"), args[1]);
            }
            return Collections.emptyList();
        }

        if (matches(sub, "워프", "warp", "warps", "dnjvm")) {
            if (args.length == 2) {
                return filter(Arrays.asList("메뉴", "페이지"), args[1]);
            }
            if (args.length == 3 && matches(args[1], "페이지")) {
                return Collections.singletonList("<번호>");
            }
            return Collections.emptyList();
        }

        if (matches(sub, "규칙", "rule", "rules", "rbclr")) {
            if (args.length == 2) {
                return filter(Arrays.asList("목록", "추가", "삭제"), args[1]);
            }
            if (matches(args[1], "추가", "add")) {
                return Collections.singletonList("<내용>");
            }
            if (matches(args[1], "삭제", "remove") && args.length == 3) {
                return Collections.singletonList("<번호>");
            }
            return Collections.emptyList();
        }

        if (matches(sub, "알바", "coop")) {
            if (args.length == 2) {
                return filter(Arrays.asList("목록", "추가", "삭제", "메뉴"), args[1]);
            }
            if (matches(args[1], "추가", "add") || matches(args[1], "삭제", "remove", "해고", "uncoop")) {
                return Collections.singletonList("<닉네임>");
            }
            return Collections.emptyList();
        }

        if (matches(sub, "차단", "ban")) {
            if (args.length == 2) {
                return filter(Arrays.asList("목록", "추가", "삭제", "메뉴"), args[1]);
            }
            if (matches(args[1], "추가", "add") || matches(args[1], "삭제", "remove", "해제", "unban")) {
                return Collections.singletonList("<닉네임>");
            }
            return Collections.emptyList();
        }

        if (matches(sub, "평가", "vudrk", "rating")) {
            if (args.length == 2) {
                return filter(Arrays.asList("0", "1", "2", "3", "4", "5"), args[1]);
            }
            return Collections.emptyList();
        }

        if (matches(sub, "권한")) {
            if (args.length == 2) {
                return filter(Arrays.asList("메뉴", "목록", "설정"), args[1]);
            }
            if (matches(args[1], "목록")) {
                if (args.length == 3) {
                    return Collections.singletonList("<역할>");
                }
                return Collections.emptyList();
            }
            if (matches(args[1], "설정")) {
                if (args.length == 3) {
                    return Collections.singletonList("<역할>");
                }
                if (args.length == 4) {
                    return Collections.singletonList("<권한>");
                }
                if (args.length == 5) {
                    return filter(Arrays.asList("허용", "차단"), args[4]);
                }
            }
            return Collections.emptyList();
        }

        if (matches(sub, "순위")) {
            if (args.length == 2) {
                return filter(Arrays.asList("메뉴", "기여도", "스냅샷", "페이지"), args[1]);
            }
            if (args.length == 3 && matches(args[1], "기여도")) {
                return Collections.singletonList("<팜ID>");
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
