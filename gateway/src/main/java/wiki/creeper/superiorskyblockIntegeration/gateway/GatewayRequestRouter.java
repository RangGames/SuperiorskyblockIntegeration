package wiki.creeper.superiorskyblockIntegeration.gateway;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.plugin.java.JavaPlugin;
import java.time.Duration;
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
                         PlayerMetadataService metadataService) {
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
            case ISLAND_DISBAND -> handleIslandDisband(request);
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
                events.publishInviteCreated(actorUuid, response.data());
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
        return execute("island.get", () -> bridge.getIslandInfo(actorUuid, owner, payload.deepCopy()));
    }

    private GatewayResponse handleMembersList(RedisMessage request) {
        UUID actorUuid = request.actor() != null ? parseUuid(request.actor(), "actor") : null;
        JsonObject payload = request.data();
        Optional<String> islandId = Optional.ofNullable(readString(payload, "islandId"));
        return execute("members.list", () -> bridge.listMembers(actorUuid, islandId, payload.deepCopy()));
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
        try {
            return callSync(callable);
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

    private <T> T callSync(Callable<T> callable) throws Exception {
        if (!bridge.isAvailable()) {
            throw new GatewayException(ErrorCode.INTERNAL, "SuperiorSkyblock bridge not available", true);
        }
        if (plugin.getServer().isPrimaryThread()) {
            return callable.call();
        }

        Future<T> future = plugin.getServer().getScheduler().callSyncMethod(plugin, callable);
        try {
            return future.get(config.gateway().superiorSkyblock().apiHookTimeoutMs(), TimeUnit.MILLISECONDS);
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
        return execute("farm.ranking.top", () -> {
            JsonObject payload = request.data();
            int limit = payload.has("limit") ? Math.max(1, payload.get("limit").getAsInt()) : 10;
            JsonObject data = rankingService != null ? rankingService.topIslands(limit) : new JsonObject();
            return GatewayResponse.ok(data);
        });
    }

    private GatewayResponse handleFarmRankingMembers(RedisMessage request) {
        return execute("farm.ranking.members", () -> {
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
            int limit = payload.has("limit") ? Math.max(1, payload.get("limit").getAsInt()) : 25;
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
                    tier.addProperty("moonlight", reward.moonlight());
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

    private GatewayResponse handleIslandDisband(RedisMessage request) {
        UUID actorUuid = parseUuid(request.actor(), "actor");
        return execute("island.disband", () -> bridge.disbandIsland(actorUuid));
    }

    private GatewayResponse handleFarmRankingSnapshot(RedisMessage request) {
        return execute("farm.ranking.snapshot", () -> {
            JsonObject payload = request.data();
            String periodId = readString(payload, "periodId");
            if (periodId == null || periodId.isBlank()) {
                throw new GatewayException(ErrorCode.BAD_REQUEST, "periodId is required");
            }
            String displayName = readString(payload, "displayName");
            int limit = payload.has("limit") ? Math.max(1, payload.get("limit").getAsInt()) : 10;
            if (rankingService != null) {
                rankingService.snapshot(periodId, displayName, limit);
            }
            JsonObject data = new JsonObject();
            data.addProperty("snapshot", periodId);
            return GatewayResponse.ok(data);
        });
    }

    private GatewayResponse handleFarmHistoryList(RedisMessage request) {
        return execute("farm.history.list", () -> {
            JsonObject payload = request.data();
            int page = payload.has("page") ? Math.max(1, payload.get("page").getAsInt()) : 1;
            int pageSize = payload.has("pageSize") ? Math.max(1, payload.get("pageSize").getAsInt()) : 6;
            JsonObject data = rankingService != null
                    ? rankingService.historyList(page, pageSize)
                    : new JsonObject();
            data.addProperty("page", page);
            data.addProperty("pageSize", pageSize);
            return GatewayResponse.ok(data);
        });
    }

    private GatewayResponse handleFarmHistoryDetail(RedisMessage request) {
        return execute("farm.history.detail", () -> {
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
        metadataService.put(player, key, value, null)
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING, "Failed to store metadata " + key + " for " + player, ex);
                    return null;
                });
    }

    private void removeMetadata(UUID player, String key) {
        metadataService.delete(player, key)
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING, "Failed to remove metadata " + key + " for " + player, ex);
                    return null;
                });
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
