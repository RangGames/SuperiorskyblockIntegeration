package wiki.creeper.superiorskyblockIntegeration.gateway;

import com.google.gson.JsonObject;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.Jedis;

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

import wiki.creeper.superiorskyblockIntegeration.common.Operations;
import wiki.creeper.superiorskyblockIntegeration.common.errors.ErrorCode;
import wiki.creeper.superiorskyblockIntegeration.config.PluginConfig;
import wiki.creeper.superiorskyblockIntegeration.gateway.cache.PlayerIslandCache;
import wiki.creeper.superiorskyblockIntegeration.gateway.idempotency.IdempotencyKeyBuilder;
import wiki.creeper.superiorskyblockIntegeration.gateway.idempotency.IdempotencyService;
import wiki.creeper.superiorskyblockIntegeration.gateway.bridge.SuperiorSkyblockBridge;
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

    GatewayRequestRouter(JavaPlugin plugin,
                         RedisManager redisManager,
                         RedisChannels channels,
                         MessageSecurity security,
                         IdempotencyService idempotency,
                         PluginConfig config,
                         SuperiorSkyblockBridge bridge,
                         GatewayEventPublisher events,
                         PlayerIslandCache islandCache) {
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
    }

    void runSubscription(GatewaySubscriber subscriber, String pattern) {
        try (Jedis jedis = redisManager.pool().getResource()) {
            jedis.psubscribe(subscriber, pattern);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Gateway subscription loop terminated", ex);
        }
    }

    void handle(String channel, RedisMessage request) {
        Optional<Operations> operation = Operations.from(request.op());
        if (operation.isEmpty()) {
            plugin.getLogger().warning("Unknown operation received on " + channel + ": " + request.op());
            publishError(request, "UNKNOWN_OPERATION", "Unsupported operation: " + request.op(), false);
            return;
        }

        GatewayResponse response = switch (operation.get()) {
            case INVITE_CREATE -> handleInviteCreate(request);
            case INVITE_ACCEPT -> handleInviteAccept(request);
            case INVITE_DENY -> handleInviteDeny(request);
            case ISLAND_GET -> handleIslandGet(request);
            case MEMBERS_LIST -> handleMembersList(request);
        };
        publishResponse(request, response);
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
            }
        });
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

    private void publishResponse(RedisMessage request, GatewayResponse response) {
        RedisMessage outgoing = RedisMessage.responseFor(request);
        outgoing.setOk(response.ok());
        if (response.ok()) {
            outgoing.setData(response.data());
        } else if (response.error() != null) {
            outgoing.setError(response.error().code(), response.error().message(), response.error().retryable());
        }
        security.sign(outgoing);
        String responseChannel = channels.responseChannel(request.id());
        redisManager.publish(responseChannel, outgoing.toJson());
    }

    private void publishError(RedisMessage request, String code, String message, boolean retryable) {
        GatewayResponse error = GatewayResponse.error(code, message, retryable);
        publishResponse(request, error);
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
                islandCache.setMembership(targetPlayer, UUID.fromString(rawIsland));
                return;
            } catch (IllegalArgumentException ignored) {
                // fall through to refresh
            }
        }
        islandCache.refresh(targetPlayer);
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
}
