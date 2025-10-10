package wiki.creeper.superiorskyblockIntegeration.gateway;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import java.time.Duration;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import wiki.creeper.superiorskyblockIntegeration.api.PlayerMetadataService;

import wiki.creeper.superiorskyblockIntegeration.common.Operations;
import wiki.creeper.superiorskyblockIntegeration.common.errors.ErrorCode;
import wiki.creeper.superiorskyblockIntegeration.common.quest.QuestType;
import wiki.creeper.superiorskyblockIntegeration.config.PluginConfig;
import wiki.creeper.superiorskyblockIntegeration.common.model.PlayerProfile;
import wiki.creeper.superiorskyblockIntegeration.gateway.cache.PlayerIslandCache;
import wiki.creeper.superiorskyblockIntegeration.gateway.data.GatewayDataService;
import wiki.creeper.superiorskyblockIntegeration.gateway.idempotency.IdempotencyKeyBuilder;
import wiki.creeper.superiorskyblockIntegeration.gateway.idempotency.IdempotencyService;
import wiki.creeper.superiorskyblockIntegeration.gateway.bridge.SuperiorSkyblockBridge;
import wiki.creeper.superiorskyblockIntegeration.gateway.data.GatewayQuestService;
import wiki.creeper.superiorskyblockIntegeration.gateway.data.GatewayRankingService;
import wiki.creeper.superiorskyblockIntegeration.gateway.errors.GatewayException;
import wiki.creeper.superiorskyblockIntegeration.redis.MessageSecurity;
import wiki.creeper.superiorskyblockIntegeration.redis.RedisChannels;
import wiki.creeper.superiorskyblockIntegeration.redis.RedisManager;
import wiki.creeper.superiorskyblockIntegeration.redis.RedisMessage;
import wiki.creeper.superiorskyblockIntegeration.redis.RedisCodec;

/**
 * Routes incoming Redis requests to the appropriate gateway handler.
 */
final class GatewayRequestRouter {

    private final JavaPlugin plugin;
    private final RedisManager redisManager;
    private final RedisChannels channels;
    private final MessageSecurity security;
    private final GatewayEventPublisher events;
    private final IdempotencyService idempotency;
    private final IdempotencyKeyBuilder idempotencyKeys;
    private final PluginConfig config;
    private final SuperiorSkyblockBridge bridge;
    private final PlayerIslandCache islandCache;
    private final GatewayDataService dataService;
    private final GatewayRankingService rankingService;
    private final GatewayQuestService questService;
    private final PlayerMetadataService metadataService;
    private final int compressionThreshold;
    private final KickReasonRegistry kickReasons;
    private static final String POWER_REWARD_NAMESPACE = "rewards:power";
    private static final String TOP_REWARD_NAMESPACE = "rewards:top";
    private static final String ISLAND_RULE_NAMESPACE = "rules:island";
    private static final String BANK_LOCK_NAMESPACE = "bank:lock";
    private static final int MAX_REWARD_SLOTS = 27;
    private static final int MAX_ISLAND_RULES = 5;

    GatewayRequestRouter(JavaPlugin plugin,
                         RedisManager redisManager,
                         RedisChannels channels,
                         MessageSecurity security,
                         IdempotencyService idempotency,
                         PluginConfig config,
                         SuperiorSkyblockBridge bridge,
                         GatewayEventPublisher events,
                         PlayerIslandCache islandCache,
                         GatewayDataService dataService,
                         GatewayRankingService rankingService,
                         GatewayQuestService questService,
                         PlayerMetadataService metadataService,
                         KickReasonRegistry kickReasons) {
        this.plugin = plugin;
        this.redisManager = redisManager;
        this.channels = channels;
        this.security = security;
        this.events = events;
        this.idempotency = idempotency;
        this.idempotencyKeys = new IdempotencyKeyBuilder(config.channels().prefix() + ":idemp");
        this.config = config;
        this.bridge = bridge;
        this.islandCache = islandCache;
        this.dataService = dataService;
        this.rankingService = rankingService;
        this.questService = questService;
        this.metadataService = metadataService;
        this.compressionThreshold = config.redis().messageCompressionThreshold();
        this.kickReasons = kickReasons;
    }

    void handle(String channel, RedisMessage request) {
        Optional<Operations> operation = Operations.from(request.op());
        if (operation.isEmpty()) {
            String channelOperation = extractOperationFromChannel(channel);
            if (channelOperation != null) {
                operation = Operations.from(channelOperation);
            }
        }
        if (operation.isEmpty()) {
            plugin.getLogger().warning("Unknown operation received on " + channel + ": " + request.op());
            publishError(request, "UNKNOWN_OPERATION", "Unsupported operation: " + request.op(), false);
            return;
        }

        GatewayResponse response = dispatch(operation.get(), request);
        publishResponse(request, response);
    }

    public GatewayResponse executeLocally(Operations operation, RedisMessage request) {
        Objects.requireNonNull(operation, "operation");
        return dispatch(operation, request);
    }

    private GatewayResponse dispatch(Operations operation, RedisMessage request) {
        return switch (operation) {
            case INVITE_CREATE -> handleInviteCreate(request);
            case INVITE_ACCEPT -> handleInviteAccept(request);
            case INVITE_DENY -> handleInviteDeny(request);
            case INVITES_PENDING -> handleInvitesPending(request);
            case ISLAND_GET -> handleIslandGet(request);
            case MEMBERS_LIST -> handleMembersList(request);
            case MEMBERS_KICK -> handleMemberKick(request);
            case DATA_PUT -> handleDataPut(request);
            case DATA_GET -> handleDataGet(request);
            case DATA_DELETE -> handleDataDelete(request);
            case PLAYER_PROFILE_REGISTER -> handleProfileRegister(request);
            case PLAYER_PROFILE_LOOKUP -> handleProfileLookup(request);
            case PLAYER_ISLAND_LOOKUP -> handlePlayerIslandLookup(request);
            case QUEST_STATE -> handleQuestState(request);
            case QUEST_ASSIGN -> handleQuestAssign(request);
            case QUEST_PROGRESS -> handleQuestProgress(request);
            case FARM_RANKING_TOP -> handleFarmRankingTop(request);
            case FARM_RANKING_MEMBERS -> handleFarmRankingMembers(request);
            case FARM_RANKING_INCREMENT -> handleFarmRankingIncrement(request);
            case FARM_RANKING_SNAPSHOT -> handleFarmRankingSnapshot(request);
            case FARM_HISTORY_LIST -> handleFarmHistoryList(request);
            case FARM_HISTORY_DETAIL -> handleFarmHistoryDetail(request);
            case FARM_BORDER_STATE -> handleFarmBorderState(request);
            case FARM_BORDER_TOGGLE -> handleFarmBorderToggle(request);
            case FARM_BORDER_COLOR -> handleFarmBorderColor(request);
            case FARM_REWARD_TABLE -> handleFarmRewardTable(request);
            case FARM_SHOP_TABLE -> handleFarmShopTable(request);
            case FARM_POINTS_INFO -> handleFarmPointsInfo(request);
            case FARM_HOPPER_INFO -> handleFarmHopperInfo(request);
            case FARM_RATING_UPDATE -> handleFarmRatingUpdate(request);
            case FARM_WARP_HOME_LIST -> handleFarmWarpHomeList(request);
            case FARM_WARP_HOME_SET -> handleFarmWarpHomeSet(request);
            case FARM_WARP_HOME_DELETE -> handleFarmWarpHomeDelete(request);
            case FARM_WARP_HOME_RENAME -> handleFarmWarpHomeRename(request);
            case FARM_WARP_HOME_TOGGLE -> handleFarmWarpHomeToggle(request);
            case FARM_WARP_PLAYER_LIST -> handleFarmWarpPlayerList(request);
            case FARM_WARP_GLOBAL_LIST -> handleFarmWarpGlobalList(request);
            case FARM_WARP_VISIT -> handleFarmWarpVisit(request);
            case FARM_RULE_LIST -> handleFarmRuleList(request);
            case FARM_RULE_ADD -> handleFarmRuleAdd(request);
            case FARM_RULE_REMOVE -> handleFarmRuleRemove(request);
            case FARM_COOP_LIST -> handleFarmCoopList(request);
            case FARM_COOP_ADD -> handleFarmCoopAdd(request);
            case FARM_COOP_REMOVE -> handleFarmCoopRemove(request);
            case FARM_BAN_LIST -> handleFarmBanList(request);
            case FARM_BAN_ADD -> handleFarmBanAdd(request);
            case FARM_BAN_REMOVE -> handleFarmBanRemove(request);
            case FARM_CHAT_SEND -> handleFarmChatSend(request);
            case ISLAND_DISBAND -> handleIslandDisband(request);
            case ROLE_PERMISSIONS_LIST -> handleRolePermissionsList(request);
            case ROLE_PERMISSIONS_UPDATE -> handleRolePermissionsUpdate(request);
            case BANK_STATE -> handleBankState(request);
            case BANK_DEPOSIT -> handleBankDeposit(request);
            case BANK_WITHDRAW -> handleBankWithdraw(request);
            case BANK_HISTORY -> handleBankHistory(request);
            case BANK_LOCK_SET -> handleBankLockSet(request);
            case ADMIN_RESET_PERMISSIONS -> handleAdminResetPermissions(request);
            case ADMIN_LOOKUP_ISLAND_UUID -> handleAdminLookupIslandUuid(request);
            case ADMIN_LOOKUP_ISLAND_OWNER -> handleAdminLookupIslandOwner(request);
            case ADMIN_TOGGLE_GAMBLING -> handleAdminToggleGambling(request);
            case ADMIN_LOAD_POWER_REWARD -> handleAdminLoadPowerRewards(request);
            case ADMIN_SAVE_POWER_REWARD -> handleAdminSavePowerRewards(request);
            case ADMIN_LOAD_TOP_REWARD -> handleAdminLoadTopRewards(request);
            case ADMIN_SAVE_TOP_REWARD -> handleAdminSaveTopRewards(request);
            case ADMIN_GIVE_TOP_REWARD -> handleAdminGiveTopRewards(request);
        };
    }

    private String extractOperationFromChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return null;
        }
        int marker = channel.indexOf(".req.");
        if (marker < 0) {
            return null;
        }
        int start = marker + 5; // skip past ".req."
        if (start >= channel.length()) {
            return null;
        }
        return channel.substring(start).trim();
    }

    private GatewayResponse handleInviteCreate(RedisMessage request) {
        JsonObject payload = request.data();
        String targetRaw = readString(payload, "target");
        if (targetRaw == null || targetRaw.isBlank()) {
            return GatewayResponse.error(ErrorCode.BAD_REQUEST.code(), "target must be provided", false);
        }
        UUID actorUuid = parseUuid(request.actor(), "actor");

        String key = idempotencyKeys.forInviteCreate(request.actor(), payload);
        return executeIdempotent(key, () -> execute("invite.create", () ->
                bridge.createInvite(actorUuid, targetRaw, payload.deepCopy())), response -> {
            if (response.ok()) {
                UUID targetUuid = extractUuid(response.data(), "targetUuid");
                if (targetUuid != null) {
                    events.publishInviteRefresh(targetUuid);
                }
            }
        });
    }

    private GatewayResponse handleInviteAccept(RedisMessage request) {
        UUID actorUuid = parseUuid(request.actor(), "actor");
        JsonObject payload = request.data();
        String inviteId = readString(payload, "inviteId");
        String key = idempotencyKeys.forInviteAccept(request.actor());
        return executeIdempotent(key, () -> execute("invite.accept", () ->
                bridge.acceptInvite(actorUuid, inviteId, payload.deepCopy())), response -> {
            if (response.ok()) {
                events.publishMemberAdded(actorUuid, response.data());
                updateMembershipCache(actorUuid, response.data());
                requestTeleport(response.data());
                UUID inviterUuid = extractUuid(response.data(), "inviterUuid");
                if (inviterUuid != null) {
                    events.publishInviteResult("accept", actorUuid, inviterUuid, response.data());
                    events.publishInviteRefresh(inviterUuid);
                }
                events.publishInviteRefresh(actorUuid);
            }
        });
    }

    private GatewayResponse handleInviteDeny(RedisMessage request) {
        UUID actorUuid = parseUuid(request.actor(), "actor");
        JsonObject payload = request.data();
        String inviteId = readString(payload, "inviteId");
        String key = idempotencyKeys.forInviteDeny(request.actor());
        return executeIdempotent(key, () -> execute("invite.deny", () ->
                bridge.denyInvite(actorUuid, inviteId, payload.deepCopy())), response -> {
            if (response.ok()) {
                events.publishInviteRevoked(actorUuid, response.data());
                islandCache.removePlayer(actorUuid);
                removeMetadata(actorUuid, "island.uuid");
                UUID inviterUuid = extractUuid(response.data(), "inviterUuid");
                if (inviterUuid != null) {
                    events.publishInviteResult("reject", actorUuid, inviterUuid, response.data());
                    events.publishInviteRefresh(inviterUuid);
                }
                events.publishInviteRefresh(actorUuid);
            }
        });
    }

    private GatewayResponse handleInvitesPending(RedisMessage request) {
        UUID actorUuid = parseUuid(request.actor(), "actor");
        JsonObject payload = request.data();
        return execute("invites.pending", () -> bridge.listPendingInvites(actorUuid, payload.deepCopy()));
    }

    private GatewayResponse handleIslandGet(RedisMessage request) {
        UUID actorUuid = request.actor() != null ? parseUuid(request.actor(), "actor") : null;
        JsonObject payload = request.data();
        Optional<String> owner = Optional.ofNullable(readString(payload, "owner"));
        int timeoutMs = Math.max(3000, config.gateway().superiorSkyblock().apiHookTimeoutMs());
        return execute("island.get",
                () -> bridge.getIslandInfo(actorUuid, owner, payload.deepCopy()),
                timeoutMs);
    }

    private GatewayResponse handleMembersList(RedisMessage request) {
        UUID actorUuid = request.actor() != null ? parseUuid(request.actor(), "actor") : null;
        JsonObject payload = request.data();
        Optional<String> islandId = Optional.ofNullable(readString(payload, "islandId"));
        int timeoutMs = Math.max(3000, config.gateway().superiorSkyblock().apiHookTimeoutMs());
        return execute("members.list",
                () -> bridge.listMembers(actorUuid, islandId, payload.deepCopy()),
                timeoutMs);
    }

    private GatewayResponse handleMemberKick(RedisMessage request) {
        UUID actorUuid = parseUuid(request.actor(), "actor");
        JsonObject payload = request.data();
        String targetRaw = readString(payload, "target");
        if (targetRaw == null || targetRaw.isBlank()) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "target is required");
        }
        UUID targetUuid = parseUuid(targetRaw, "target");
        String reason = readString(payload, "reason");
        kickReasons.register(actorUuid, targetUuid, reason);
        putMetadata(actorUuid, "island.kick.target", targetUuid.toString());
        if (reason != null && !reason.isBlank()) {
            putMetadata(actorUuid, "island.kick.reason", reason);
        } else {
            removeMetadata(actorUuid, "island.kick.reason");
        }
        try {
            GatewayResponse response = execute("members.kick",
                    () -> bridge.kickMember(actorUuid, targetUuid, reason, payload.deepCopy()));
            if (!response.ok()) {
                kickReasons.invalidate(actorUuid, targetUuid);
            }
            return response;
        } catch (RuntimeException ex) {
            kickReasons.invalidate(actorUuid, targetUuid);
            throw ex;
        }
    }

    private GatewayResponse handleDataPut(RedisMessage request) {
        JsonObject payload = request.data();
        String namespace = readString(payload, "namespace");
        String key = readString(payload, "key");
        String value = readString(payload, "value");
        long ttl = payload.has("ttl") ? payload.get("ttl").getAsLong() : 0L;
        if (namespace == null || key == null || value == null) {
            return GatewayResponse.error(ErrorCode.BAD_REQUEST.code(), "namespace, key and value are required", false);
        }
        dataService.setData(namespace, key, value, ttl > 0 ? Duration.ofSeconds(ttl) : null);
        JsonObject data = new JsonObject();
        data.addProperty("stored", true);
        return GatewayResponse.ok(data);
    }

    private GatewayResponse handleDataGet(RedisMessage request) {
        JsonObject payload = request.data();
        String namespace = readString(payload, "namespace");
        String key = readString(payload, "key");
        if (namespace == null || key == null) {
            return GatewayResponse.error(ErrorCode.BAD_REQUEST.code(), "namespace and key are required", false);
        }
        JsonObject data = new JsonObject();
        dataService.getData(namespace, key).ifPresent(value -> data.addProperty("value", value));
        return GatewayResponse.ok(data);
    }

    private GatewayResponse handleDataDelete(RedisMessage request) {
        JsonObject payload = request.data();
        String namespace = readString(payload, "namespace");
        String key = readString(payload, "key");
        if (namespace == null || key == null) {
            return GatewayResponse.error(ErrorCode.BAD_REQUEST.code(), "namespace and key are required", false);
        }
        dataService.deleteData(namespace, key);
        JsonObject data = new JsonObject();
        data.addProperty("deleted", true);
        return GatewayResponse.ok(data);
    }

    private GatewayResponse handleRolePermissionsList(RedisMessage request) {
        UUID actorUuid = parseUuid(request.actor(), "actor");
        return execute("roles.permissions.list", () -> bridge.listRolePermissions(actorUuid));
    }

    private GatewayResponse handleRolePermissionsUpdate(RedisMessage request) {
        UUID actorUuid = parseUuid(request.actor(), "actor");
        JsonObject payload = request.data();
        String roleName = readString(payload, "role");
        String privilegeName = readString(payload, "privilege");
        if (roleName == null || privilegeName == null || !payload.has("enabled")) {
            return GatewayResponse.error(ErrorCode.BAD_REQUEST.code(), "role, privilege and enabled are required", false);
        }
        boolean enabled;
        try {
            enabled = payload.get("enabled").getAsBoolean();
        } catch (Exception ex) {
            return GatewayResponse.error(ErrorCode.BAD_REQUEST.code(), "enabled must be a boolean", false);
        }
        return execute("roles.permissions.update", () ->
                bridge.updateRolePermission(actorUuid, roleName, privilegeName, enabled));
    }

    private GatewayResponse handleBankState(RedisMessage request) {
        UUID actorUuid = parseUuid(request.actor(), "actor");
        GatewayResponse response = execute("bank.state", () -> bridge.bankState(actorUuid));
        if (response.ok()) {
            JsonObject data = response.data();
            if (data != null && data.has("islandId") && !data.get("islandId").isJsonNull()) {
                String islandId = data.get("islandId").getAsString();
                boolean locked = dataService.getData(BANK_LOCK_NAMESPACE, islandId).isPresent();
                data.addProperty("locked", locked);
                boolean canLock = false;
                try {
                    UUID islandUuid = UUID.fromString(islandId);
                    SuperiorSkyblockBridge.IslandDetails details = bridge.describeIsland(islandUuid);
                    canLock = details != null && details.ownerUuid() != null && details.ownerUuid().equals(actorUuid);
                } catch (IllegalArgumentException ignored) {
                    canLock = false;
                }
                data.addProperty("canLock", canLock);
            }
        }
        return response;
    }

    private GatewayResponse handleBankDeposit(RedisMessage request) {
        UUID actorUuid = parseUuid(request.actor(), "actor");
        JsonObject payload = request.data();
        BigDecimal amount = readDecimal(payload, "amount");
        return execute("bank.deposit", () -> bridge.bankDeposit(actorUuid, amount));
    }

    private GatewayResponse handleBankWithdraw(RedisMessage request) {
        UUID actorUuid = parseUuid(request.actor(), "actor");
        JsonObject payload = request.data();
        BigDecimal amount = readDecimal(payload, "amount");
        UUID islandUuid = bridge.islandIdForPlayer(actorUuid)
                .orElseThrow(() -> new GatewayException(ErrorCode.NOT_FOUND, "Island not found"));
        if (isBankLocked(islandUuid)) {
            throw new GatewayException(ErrorCode.CONFLICT, "Bank withdraw is currently locked");
        }
        return execute("bank.withdraw", () -> bridge.bankWithdraw(actorUuid, amount));
    }

    private GatewayResponse handleBankHistory(RedisMessage request) {
        UUID actorUuid = parseUuid(request.actor(), "actor");
        JsonObject payload = request.data();
        int page = payload != null && payload.has("page")
                ? Math.max(1, readInt(payload, "page"))
                : 1;
        int pageSize = payload != null && payload.has("pageSize")
                ? Math.max(1, readInt(payload, "pageSize"))
                : 10;
        return execute("bank.history", () -> bridge.bankHistory(actorUuid, page, pageSize));
    }

    private GatewayResponse handleBankLockSet(RedisMessage request) {
        UUID actorUuid = parseUuid(request.actor(), "actor");
        JsonObject payload = request.data();
        boolean locked = readBoolean(payload, "locked");
        UUID islandUuid = bridge.islandIdForPlayer(actorUuid)
                .orElseThrow(() -> new GatewayException(ErrorCode.NOT_FOUND, "Island not found"));
        SuperiorSkyblockBridge.IslandDetails details = bridge.describeIsland(islandUuid);
        if (details == null) {
            throw new GatewayException(ErrorCode.NOT_FOUND, "Island not found");
        }
        UUID ownerUuid = details.ownerUuid();
        if (ownerUuid == null || !ownerUuid.equals(actorUuid)) {
            throw new GatewayException(ErrorCode.FORBIDDEN, "Only the island owner can change bank lock state");
        }

        String key = islandUuid.toString();
        if (locked) {
            dataService.setData(BANK_LOCK_NAMESPACE, key, Long.toString(System.currentTimeMillis()), null);
        } else {
            dataService.deleteData(BANK_LOCK_NAMESPACE, key);
        }

        String actorName = bridge.lookupPlayerName(actorUuid.toString()).orElse(actorUuid.toString());
        String message = "&a&l|&f " + actorName + "님이 금고 잠금을 " + (locked ? "설정했습니다." : "해제했습니다.");
        bridge.broadcastIslandMessage(islandUuid, List.of(message));

        JsonObject data = new JsonObject();
        data.addProperty("islandId", key);
        data.addProperty("locked", locked);
        data.addProperty("actor", actorUuid.toString());
        return GatewayResponse.ok(data);
    }

    private GatewayResponse handleAdminResetPermissions(RedisMessage request) {
        UUID actorUuid = parseUuid(request.actor(), "actor");
        JsonObject payload = request.data();
        String target = readString(payload, "target");
        if (target == null || target.isBlank()) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "target is required");
        }
        return execute("admin.reset.permissions", () -> bridge.adminResetPermissions(actorUuid, target));
    }

    private GatewayResponse handleAdminLookupIslandUuid(RedisMessage request) {
        parseUuid(request.actor(), "actor");
        JsonObject payload = request.data();
        String target = readString(payload, "target");
        if (target == null || target.isBlank()) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "target is required");
        }
        return execute("admin.lookup.island.uuid", () -> bridge.adminLookupIslandUuid(target));
    }

    private GatewayResponse handleAdminLookupIslandOwner(RedisMessage request) {
        parseUuid(request.actor(), "actor");
        JsonObject payload = request.data();
        String islandId = readString(payload, "islandId");
        if (islandId == null || islandId.isBlank()) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "islandId is required");
        }
        return execute("admin.lookup.island.owner", () -> bridge.adminLookupIslandOwner(islandId));
    }

    private GatewayResponse handleAdminToggleGambling(RedisMessage request) {
        UUID actorUuid = parseUuid(request.actor(), "actor");
        JsonObject payload = request.data();
        String target = readString(payload, "target");
        if (target == null || target.isBlank()) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "target is required");
        }

        GatewayResponse lookup = bridge.adminLookupIslandUuid(target);
        if (!lookup.ok()) {
            return lookup;
        }
        JsonObject info = lookup.data();
        String islandId = readString(info, "islandId");
        if (islandId == null || islandId.isBlank()) {
            throw new GatewayException(ErrorCode.ISLAND_NOT_FOUND, "Island not found for target");
        }

        String namespace = "island:gambling";
        Optional<String> current = dataService.getData(namespace, islandId);
        boolean enabled = current.isEmpty();
        if (enabled) {
            dataService.setData(namespace, islandId, "true", null);
        } else {
            dataService.deleteData(namespace, islandId);
        }

        JsonObject data = new JsonObject();
        data.addProperty("islandId", islandId);
        data.addProperty("enabled", enabled);
        if (info.has("playerName") && !info.get("playerName").isJsonNull()) {
            data.addProperty("playerName", info.get("playerName").getAsString());
        }
        if (actorUuid != null) {
            data.addProperty("actorUuid", actorUuid.toString());
        }
        return GatewayResponse.ok(data);
    }

    private GatewayResponse handleAdminLoadPowerRewards(RedisMessage request) {
        parseUuid(request.actor(), "actor");
        JsonObject payload = request.data();
        int tier = readInt(payload, "tier");
        if (tier <= 0) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "tier must be positive");
        }
        JsonArray stored = loadRewardArray(POWER_REWARD_NAMESPACE, String.valueOf(tier));
        List<ItemStack> items = deserializeItemsLenient(stored);
        JsonArray normalized = serializeItems(items);
        JsonObject data = new JsonObject();
        data.addProperty("tier", tier);
        data.add("items", normalized);
        return GatewayResponse.ok(data);
    }

    private GatewayResponse handleAdminSavePowerRewards(RedisMessage request) {
        parseUuid(request.actor(), "actor");
        JsonObject payload = request.data();
        int tier = readInt(payload, "tier");
        if (tier <= 0) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "tier must be positive");
        }
        JsonArray itemsArray = readArray(payload, "items");
        List<ItemStack> items = deserializeItemsStrict(itemsArray);
        JsonArray normalized = serializeItems(items);
        String key = String.valueOf(tier);
        if (normalized.size() == 0) {
            dataService.deleteData(POWER_REWARD_NAMESPACE, key);
        } else {
            dataService.setData(POWER_REWARD_NAMESPACE, key, RedisCodec.gson().toJson(normalized), null);
        }
        JsonObject data = new JsonObject();
        data.addProperty("tier", tier);
        data.add("items", normalized);
        return GatewayResponse.ok(data);
    }

    private GatewayResponse handleAdminLoadTopRewards(RedisMessage request) {
        parseUuid(request.actor(), "actor");
        JsonObject payload = request.data();
        int rank = readInt(payload, "rank");
        if (rank <= 0) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "rank must be positive");
        }
        JsonArray stored = loadRewardArray(TOP_REWARD_NAMESPACE, String.valueOf(rank));
        List<ItemStack> items = deserializeItemsLenient(stored);
        JsonArray normalized = serializeItems(items);
        JsonObject data = new JsonObject();
        data.addProperty("rank", rank);
        data.add("items", normalized);
        return GatewayResponse.ok(data);
    }

    private GatewayResponse handleAdminSaveTopRewards(RedisMessage request) {
        parseUuid(request.actor(), "actor");
        JsonObject payload = request.data();
        int rank = readInt(payload, "rank");
        if (rank <= 0) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "rank must be positive");
        }
        JsonArray itemsArray = readArray(payload, "items");
        List<ItemStack> items = deserializeItemsStrict(itemsArray);
        JsonArray normalized = serializeItems(items);
        String key = String.valueOf(rank);
        if (normalized.size() == 0) {
            dataService.deleteData(TOP_REWARD_NAMESPACE, key);
        } else {
            dataService.setData(TOP_REWARD_NAMESPACE, key, RedisCodec.gson().toJson(normalized), null);
        }
        JsonObject data = new JsonObject();
        data.addProperty("rank", rank);
        data.add("items", normalized);
        return GatewayResponse.ok(data);
    }

    private GatewayResponse handleAdminGiveTopRewards(RedisMessage request) {
        parseUuid(request.actor(), "actor");
        JsonObject payload = request.data();
        int rank = readInt(payload, "rank");
        if (rank <= 0) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "rank must be positive");
        }
        JsonArray stored = loadRewardArray(TOP_REWARD_NAMESPACE, String.valueOf(rank));
        List<ItemStack> items = deserializeItemsLenient(stored);
        JsonArray normalized = serializeItems(items);
        JsonObject data = new JsonObject();
        data.addProperty("rank", rank);
        data.add("items", normalized);
        return GatewayResponse.ok(data);
    }

    private GatewayResponse handleProfileRegister(RedisMessage request) {
        JsonObject payload = request.data();
        String playerUuid = readString(payload, "playerUuid");
        String playerName = readString(payload, "playerName");
        long lastSeen = payload.has("lastSeen") ? payload.get("lastSeen").getAsLong() : System.currentTimeMillis();
        if (playerUuid == null || playerName == null) {
            return GatewayResponse.error(ErrorCode.BAD_REQUEST.code(), "playerUuid and playerName are required", false);
        }
        dataService.setPlayerProfile(new PlayerProfile(playerUuid, playerName, lastSeen));
        JsonObject data = new JsonObject();
        data.addProperty("registered", true);
        return GatewayResponse.ok(data);
    }

    private GatewayResponse handleProfileLookup(RedisMessage request) {
        JsonObject payload = request.data();
        String query = readString(payload, "query");
        if (query == null || query.isBlank()) {
            return GatewayResponse.error(ErrorCode.BAD_REQUEST.code(), "query is required", false);
        }
        Optional<PlayerProfile> result;
        if (query.length() == 36 && query.contains("-")) {
            result = dataService.findProfileByUuid(query);
        } else {
            result = dataService.findProfileByName(query);
        }
        JsonObject data = new JsonObject();
        result.ifPresent(profile -> data.add("profile", dataService.toJson(profile)));
        return GatewayResponse.ok(data);
    }

    private GatewayResponse handlePlayerIslandLookup(RedisMessage request) {
        JsonObject payload = request.data();
        String rawUuid = readString(payload, "playerUuid");
        UUID uuid;
        if (rawUuid != null) {
            uuid = parseUuid(rawUuid, "playerUuid");
        } else if (request.actor() != null) {
            uuid = parseUuid(request.actor(), "actor");
        } else {
            return GatewayResponse.error(ErrorCode.BAD_REQUEST.code(), "playerUuid is required", false);
        }

        Optional<UUID> islandId = islandCache.islandId(uuid);
        if (islandId.isEmpty() && bridge.isAvailable()) {
            islandCache.refresh(uuid);
            islandId = islandCache.islandId(uuid);
        }

        JsonObject data = new JsonObject();
        islandId.ifPresent(id -> data.addProperty("islandId", id.toString()));
        islandId.ifPresentOrElse(
                id -> putMetadata(uuid, "island.uuid", id.toString()),
                () -> removeMetadata(uuid, "island.uuid"));
        return GatewayResponse.ok(data);
    }

    private void publishResponse(RedisMessage request, GatewayResponse response) {
        RedisMessage outgoing = RedisMessage.responseFor(request);
        outgoing.setOk(response.ok());
        if (response.ok()) {
            outgoing.setData(response.data());
        } else if (response.error() != null) {
            outgoing.setError(response.error().code(), response.error().message(), response.error().retryable());
        }
        outgoing.compressDataIfNeeded(compressionThreshold);
        security.sign(outgoing);
        String responseChannel = channels.responseChannel(request.id());
        redisManager.publish(responseChannel, outgoing.toJson());
    }

    private void publishError(RedisMessage request, String code, String message, boolean retryable) {
        GatewayResponse error = GatewayResponse.error(code, message, retryable);
        publishResponse(request, error);
    }

    private void publishBus(String topic, JsonObject payload) {
        if (topic == null || topic.isBlank() || payload == null) {
            return;
        }
        redisManager.publish(channels.busChannel(topic), payload.toString());
    }

    private GatewayResponse executeIdempotent(String key,
                                              Supplier<GatewayResponse> action,
                                              Consumer<GatewayResponse> afterSuccess) {
        return idempotency.fetch(key).orElseGet(() -> {
            GatewayResponse response = action.get();
            idempotency.store(key, response);
            if (afterSuccess != null && response.ok()) {
                afterSuccess.accept(response);
            }
            return response;
        });
    }

    private GatewayResponse execute(String operation, Callable<GatewayResponse> callable) {
        int timeoutMs = config.gateway().superiorSkyblock().apiHookTimeoutMs();
        return execute(operation, callable, timeoutMs);
    }

    private GatewayResponse execute(String operation,
                                    Callable<GatewayResponse> callable,
                                    int timeoutMs) {
        try {
            return callSync(callable, timeoutMs);
        } catch (GatewayException ex) {
            if (config.logging().requestBodies()) {
                plugin.getLogger().log(Level.FINE, "Gateway operation " + operation + " failed: " + ex.getMessage(), ex);
            }
            return GatewayResponse.error(ex.code().code(), ex.getMessage(), ex.retryable());
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Gateway operation " + operation + " failed", ex);
            return GatewayResponse.error(ErrorCode.INTERNAL.code(), "Internal error", true);
        }
    }

    private GatewayResponse executeAsync(String operation, Callable<GatewayResponse> callable) {
        try {
            return callable.call();
        } catch (GatewayException ex) {
            if (config.logging().requestBodies()) {
                plugin.getLogger().log(Level.FINE, "Gateway operation " + operation + " failed: " + ex.getMessage(), ex);
            }
            return GatewayResponse.error(ex.code().code(), ex.getMessage(), ex.retryable());
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Gateway operation " + operation + " failed", ex);
            return GatewayResponse.error(ErrorCode.INTERNAL.code(), "Internal error", true);
        }
    }

    private <T> T callSync(Callable<T> callable, int timeoutMs) throws Exception {
        if (!bridge.isAvailable()) {
            throw new GatewayException(ErrorCode.INTERNAL, "SuperiorSkyblock bridge not available", true);
        }
        if (plugin.getServer().isPrimaryThread()) {
            return callable.call();
        }

        Future<T> future = plugin.getServer().getScheduler().callSyncMethod(plugin, callable);
        try {
            long waitMs = Math.max(100L, timeoutMs);
            return future.get(waitMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw new GatewayException(ErrorCode.TIMEOUT, "Timed out waiting for SuperiorSkyblock", true, ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof GatewayException gatewayException) {
                throw gatewayException;
            }
            throw new GatewayException(ErrorCode.INTERNAL,
                    cause != null && cause.getMessage() != null ? cause.getMessage() : "Internal error",
                    true,
                    cause);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new GatewayException(ErrorCode.INTERNAL, "Interrupted while waiting for SuperiorSkyblock", true, ex);
        }
    }

    private void updateMembershipCache(UUID playerUuid, JsonObject data) {
        if (playerUuid == null) {
            return;
        }
        UUID targetPlayer = playerUuid;
        if (data != null && data.has("memberUuid") && !data.get("memberUuid").isJsonNull()) {
            try {
                targetPlayer = UUID.fromString(data.get("memberUuid").getAsString());
            } catch (IllegalArgumentException ignored) {
                // ignore malformed override and fall back to actor uuid
            }
        }

        if (data != null && data.has("islandId") && !data.get("islandId").isJsonNull()) {
            String rawIsland = data.get("islandId").getAsString();
            try {
                UUID islandUuid = UUID.fromString(rawIsland);
                islandCache.setMembership(targetPlayer, islandUuid);
                putMetadata(targetPlayer, "island.uuid", islandUuid.toString());
                return;
            } catch (IllegalArgumentException ignored) {
                // fall through to refresh
            }
        }

        islandCache.refresh(targetPlayer);
        removeMetadata(targetPlayer, "island.uuid");
    }

    private GatewayResponse handleQuestState(RedisMessage request) {
        return execute("quest.state", () -> {
            UUID actorUuid = request.actor() != null ? parseUuid(request.actor(), "actor") : null;
            JsonObject payload = request.data();
            UUID islandUuid = resolveIslandUuid(actorUuid, null, payload);
            if (islandUuid == null) {
                throw new GatewayException(ErrorCode.ISLAND_NOT_FOUND, "Island not found");
            }
            JsonObject data = questService.toJson(questService.load(islandUuid.toString()));
            data.addProperty("islandId", islandUuid.toString());
            boolean canManage = actorUuid != null && bridge.canManageIslandQuests(actorUuid);
            data.addProperty("canManage", canManage);
            return GatewayResponse.ok(data);
        });
    }

    private GatewayResponse handleQuestAssign(RedisMessage request) {
        return execute("quest.assign", () -> {
            UUID actorUuid = parseUuid(request.actor(), "actor");
            JsonObject payload = request.data();
            QuestType type = parseQuestType(payload);
            int questCount = payload.has("questCount") ? payload.get("questCount").getAsInt() : 0;
            if (questCount <= 0) {
                throw new GatewayException(ErrorCode.BAD_REQUEST, "questCount must be positive");
            }
            UUID islandUuid = resolveIslandUuid(actorUuid, null, payload);
            if (islandUuid == null) {
                throw new GatewayException(ErrorCode.ISLAND_NOT_FOUND, "Island not found");
            }
            if (!bridge.canManageIslandQuests(actorUuid)) {
                throw new GatewayException(ErrorCode.QUEST_PERMISSION_DENIED, "Actor lacks quest permissions");
            }
            int memberCount = Math.max(bridge.memberCount(islandUuid), islandCache.members(islandUuid).size());
            if (memberCount <= 0) {
                memberCount = 1;
            }
            JsonObject data = questService.toJson(questService.assign(islandUuid.toString(), type, questCount, memberCount));
            data.addProperty("islandId", islandUuid.toString());
            data.addProperty("memberCount", memberCount);
            data.addProperty("type", type.name());
            data.addProperty("questCount", questCount);
            return GatewayResponse.ok(data);
        });
    }

    private GatewayResponse handleQuestProgress(RedisMessage request) {
        return execute("quest.progress", () -> {
            UUID actorUuid = request.actor() != null ? parseUuid(request.actor(), "actor") : null;
            JsonObject payload = request.data();
            QuestType type = parseQuestType(payload);
            int questId = payload.has("questId") ? payload.get("questId").getAsInt() : 0;
            if (questId <= 0) {
                throw new GatewayException(ErrorCode.BAD_REQUEST, "questId must be positive");
            }
            int amount = payload.has("amount") ? payload.get("amount").getAsInt() : 1;
            String contributorRaw = readString(payload, "contributor");
            UUID contributorUuid = contributorRaw != null ? parseUuid(contributorRaw, "contributor") : actorUuid;
            UUID islandUuid = resolveIslandUuid(actorUuid, contributorUuid, payload);
            if (islandUuid == null) {
                throw new GatewayException(ErrorCode.ISLAND_NOT_FOUND, "Island not found");
            }
            JsonObject data = questService.toJson(questService.increment(islandUuid.toString(), type, questId, amount, contributorUuid));
            data.addProperty("islandId", islandUuid.toString());
            data.addProperty("questId", questId);
            data.addProperty("amount", amount);
            data.addProperty("type", type.name());
            if (contributorUuid != null) {
                data.addProperty("contributorUuid", contributorUuid.toString());
            }
            return GatewayResponse.ok(data);
        });
    }

    private GatewayResponse handleFarmRankingTop(RedisMessage request) {
        return executeAsync("farm.ranking.top", () -> {
            JsonObject payload = request.data();
            int limit = payload.has("limit") ? Math.max(1, readInt(payload, "limit")) : 10;
            JsonObject data = rankingService != null ? rankingService.topIslands(limit) : new JsonObject();
            return GatewayResponse.ok(data);
        });
    }

    private GatewayResponse handleFarmRankingMembers(RedisMessage request) {
        return executeAsync("farm.ranking.members", () -> {
            JsonObject payload = request.data();
            String islandId = readString(payload, "islandId");
            if (islandId == null) {
                throw new GatewayException(ErrorCode.BAD_REQUEST, "islandId is required");
            }
            UUID islandUuid;
            try {
                islandUuid = UUID.fromString(islandId);
            } catch (IllegalArgumentException ex) {
                throw new GatewayException(ErrorCode.BAD_REQUEST, "islandId must be a UUID", false, ex);
            }
            int limit = payload.has("limit") ? Math.max(1, readInt(payload, "limit")) : 25;
            JsonObject data = rankingService != null
                    ? rankingService.islandMembers(islandUuid, limit)
                    : new JsonObject();
            data.addProperty("islandId", islandId);
            return GatewayResponse.ok(data);
        });
    }

    private GatewayResponse handleFarmRankingIncrement(RedisMessage request) {
        return execute("farm.ranking.increment", () -> {
            if (rankingService == null) {
                return GatewayResponse.ok(new JsonObject());
            }
            JsonObject payload = request.data();
            String islandRaw = readString(payload, "islandId");
            if (islandRaw == null || islandRaw.isBlank()) {
                throw new GatewayException(ErrorCode.BAD_REQUEST, "islandId is required");
            }
            UUID islandUuid = parseUuid(islandRaw, "islandId");
            long totalIncrement = payload.has("total") ? payload.get("total").getAsLong() : 0L;
            long dailyIncrement = payload.has("daily") ? payload.get("daily").getAsLong() : 0L;
            long weeklyIncrement = payload.has("weekly") ? payload.get("weekly").getAsLong() : 0L;
            if (totalIncrement < 0 || dailyIncrement < 0 || weeklyIncrement < 0) {
                throw new GatewayException(ErrorCode.BAD_REQUEST, "increments must be non-negative");
            }
            if (totalIncrement == 0) {
                long sum = dailyIncrement + weeklyIncrement;
                if (sum > 0) {
                    totalIncrement = sum;
                }
            }
            if (totalIncrement == 0 && dailyIncrement == 0 && weeklyIncrement == 0) {
                throw new GatewayException(ErrorCode.BAD_REQUEST, "at least one increment value must be positive");
            }
            String contributorRaw = readString(payload, "contributorUuid");
            UUID contributorUuid = contributorRaw != null && !contributorRaw.isBlank()
                    ? parseUuid(contributorRaw, "contributorUuid")
                    : null;
            rankingService.incrementScores(islandUuid, contributorUuid, totalIncrement, dailyIncrement, weeklyIncrement);
            JsonObject data = new JsonObject();
            data.addProperty("islandId", islandUuid.toString());
            data.addProperty("total", totalIncrement);
            data.addProperty("daily", dailyIncrement);
            data.addProperty("weekly", weeklyIncrement);
            if (contributorUuid != null) {
                data.addProperty("contributorUuid", contributorUuid.toString());
            }
            return GatewayResponse.ok(data);
        });
    }

    private GatewayResponse handleFarmRewardTable(RedisMessage request) {
        return execute("farm.reward.table", () -> {
            JsonObject data = new JsonObject();
            JsonArray rewards = new JsonArray();
            PluginConfig.RewardSettings rewardSettings = config.gateway().rewards();
            if (rewardSettings != null) {
                for (var reward : rewardSettings.farmRanking()) {
                    JsonObject tier = new JsonObject();
                    tier.addProperty("minRank", reward.minRank());
                    tier.addProperty("maxRank", reward.maxRank());
                    tier.addProperty("title", reward.title());
                    tier.addProperty("icon", reward.icon());
                    tier.addProperty("farmPoints", reward.farmPoints());
                    JsonArray lore = new JsonArray();
                    for (String line : reward.lore()) {
                        lore.add(line);
                    }
                    tier.add("lore", lore);
                    rewards.add(tier);
                }
            }
            data.add("rewards", rewards);
            return GatewayResponse.ok(data);
        });
    }

    private GatewayResponse handleFarmShopTable(RedisMessage request) {
        return execute("farm.shop.table", () -> {
            JsonObject data = new JsonObject();
            JsonArray items = new JsonArray();
            PluginConfig.ShopSettings shopSettings = config.gateway().shop();
            if (shopSettings != null) {
                for (PluginConfig.ShopItem item : shopSettings.items()) {
                    if (!item.enabled()) {
                        continue;
                    }
                    JsonObject entry = new JsonObject();
                    entry.addProperty("slot", item.slot());
                    entry.addProperty("title", item.title());
                    entry.addProperty("icon", item.icon());
                    entry.addProperty("currency", item.currency());
                    entry.addProperty("price", item.price());
                    entry.addProperty("command", item.command());
                    entry.addProperty("enabled", item.enabled());
                    JsonArray lore = new JsonArray();
                    for (String line : item.lore()) {
                        lore.add(line);
                    }
                    entry.add("lore", lore);
                    items.add(entry);
                }
            }
            data.add("items", items);
            return GatewayResponse.ok(data);
        });
    }

    private GatewayResponse handleFarmPointsInfo(RedisMessage request) {
        UUID actorUuid = parseUuid(request.actor(), "actor");
        return execute("farm.points.info", () -> {
            JsonObject data;
            UUID islandUuid = bridge.islandIdForPlayer(actorUuid).orElse(null);
            if (islandUuid == null) {
                data = new JsonObject();
                data.addProperty("hasIsland", false);
            } else {
                data = rankingService != null
                        ? rankingService.islandPoints(islandUuid)
                        : new JsonObject();
                data.addProperty("hasIsland", true);
            }
            return GatewayResponse.ok(data);
        });
    }

    private GatewayResponse handleFarmHopperInfo(RedisMessage request) {
        UUID actorUuid = parseUuid(request.actor(), "actor");
        return execute("farm.hopper.info", () -> {
            UUID islandUuid = bridge.islandIdForPlayer(actorUuid).orElse(null);
            if (islandUuid == null) {
                JsonObject data = new JsonObject();
                data.addProperty("hasIsland", false);
                return GatewayResponse.ok(data);
            }
            GatewayResponse response = bridge.hopperState(actorUuid);
            if (!response.ok()) {
                return response;
            }
            JsonObject data = response.data() != null ? response.data().deepCopy() : new JsonObject();
            data.addProperty("hasIsland", true);
            return GatewayResponse.ok(data);
        });
    }

    private GatewayResponse handleFarmRatingUpdate(RedisMessage request) {
        UUID actorUuid = parseUuid(request.actor(), "actor");
        int rating = readInt(request.data(), "rating");
        if (rating < 0 || rating > 5) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "rating must be between 0 and 5");
        }
        return execute("farm.rating.update", () -> bridge.updateIslandRating(actorUuid, rating));
    }

    private GatewayResponse handleFarmWarpHomeList(RedisMessage request) {
        UUID actorUuid = parseUuid(request.actor(), "actor");
        return execute("farm.warp.home.list", () -> bridge.listHomeWarps(actorUuid));
    }

    private GatewayResponse handleFarmWarpHomeSet(RedisMessage request) {
        UUID actorUuid = parseUuid(request.actor(), "actor");
        JsonObject payload = request.data();
        String name = readString(payload, "name");
        if (name == null || name.isBlank()) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "name is required");
        }
        Location location = readLocation(payload);
        return execute("farm.warp.home.set", () -> bridge.createHomeWarp(actorUuid, name, location));
    }

    private GatewayResponse handleFarmWarpHomeDelete(RedisMessage request) {
        UUID actorUuid = parseUuid(request.actor(), "actor");
        JsonObject payload = request.data();
        String warp = readString(payload, "warp");
        if (warp == null || warp.isBlank()) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "warp is required");
        }
        return execute("farm.warp.home.delete", () -> bridge.deleteHomeWarp(actorUuid, warp));
    }

    private GatewayResponse handleFarmWarpHomeRename(RedisMessage request) {
        UUID actorUuid = parseUuid(request.actor(), "actor");
        JsonObject payload = request.data();
        String warp = readString(payload, "warp");
        String name = readString(payload, "name");
        if (warp == null || warp.isBlank() || name == null || name.isBlank()) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "warp and name are required");
        }
        return execute("farm.warp.home.rename", () -> bridge.renameHomeWarp(actorUuid, warp, name));
    }

    private GatewayResponse handleFarmWarpHomeToggle(RedisMessage request) {
        UUID actorUuid = parseUuid(request.actor(), "actor");
        JsonObject payload = request.data();
        String warp = readString(payload, "warp");
        if (warp == null || warp.isBlank()) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "warp is required");
        }
        return execute("farm.warp.home.toggle", () -> bridge.toggleHomeWarpPrivacy(actorUuid, warp));
    }

    private GatewayResponse handleFarmWarpPlayerList(RedisMessage request) {
        UUID actorUuid = parseUuid(request.actor(), "actor");
        JsonObject payload = request.data();
        String target = readString(payload, "target");
        if (target == null || target.isBlank()) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "target is required");
        }
        return execute("farm.warp.player.list", () -> bridge.listPlayerWarps(actorUuid, target));
    }

    private GatewayResponse handleFarmWarpGlobalList(RedisMessage request) {
        UUID actorUuid = parseUuid(request.actor(), "actor");
        JsonObject payload = request.data();
        int page = payload.has("page") ? Math.max(1, readInt(payload, "page")) : 1;
        int pageSize = payload.has("pageSize") ? Math.max(1, readInt(payload, "pageSize")) : 36;
        return execute("farm.warp.global.list", () -> bridge.listGlobalWarps(actorUuid, page, pageSize));
    }

    private GatewayResponse handleFarmWarpVisit(RedisMessage request) {
        UUID actorUuid = parseUuid(request.actor(), "actor");
        JsonObject payload = request.data();
        String islandId = readString(payload, "islandId");
        String warp = readString(payload, "warp");
        if (islandId == null || islandId.isBlank() || warp == null || warp.isBlank()) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "islandId and warp are required");
        }
        return execute("farm.warp.visit", () -> bridge.visitWarp(actorUuid, islandId, warp));
    }

    private GatewayResponse handleFarmRuleList(RedisMessage request) {
        UUID actorUuid = parseUuid(request.actor(), "actor");
        UUID islandUuid = bridge.islandIdForPlayer(actorUuid).orElse(null);
        if (islandUuid == null) {
            throw new GatewayException(ErrorCode.ISLAND_NOT_FOUND, "Player has no island");
        }
        JsonArray rules = loadIslandRules(islandUuid);
        JsonObject data = buildRulePayload(actorUuid, islandUuid, rules);
        return GatewayResponse.ok(data);
    }

    private GatewayResponse handleFarmRuleAdd(RedisMessage request) {
        UUID actorUuid = parseUuid(request.actor(), "actor");
        JsonObject payload = request.data();
        String rule = readString(payload, "rule");
        if (rule == null) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "rule is required");
        }
        String normalized = rule.trim();
        if (normalized.isEmpty()) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "rule is required");
        }
        UUID islandUuid = bridge.islandIdForPlayer(actorUuid).orElse(null);
        if (islandUuid == null) {
            throw new GatewayException(ErrorCode.ISLAND_NOT_FOUND, "Player has no island");
        }
        ensureIslandOwner(actorUuid, islandUuid);
        JsonArray rules = loadIslandRules(islandUuid);
        if (rules.size() >= MAX_ISLAND_RULES) {
            throw new GatewayException(ErrorCode.CONFLICT, "Maximum number of rules reached");
        }
        rules.add(normalized);
        saveIslandRules(islandUuid, rules);
        JsonObject data = buildRulePayload(actorUuid, islandUuid, rules);
        data.addProperty("added", normalized);
        return GatewayResponse.ok(data);
    }

    private GatewayResponse handleFarmRuleRemove(RedisMessage request) {
        UUID actorUuid = parseUuid(request.actor(), "actor");
        JsonObject payload = request.data();
        int index = readInt(payload, "index");
        if (index <= 0) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "index must be positive");
        }
        UUID islandUuid = bridge.islandIdForPlayer(actorUuid).orElse(null);
        if (islandUuid == null) {
            throw new GatewayException(ErrorCode.ISLAND_NOT_FOUND, "Player has no island");
        }
        ensureIslandOwner(actorUuid, islandUuid);
        JsonArray rules = loadIslandRules(islandUuid);
        int zeroIndex = index - 1;
        if (zeroIndex < 0 || zeroIndex >= rules.size()) {
            throw new GatewayException(ErrorCode.NOT_FOUND, "Rule not found");
        }
        String removed = rules.get(zeroIndex).getAsString();
        rules.remove(zeroIndex);
        saveIslandRules(islandUuid, rules);
        JsonObject data = buildRulePayload(actorUuid, islandUuid, rules);
        data.addProperty("removed", removed);
        data.addProperty("removedIndex", index);
        return GatewayResponse.ok(data);
    }

    private GatewayResponse handleFarmCoopList(RedisMessage request) {
        UUID actorUuid = parseUuid(request.actor(), "actor");
        return execute("farm.coop.list", () -> bridge.listCoopPlayers(actorUuid));
    }

    private GatewayResponse handleFarmCoopAdd(RedisMessage request) {
        UUID actorUuid = parseUuid(request.actor(), "actor");
        JsonObject payload = request.data();
        String target = readString(payload, "target");
        if (target == null || target.isBlank()) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "target is required");
        }
        return execute("farm.coop.add", () -> bridge.addCoopPlayer(actorUuid, target));
    }

    private GatewayResponse handleFarmCoopRemove(RedisMessage request) {
        UUID actorUuid = parseUuid(request.actor(), "actor");
        JsonObject payload = request.data();
        String target = readString(payload, "target");
        if (target == null || target.isBlank()) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "target is required");
        }
        return execute("farm.coop.remove", () -> bridge.removeCoopPlayer(actorUuid, target));
    }

    private GatewayResponse handleFarmBanList(RedisMessage request) {
        UUID actorUuid = parseUuid(request.actor(), "actor");
        return execute("farm.ban.list", () -> bridge.listBannedPlayers(actorUuid));
    }

    private GatewayResponse handleFarmBanAdd(RedisMessage request) {
        UUID actorUuid = parseUuid(request.actor(), "actor");
        JsonObject payload = request.data();
        String target = readString(payload, "target");
        if (target == null || target.isBlank()) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "target is required");
        }
        return execute("farm.ban.add", () -> bridge.addBannedPlayer(actorUuid, target));
    }

    private GatewayResponse handleFarmBanRemove(RedisMessage request) {
        UUID actorUuid = parseUuid(request.actor(), "actor");
        JsonObject payload = request.data();
        String target = readString(payload, "target");
        if (target == null || target.isBlank()) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "target is required");
        }
        return execute("farm.ban.remove", () -> bridge.removeBannedPlayer(actorUuid, target));
    }

    private GatewayResponse handleFarmChatSend(RedisMessage request) {
        UUID actorUuid = parseUuid(request.actor(), "actor");
        JsonObject payload = request.data();
        String rawMessage = readString(payload, "message");
        if (rawMessage == null || rawMessage.isBlank()) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "message is required");
        }
        if (rawMessage.length() > 1024) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "message is too long");
        }
        return execute("farm.chat.send", () -> {
            String decoded = decodeChatPayload(rawMessage);
            String sanitized = sanitizeChatMessage(decoded);
            if (sanitized.isEmpty()) {
                throw new GatewayException(ErrorCode.BAD_REQUEST, "message is required");
            }
            if (sanitized.length() > 256) {
                sanitized = sanitized.substring(0, 256);
            }
            UUID islandUuid = bridge.islandIdForPlayer(actorUuid)
                    .orElseThrow(() -> new GatewayException(ErrorCode.ISLAND_NOT_FOUND, "Island not found"));
            String actorName = resolveActorName(request, actorUuid);
            String formatted = ChatColor.GREEN + "[팜] "
                    + ChatColor.WHITE + actorName
                    + ChatColor.GRAY + " » "
                    + ChatColor.WHITE + sanitized;
            bridge.broadcastIslandMessage(islandUuid, List.of(formatted));

            JsonObject data = new JsonObject();
            data.addProperty("islandId", islandUuid.toString());
            data.addProperty("actorName", actorName);
            data.addProperty("message", sanitized);
            return GatewayResponse.ok(data);
        });
    }

    private GatewayResponse handleIslandDisband(RedisMessage request) {
        UUID actorUuid = parseUuid(request.actor(), "actor");
        return execute("island.disband", () -> bridge.disbandIsland(actorUuid));
    }

    private GatewayResponse handleFarmRankingSnapshot(RedisMessage request) {
        return executeAsync("farm.ranking.snapshot", () -> {
            JsonObject payload = request.data();
            String periodId = readString(payload, "periodId");
            if (periodId == null || periodId.isBlank()) {
                throw new GatewayException(ErrorCode.BAD_REQUEST, "periodId is required");
            }
            String displayName = readString(payload, "displayName");
            int limit = payload.has("limit") ? Math.max(1, readInt(payload, "limit")) : 10;
            if (rankingService != null) {
                rankingService.snapshot(periodId, displayName, limit);
            }
            JsonObject data = new JsonObject();
            data.addProperty("snapshot", periodId);
            return GatewayResponse.ok(data);
        });
    }

    private GatewayResponse handleFarmHistoryList(RedisMessage request) {
        return executeAsync("farm.history.list", () -> {
            JsonObject payload = request.data();
            int page = payload.has("page") ? Math.max(1, readInt(payload, "page")) : 1;
            int pageSize = payload.has("pageSize") ? Math.max(1, readInt(payload, "pageSize")) : 6;
            JsonObject data = rankingService != null
                    ? rankingService.historyList(page, pageSize)
                    : new JsonObject();
            data.addProperty("page", page);
            data.addProperty("pageSize", pageSize);
            return GatewayResponse.ok(data);
        });
    }

    private GatewayResponse handleFarmHistoryDetail(RedisMessage request) {
        return executeAsync("farm.history.detail", () -> {
            JsonObject payload = request.data();
            String periodId = readString(payload, "periodId");
            if (periodId == null || periodId.isBlank()) {
                throw new GatewayException(ErrorCode.BAD_REQUEST, "periodId is required");
            }
            JsonObject data = rankingService != null
                    ? rankingService.historyDetail(periodId)
                    : new JsonObject();
            data.addProperty("periodId", periodId);
            return GatewayResponse.ok(data);
        });
    }

    private GatewayResponse handleFarmBorderState(RedisMessage request) {
        UUID actorUuid = parseUuid(request.actor(), "actor");
        return execute("farm.border.state", () -> bridge.borderState(actorUuid));
    }

    private GatewayResponse handleFarmBorderToggle(RedisMessage request) {
        UUID actorUuid = parseUuid(request.actor(), "actor");
        return execute("farm.border.toggle", () -> bridge.toggleWorldBorder(actorUuid));
    }

    private GatewayResponse handleFarmBorderColor(RedisMessage request) {
        UUID actorUuid = parseUuid(request.actor(), "actor");
        JsonObject payload = request.data();
        String color = readString(payload, "color");
        if (color == null || color.isBlank()) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "color is required");
        }
        String normalized = color.trim();
        return execute("farm.border.color", () -> bridge.setBorderColor(actorUuid, normalized));
    }

    private void requestTeleport(JsonObject data) {
        if (data == null || !data.has("memberUuid") || data.get("memberUuid").isJsonNull()) {
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("playerUuid", data.get("memberUuid").getAsString());
        publishBus("farm.setloclobby", payload);
    }

    private QuestType parseQuestType(JsonObject payload) {
        String rawType = readString(payload, "type");
        if (rawType == null) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "type is required");
        }
        try {
            return QuestType.valueOf(rawType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "Unknown quest type: " + rawType, false, ex);
        }
    }

    private UUID resolveIslandUuid(UUID actorUuid, UUID contributorUuid, JsonObject payload) {
        String islandRaw = readString(payload, "islandId");
        if (islandRaw != null && !islandRaw.isBlank()) {
            return parseUuid(islandRaw, "islandId");
        }
        UUID reference = contributorUuid != null ? contributorUuid : actorUuid;
        if (reference == null) {
            return null;
        }
        Optional<UUID> cached = islandCache.islandId(reference);
        if (cached.isPresent()) {
            return cached.get();
        }
        Optional<UUID> resolved = bridge.islandIdForPlayer(reference);
        resolved.ifPresent(uuid -> islandCache.setMembership(reference, uuid));
        return resolved.orElse(null);
    }

    private void putMetadata(UUID player, String key, String value) {
        if (metadataService == null) {
            return;
        }
        metadataService.put(player, key, value, null)
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING, "Failed to store metadata " + key + " for " + player, ex);
                    return null;
                });
    }

    private void removeMetadata(UUID player, String key) {
        if (metadataService == null) {
            return;
        }
        metadataService.delete(player, key)
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING, "Failed to remove metadata " + key + " for " + player, ex);
                    return null;
                });
    }

    private String decodeChatPayload(String encoded) {
        if (encoded == null) {
            return "";
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "message must be base64 encoded", false, ex);
        }
    }

    private String sanitizeChatMessage(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = raw.replace('\n', ' ').replace('\r', ' ').replace('§', '&');
        return normalized.replaceAll("\\s+", " ").trim();
    }

    private String resolveActorName(RedisMessage request, UUID actorUuid) {
        String actorName = null;
        if (request != null) {
            JsonObject root = request.root();
            if (root != null && root.has("actorName") && !root.get("actorName").isJsonNull()) {
                String raw = root.get("actorName").getAsString();
                if (raw != null && !raw.isBlank()) {
                    actorName = raw;
                }
            }
        }
        if (actorName == null || actorName.isBlank()) {
            actorName = bridge.lookupPlayerName(actorUuid.toString()).orElse(actorUuid.toString());
        }
        return actorName;
    }

    private UUID parseUuid(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, field + " is required");
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, field + " must be a UUID", false, ex);
        }
    }

    private String readString(JsonObject payload, String field) {
        return payload.has(field) && !payload.get(field).isJsonNull()
                ? payload.get(field).getAsString()
                : null;
    }

    private BigDecimal readDecimal(JsonObject payload, String field) {
        String raw = readString(payload, field);
        if (raw == null || raw.isBlank()) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, field + " is required");
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException ex) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, field + " must be a number", false, ex);
        }
    }

    private int readInt(JsonObject payload, String field) {
        if (payload.has(field) && payload.get(field).isJsonPrimitive()) {
            try {
                return payload.get(field).getAsInt();
            } catch (NumberFormatException ex) {
                throw new GatewayException(ErrorCode.BAD_REQUEST, field + " must be a number", false, ex);
            }
        }
        String raw = readString(payload, field);
        if (raw == null || raw.isBlank()) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, field + " is required");
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, field + " must be a number", false, ex);
        }
    }

    private boolean readBoolean(JsonObject payload, String field) {
        if (payload == null || !payload.has(field)) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, field + " is required");
        }
        try {
            return payload.get(field).getAsBoolean();
        } catch (Exception ex) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, field + " must be a boolean", false, ex);
        }
    }

    private boolean isBankLocked(UUID islandUuid) {
        if (islandUuid == null) {
            return false;
        }
        return dataService.getData(BANK_LOCK_NAMESPACE, islandUuid.toString()).isPresent();
    }

    private double readDouble(JsonObject payload, String field) {
        if (payload.has(field) && payload.get(field).isJsonPrimitive()) {
            try {
                return payload.get(field).getAsDouble();
            } catch (NumberFormatException ex) {
                throw new GatewayException(ErrorCode.BAD_REQUEST, field + " must be a number", false, ex);
            }
        }
        String raw = readString(payload, field);
        if (raw == null || raw.isBlank()) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, field + " is required");
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, field + " must be a number", false, ex);
        }
    }

    private Location readLocation(JsonObject payload) {
        String worldName = readString(payload, "world");
        if (worldName == null || worldName.isBlank()) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "world is required");
        }
        var world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "Unknown world: " + worldName);
        }
        double x = readDouble(payload, "x");
        double y = readDouble(payload, "y");
        double z = readDouble(payload, "z");
        double yawValue = payload.has("yaw") && !payload.get("yaw").isJsonNull() ? payload.get("yaw").getAsDouble() : 0.0D;
        double pitchValue = payload.has("pitch") && !payload.get("pitch").isJsonNull() ? payload.get("pitch").getAsDouble() : 0.0D;
        return new Location(world, x, y, z, (float) yawValue, (float) pitchValue);
    }

    private JsonArray readArray(JsonObject payload, String field) {
        if (!payload.has(field) || payload.get(field).isJsonNull()) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, field + " is required");
        }
        if (!payload.get(field).isJsonArray()) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, field + " must be an array");
        }
        return payload.get(field).getAsJsonArray().deepCopy();
    }

    private JsonArray loadRewardArray(String namespace, String key) {
        return dataService.getData(namespace, key)
                .map(this::parseArray)
                .orElseGet(JsonArray::new);
    }

    private JsonArray loadIslandRules(UUID islandUuid) {
        if (islandUuid == null) {
            return new JsonArray();
        }
        try {
            return dataService.getData(ISLAND_RULE_NAMESPACE, islandUuid.toString())
                    .map(raw -> RedisCodec.gson().fromJson(raw, JsonArray.class))
                    .orElseGet(JsonArray::new);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to parse island rules for " + islandUuid, ex);
            return new JsonArray();
        }
    }

    private void saveIslandRules(UUID islandUuid, JsonArray rules) {
        if (islandUuid == null) {
            return;
        }
        String key = islandUuid.toString();
        if (rules == null || rules.size() == 0) {
            dataService.deleteData(ISLAND_RULE_NAMESPACE, key);
        } else {
            dataService.setData(ISLAND_RULE_NAMESPACE, key, RedisCodec.gson().toJson(rules), null);
        }
    }

    private void ensureIslandOwner(UUID actorUuid, UUID islandUuid) {
        SuperiorSkyblockBridge.IslandDetails details = bridge.describeIsland(islandUuid);
        if (details == null) {
            throw new GatewayException(ErrorCode.ISLAND_NOT_FOUND, "Island not found");
        }
        if (details.ownerUuid() == null || !details.ownerUuid().equals(actorUuid)) {
            throw new GatewayException(ErrorCode.FORBIDDEN, "Only the island owner can modify rules");
        }
    }

    private JsonObject buildRulePayload(UUID actorUuid, UUID islandUuid, JsonArray rules) {
        JsonObject data = new JsonObject();
        data.addProperty("islandId", islandUuid.toString());
        SuperiorSkyblockBridge.IslandDetails details = bridge.describeIsland(islandUuid);
        boolean editable = false;
        if (details != null) {
            data.addProperty("islandName", details.name());
            if (details.ownerUuid() != null) {
                data.addProperty("ownerUuid", details.ownerUuid().toString());
                editable = details.ownerUuid().equals(actorUuid);
            }
            if (details.ownerName() != null) {
                data.addProperty("ownerName", details.ownerName());
            }
        }
        data.addProperty("editable", editable);
        data.addProperty("max", MAX_ISLAND_RULES);
        data.add("rules", rules != null ? rules.deepCopy() : new JsonArray());
        return data;
    }

    private JsonArray serializeItems(List<ItemStack> items) {
        JsonArray array = new JsonArray();
        if (items == null) {
            return array;
        }
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            try {
                String encoded = encodeItem(item.clone());
                if (encoded != null && !encoded.isBlank()) {
                    array.add(encoded);
                }
            } catch (IOException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to serialize reward item", ex);
            }
            if (array.size() >= MAX_REWARD_SLOTS) {
                break;
            }
        }
        return array;
    }

    private List<ItemStack> deserializeItemsStrict(JsonArray array) {
        List<ItemStack> items = new ArrayList<>();
        if (array == null) {
            return items;
        }
        int index = 0;
        for (JsonElement element : array) {
            index++;
            if (!element.isJsonPrimitive()) {
                throw new GatewayException(ErrorCode.BAD_REQUEST, "Invalid item entry at index " + (index - 1));
            }
            String token = element.getAsString();
            try {
                ItemStack item = decodeItem(token);
                if (item != null && !item.getType().isAir()) {
                    items.add(item);
                }
            } catch (IOException | ClassNotFoundException ex) {
                throw new GatewayException(ErrorCode.BAD_REQUEST, "Invalid item entry", false, ex);
            }
            if (items.size() >= MAX_REWARD_SLOTS) {
                break;
            }
        }
        return items;
    }

    private List<ItemStack> deserializeItemsLenient(JsonArray array) {
        List<ItemStack> items = new ArrayList<>();
        if (array == null) {
            return items;
        }
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive()) {
                continue;
            }
            String token = element.getAsString();
            try {
                ItemStack item = decodeItem(token);
                if (item != null && !item.getType().isAir()) {
                    items.add(item);
                }
            } catch (IOException | ClassNotFoundException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to deserialize stored reward item", ex);
            }
            if (items.size() >= MAX_REWARD_SLOTS) {
                break;
            }
        }
        return items;
    }

    private String encodeItem(ItemStack item) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             BukkitObjectOutputStream data = new BukkitObjectOutputStream(output)) {
            data.writeObject(item);
            data.flush();
            return Base64.getEncoder().encodeToString(output.toByteArray());
        }
    }

    private ItemStack decodeItem(String token) throws IOException, ClassNotFoundException {
        if (token == null || token.isBlank()) {
            return null;
        }
        byte[] bytes = Base64.getDecoder().decode(token);
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes);
             BukkitObjectInputStream data = new BukkitObjectInputStream(input)) {
            Object object = data.readObject();
            return object instanceof ItemStack stack ? stack : null;
        }
    }

    private JsonArray parseArray(String raw) {
        if (raw == null || raw.isBlank()) {
            return new JsonArray();
        }
        try {
            return RedisCodec.gson().fromJson(raw, JsonArray.class);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to parse stored reward data", ex);
            return new JsonArray();
        }
    }

    private UUID extractUuid(JsonObject payload, String field) {
        if (payload == null || field == null || !payload.has(field) || payload.get(field).isJsonNull()) {
            return null;
        }
        try {
            return UUID.fromString(payload.get(field).getAsString());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
