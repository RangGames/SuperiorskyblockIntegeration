package wiki.creeper.superiorskyblockIntegeration.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import wiki.creeper.superiorskyblockIntegeration.api.NetworkOperationResult;
import wiki.creeper.superiorskyblockIntegeration.api.NetworkPayloadCustomizer;
import wiki.creeper.superiorskyblockIntegeration.api.NetworkSkyblockService;
import wiki.creeper.superiorskyblockIntegeration.client.messaging.ClientRequestDispatcher;
import wiki.creeper.superiorskyblockIntegeration.common.Operations;
import wiki.creeper.superiorskyblockIntegeration.common.quest.QuestType;
import wiki.creeper.superiorskyblockIntegeration.redis.RedisMessage;

public final class ClientNetworkService implements NetworkSkyblockService {

    private final ClientRequestDispatcher dispatcher;

    public ClientNetworkService(ClientRequestDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public CompletableFuture<NetworkOperationResult> invite(Player actor, String targetName) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(targetName, "targetName");
        return execute(Operations.INVITE_CREATE, actor, message -> message.data().addProperty("target", targetName));
    }

    @Override
    public CompletableFuture<NetworkOperationResult> acceptInvite(Player actor, String inviteId) {
        Objects.requireNonNull(actor, "actor");
        return execute(Operations.INVITE_ACCEPT, actor, message -> {
            if (inviteId != null && !inviteId.isBlank()) {
                message.data().addProperty("inviteId", inviteId);
            }
        });
    }

    @Override
    public CompletableFuture<NetworkOperationResult> denyInvite(Player actor, String inviteId) {
        Objects.requireNonNull(actor, "actor");
        return execute(Operations.INVITE_DENY, actor, message -> {
            if (inviteId != null && !inviteId.isBlank()) {
                message.data().addProperty("inviteId", inviteId);
            }
        });
    }

    @Override
    public CompletableFuture<NetworkOperationResult> pendingInvites(Player actor) {
        Objects.requireNonNull(actor, "actor");
        return execute(Operations.INVITES_PENDING, actor, null);
    }

    @Override
    public CompletableFuture<NetworkOperationResult> listMembers(Player actor, String islandId) {
        return execute(Operations.MEMBERS_LIST, actor, message -> {
            if (islandId != null && !islandId.isBlank()) {
                message.data().addProperty("islandId", islandId);
            }
        });
    }

    @Override
    public CompletableFuture<NetworkOperationResult> kickMember(Player actor,
                                                                String targetIdentifier,
                                                                String reason) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(targetIdentifier, "targetIdentifier");
        return execute(Operations.MEMBERS_KICK, actor, message -> {
            message.data().addProperty("target", targetIdentifier);
            if (reason != null && !reason.isBlank()) {
                message.data().addProperty("reason", reason);
            }
        });
    }

    @Override
    public CompletableFuture<NetworkOperationResult> islandInfo(Player actor, String ownerIdentifier) {
        return execute(Operations.ISLAND_GET, actor, message -> {
            if (ownerIdentifier != null && !ownerIdentifier.isBlank()) {
                message.data().addProperty("owner", ownerIdentifier);
            }
        });
    }

    @Override
    public CompletableFuture<NetworkOperationResult> executeRaw(Operations operation,
                                                                 Player actor,
                                                                 NetworkPayloadCustomizer customizer) {
        Objects.requireNonNull(operation, "operation");
        return execute(operation, actor, message -> {
            if (customizer != null) {
                customizer.apply(message);
            }
        });
    }

    @Override
    public CompletableFuture<NetworkOperationResult> putData(Player actor,
                                                             String namespace,
                                                             String key,
                                                             String value,
                                                             long ttlSeconds) {
        return execute(Operations.DATA_PUT, actor, message -> {
            message.data().addProperty("namespace", namespace);
            message.data().addProperty("key", key);
            message.data().addProperty("value", value);
            message.data().addProperty("ttl", ttlSeconds);
        });
    }

    @Override
    public CompletableFuture<NetworkOperationResult> getData(Player actor,
                                                             String namespace,
                                                             String key) {
        return execute(Operations.DATA_GET, actor, message -> {
            message.data().addProperty("namespace", namespace);
            message.data().addProperty("key", key);
        });
    }

    @Override
    public CompletableFuture<NetworkOperationResult> deleteData(Player actor,
                                                                String namespace,
                                                                String key) {
        return execute(Operations.DATA_DELETE, actor, message -> {
            message.data().addProperty("namespace", namespace);
            message.data().addProperty("key", key);
        });
    }

    @Override
    public CompletableFuture<NetworkOperationResult> registerPlayerProfile(Player actor) {
        Objects.requireNonNull(actor, "actor");
        return execute(Operations.PLAYER_PROFILE_REGISTER, actor, message -> {
            message.data().addProperty("playerUuid", actor.getUniqueId().toString());
            message.data().addProperty("playerName", actor.getName());
            message.data().addProperty("lastSeen", System.currentTimeMillis());
        });
    }

    @Override
    public CompletableFuture<NetworkOperationResult> lookupPlayerProfile(Player actor, String query) {
        return execute(Operations.PLAYER_PROFILE_LOOKUP, actor, message -> {
            message.data().addProperty("query", query);
        });
    }

    @Override
    public CompletableFuture<NetworkOperationResult> lookupPlayerIsland(Player actor, String playerUuid) {
        return execute(Operations.PLAYER_ISLAND_LOOKUP, actor, message -> {
            if (playerUuid != null && !playerUuid.isBlank()) {
                message.data().addProperty("playerUuid", playerUuid);
            }
        });
    }

    @Override
    public CompletableFuture<NetworkOperationResult> questState(Player actor) {
        return execute(Operations.QUEST_STATE, actor, null);
    }

    @Override
    public CompletableFuture<NetworkOperationResult> questAssign(Player actor,
                                                                 QuestType type,
                                                                 int questCount) {
        return execute(Operations.QUEST_ASSIGN, actor, message -> {
            if (type != null) {
                message.data().addProperty("type", type.name());
            }
            message.data().addProperty("questCount", questCount);
        });
    }

    @Override
    public CompletableFuture<NetworkOperationResult> questProgress(Player actor,
                                                                    QuestType type,
                                                                    int questId,
                                                                    int amount,
                                                                    String contributorUuid) {
        return execute(Operations.QUEST_PROGRESS, actor, message -> {
            if (type != null) {
                message.data().addProperty("type", type.name());
            }
            message.data().addProperty("questId", questId);
            message.data().addProperty("amount", amount);
            if (contributorUuid != null && !contributorUuid.isBlank()) {
                message.data().addProperty("contributor", contributorUuid);
            }
        });
    }

    @Override
    public CompletableFuture<NetworkOperationResult> farmRankingTop(Player actor, int limit) {
        return execute(Operations.FARM_RANKING_TOP, actor, message ->
                message.data().addProperty("limit", Math.max(1, limit)));
    }

    @Override
    public CompletableFuture<NetworkOperationResult> farmRankingMembers(Player actor, String islandId, int limit) {
        return execute(Operations.FARM_RANKING_MEMBERS, actor, message -> {
            if (islandId != null && !islandId.isBlank()) {
                message.data().addProperty("islandId", islandId);
            }
            message.data().addProperty("limit", Math.max(1, limit));
        });
    }

    @Override
    public CompletableFuture<NetworkOperationResult> farmRankingIncrement(Player actor,
                                                                          UUID islandUuid,
                                                                          long totalIncrement,
                                                                          long dailyIncrement,
                                                                          long weeklyIncrement,
                                                                          UUID contributorUuid) {
        Objects.requireNonNull(islandUuid, "islandUuid");
        if (totalIncrement < 0 || dailyIncrement < 0 || weeklyIncrement < 0) {
            throw new IllegalArgumentException("Increment values must be non-negative");
        }
        return execute(Operations.FARM_RANKING_INCREMENT, actor, message -> {
            message.data().addProperty("islandId", islandUuid.toString());
            message.data().addProperty("total", totalIncrement);
            message.data().addProperty("daily", dailyIncrement);
            message.data().addProperty("weekly", weeklyIncrement);
            if (contributorUuid != null) {
                message.data().addProperty("contributorUuid", contributorUuid.toString());
            }
        });
    }

    @Override
    public CompletableFuture<NetworkOperationResult> farmRankingSnapshot(Player actor,
                                                                         String periodId,
                                                                         String displayName,
                                                                         int limit) {
        return execute(Operations.FARM_RANKING_SNAPSHOT, actor, message -> {
            message.data().addProperty("periodId", periodId);
            if (displayName != null) {
                message.data().addProperty("displayName", displayName);
            }
            message.data().addProperty("limit", limit);
        });
    }

    @Override
    public CompletableFuture<NetworkOperationResult> farmHistoryList(Player actor, int page, int pageSize) {
        return execute(Operations.FARM_HISTORY_LIST, actor, message -> {
            message.data().addProperty("page", Math.max(1, page));
            message.data().addProperty("pageSize", Math.max(1, pageSize));
        });
    }

    @Override
    public CompletableFuture<NetworkOperationResult> farmHistoryDetail(Player actor, String periodId) {
        return execute(Operations.FARM_HISTORY_DETAIL, actor, message -> {
            message.data().addProperty("periodId", periodId);
        });
    }

    @Override
    public CompletableFuture<NetworkOperationResult> getWorldBorderState(Player actor) {
        return execute(Operations.FARM_BORDER_STATE, actor, null);
    }

    @Override
    public CompletableFuture<NetworkOperationResult> toggleWorldBorder(Player actor) {
        return execute(Operations.FARM_BORDER_TOGGLE, actor, null);
    }

    @Override
    public CompletableFuture<NetworkOperationResult> setWorldBorderColor(Player actor, String color) {
        return execute(Operations.FARM_BORDER_COLOR, actor, message -> {
            if (color != null && !color.isBlank()) {
                message.data().addProperty("color", color);
            }
        });
    }

    @Override
    public CompletableFuture<NetworkOperationResult> farmRewardTable(Player actor) {
        return execute(Operations.FARM_REWARD_TABLE, actor, null);
    }

    @Override
    public CompletableFuture<NetworkOperationResult> farmShopTable(Player actor) {
        return execute(Operations.FARM_SHOP_TABLE, actor, null);
    }

    @Override
    public CompletableFuture<NetworkOperationResult> farmPoints(Player actor) {
        Objects.requireNonNull(actor, "actor");
        return execute(Operations.FARM_POINTS_INFO, actor, null);
    }

    @Override
    public CompletableFuture<NetworkOperationResult> farmHopperState(Player actor) {
        Objects.requireNonNull(actor, "actor");
        return execute(Operations.FARM_HOPPER_INFO, actor, null);
    }

    @Override
    public CompletableFuture<NetworkOperationResult> farmRateIsland(Player actor, int rating) {
        Objects.requireNonNull(actor, "actor");
        if (rating < 0 || rating > 5) {
            throw new IllegalArgumentException("rating must be between 0 and 5");
        }
        return execute(Operations.FARM_RATING_UPDATE, actor,
                message -> message.data().addProperty("rating", rating));
    }

    @Override
    public CompletableFuture<NetworkOperationResult> listHomeWarps(Player actor) {
        Objects.requireNonNull(actor, "actor");
        return execute(Operations.FARM_WARP_HOME_LIST, actor, null);
    }

    @Override
    public CompletableFuture<NetworkOperationResult> createHomeWarp(Player actor, String name, Location location) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return execute(Operations.FARM_WARP_HOME_SET, actor, message -> {
            JsonObject data = message.data();
            data.addProperty("name", name);
            writeLocation(data, location);
        });
    }

    @Override
    public CompletableFuture<NetworkOperationResult> deleteHomeWarp(Player actor, String warpName) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(warpName, "warpName");
        if (warpName.isBlank()) {
            throw new IllegalArgumentException("warpName must not be blank");
        }
        return execute(Operations.FARM_WARP_HOME_DELETE, actor,
                message -> message.data().addProperty("warp", warpName));
    }

    @Override
    public CompletableFuture<NetworkOperationResult> renameHomeWarp(Player actor, String warpName, String newName) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(warpName, "warpName");
        Objects.requireNonNull(newName, "newName");
        if (warpName.isBlank() || newName.isBlank()) {
            throw new IllegalArgumentException("warp names must not be blank");
        }
        return execute(Operations.FARM_WARP_HOME_RENAME, actor, message -> {
            JsonObject data = message.data();
            data.addProperty("warp", warpName);
            data.addProperty("name", newName);
        });
    }

    @Override
    public CompletableFuture<NetworkOperationResult> toggleHomeWarpPrivacy(Player actor, String warpName) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(warpName, "warpName");
        if (warpName.isBlank()) {
            throw new IllegalArgumentException("warpName must not be blank");
        }
        return execute(Operations.FARM_WARP_HOME_TOGGLE, actor,
                message -> message.data().addProperty("warp", warpName));
    }

    @Override
    public CompletableFuture<NetworkOperationResult> listPlayerWarps(Player actor, String targetIdentifier) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(targetIdentifier, "targetIdentifier");
        return execute(Operations.FARM_WARP_PLAYER_LIST, actor,
                message -> message.data().addProperty("target", targetIdentifier));
    }

    @Override
    public CompletableFuture<NetworkOperationResult> listGlobalWarps(Player actor, int page, int pageSize) {
        Objects.requireNonNull(actor, "actor");
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, pageSize);
        return execute(Operations.FARM_WARP_GLOBAL_LIST, actor, message -> {
            message.data().addProperty("page", safePage);
            message.data().addProperty("pageSize", safeSize);
        });
    }

    @Override
    public CompletableFuture<NetworkOperationResult> visitWarp(Player actor, String islandId, String warpName) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(islandId, "islandId");
        Objects.requireNonNull(warpName, "warpName");
        if (warpName.isBlank()) {
            throw new IllegalArgumentException("warpName must not be blank");
        }
        return execute(Operations.FARM_WARP_VISIT, actor, message -> {
            JsonObject data = message.data();
            data.addProperty("islandId", islandId);
            data.addProperty("warp", warpName);
        });
    }

    @Override
    public CompletableFuture<NetworkOperationResult> listIslandRules(Player actor) {
        Objects.requireNonNull(actor, "actor");
        return execute(Operations.FARM_RULE_LIST, actor, null);
    }

    @Override
    public CompletableFuture<NetworkOperationResult> addIslandRule(Player actor, String ruleText) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(ruleText, "ruleText");
        if (ruleText.isBlank()) {
            throw new IllegalArgumentException("rule must not be blank");
        }
        return execute(Operations.FARM_RULE_ADD, actor,
                message -> message.data().addProperty("rule", ruleText));
    }

    @Override
    public CompletableFuture<NetworkOperationResult> removeIslandRule(Player actor, int index) {
        Objects.requireNonNull(actor, "actor");
        if (index <= 0) {
            throw new IllegalArgumentException("index must be positive");
        }
        return execute(Operations.FARM_RULE_REMOVE, actor,
                message -> message.data().addProperty("index", index));
    }

    @Override
    public CompletableFuture<NetworkOperationResult> rolePermissions(Player actor) {
        Objects.requireNonNull(actor, "actor");
        return execute(Operations.ROLE_PERMISSIONS_LIST, actor, null);
    }

    @Override
    public CompletableFuture<NetworkOperationResult> updateRolePermission(Player actor,
                                                                          String roleName,
                                                                          String privilege,
                                                                          boolean enabled) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(roleName, "roleName");
        Objects.requireNonNull(privilege, "privilege");
        return execute(Operations.ROLE_PERMISSIONS_UPDATE, actor, message -> {
            message.data().addProperty("role", roleName);
            message.data().addProperty("privilege", privilege);
            message.data().addProperty("enabled", enabled);
        });
    }

    @Override
    public CompletableFuture<NetworkOperationResult> listCoopPlayers(Player actor) {
        Objects.requireNonNull(actor, "actor");
        return execute(Operations.FARM_COOP_LIST, actor, null);
    }

    @Override
    public CompletableFuture<NetworkOperationResult> addCoopPlayer(Player actor, String targetIdentifier) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(targetIdentifier, "targetIdentifier");
        return execute(Operations.FARM_COOP_ADD, actor,
                message -> message.data().addProperty("target", targetIdentifier));
    }

    @Override
    public CompletableFuture<NetworkOperationResult> removeCoopPlayer(Player actor, String targetIdentifier) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(targetIdentifier, "targetIdentifier");
        return execute(Operations.FARM_COOP_REMOVE, actor,
                message -> message.data().addProperty("target", targetIdentifier));
    }

    @Override
    public CompletableFuture<NetworkOperationResult> listBannedPlayers(Player actor) {
        Objects.requireNonNull(actor, "actor");
        return execute(Operations.FARM_BAN_LIST, actor, null);
    }

    @Override
    public CompletableFuture<NetworkOperationResult> addBannedPlayer(Player actor, String targetIdentifier) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(targetIdentifier, "targetIdentifier");
        return execute(Operations.FARM_BAN_ADD, actor,
                message -> message.data().addProperty("target", targetIdentifier));
    }

    @Override
    public CompletableFuture<NetworkOperationResult> removeBannedPlayer(Player actor, String targetIdentifier) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(targetIdentifier, "targetIdentifier");
        return execute(Operations.FARM_BAN_REMOVE, actor,
                message -> message.data().addProperty("target", targetIdentifier));
    }

    @Override
    public CompletableFuture<NetworkOperationResult> farmChat(Player actor, String message) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(message, "message");
        return execute(Operations.FARM_CHAT_SEND, actor,
                payload -> payload.data().addProperty("message", message));
    }

    private void writeLocation(JsonObject data, Location location) {
        if (location.getWorld() == null) {
            throw new IllegalArgumentException("location world must not be null");
        }
        data.addProperty("world", location.getWorld().getName());
        data.addProperty("x", location.getX());
        data.addProperty("y", location.getY());
        data.addProperty("z", location.getZ());
        data.addProperty("yaw", location.getYaw());
        data.addProperty("pitch", location.getPitch());
    }

    @Override
    public CompletableFuture<NetworkOperationResult> bankState(Player actor) {
        Objects.requireNonNull(actor, "actor");
        return execute(Operations.BANK_STATE, actor, null);
    }

    @Override
    public CompletableFuture<NetworkOperationResult> bankDeposit(Player actor, BigDecimal amount) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        return execute(Operations.BANK_DEPOSIT, actor,
                message -> message.data().addProperty("amount", amount.toPlainString()));
    }

    @Override
    public CompletableFuture<NetworkOperationResult> bankWithdraw(Player actor, BigDecimal amount) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        return execute(Operations.BANK_WITHDRAW, actor,
                message -> message.data().addProperty("amount", amount.toPlainString()));
    }

    @Override
    public CompletableFuture<NetworkOperationResult> bankHistory(Player actor, int page, int pageSize) {
        Objects.requireNonNull(actor, "actor");
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, pageSize);
        return execute(Operations.BANK_HISTORY, actor, message -> {
            message.data().addProperty("page", safePage);
            message.data().addProperty("pageSize", safeSize);
        });
    }

    @Override
    public CompletableFuture<NetworkOperationResult> bankSetLock(Player actor, boolean locked) {
        Objects.requireNonNull(actor, "actor");
        return execute(Operations.BANK_LOCK_SET, actor,
                message -> message.data().addProperty("locked", locked));
    }

    @Override
    public CompletableFuture<NetworkOperationResult> adminResetPermissions(Player actor, String targetIdentifier) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(targetIdentifier, "targetIdentifier");
        return execute(Operations.ADMIN_RESET_PERMISSIONS, actor,
                message -> message.data().addProperty("target", targetIdentifier));
    }

    @Override
    public CompletableFuture<NetworkOperationResult> adminLookupIslandUuid(Player actor, String targetIdentifier) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(targetIdentifier, "targetIdentifier");
        return execute(Operations.ADMIN_LOOKUP_ISLAND_UUID, actor,
                message -> message.data().addProperty("target", targetIdentifier));
    }

    @Override
    public CompletableFuture<NetworkOperationResult> adminLookupIslandOwner(Player actor, String islandUuid) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(islandUuid, "islandUuid");
        return execute(Operations.ADMIN_LOOKUP_ISLAND_OWNER, actor,
                message -> message.data().addProperty("islandId", islandUuid));
    }

    @Override
    public CompletableFuture<NetworkOperationResult> adminToggleGambling(Player actor, String targetIdentifier) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(targetIdentifier, "targetIdentifier");
        return execute(Operations.ADMIN_TOGGLE_GAMBLING, actor,
                message -> message.data().addProperty("target", targetIdentifier));
    }

    @Override
    public CompletableFuture<NetworkOperationResult> adminLoadPowerRewards(Player actor, int tier) {
        Objects.requireNonNull(actor, "actor");
        return execute(Operations.ADMIN_LOAD_POWER_REWARD, actor,
                message -> message.data().addProperty("tier", tier));
    }

    @Override
    public CompletableFuture<NetworkOperationResult> adminSavePowerRewards(Player actor,
                                                                           int tier,
                                                                           JsonArray items) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(items, "items");
        return execute(Operations.ADMIN_SAVE_POWER_REWARD, actor, message -> {
            message.data().addProperty("tier", tier);
            message.data().add("items", items.deepCopy());
        });
    }

    @Override
    public CompletableFuture<NetworkOperationResult> adminLoadTopRewards(Player actor, int rank) {
        Objects.requireNonNull(actor, "actor");
        return execute(Operations.ADMIN_LOAD_TOP_REWARD, actor,
                message -> message.data().addProperty("rank", rank));
    }

    @Override
    public CompletableFuture<NetworkOperationResult> adminSaveTopRewards(Player actor,
                                                                         int rank,
                                                                         JsonArray items) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(items, "items");
        return execute(Operations.ADMIN_SAVE_TOP_REWARD, actor, message -> {
            message.data().addProperty("rank", rank);
            message.data().add("items", items.deepCopy());
        });
    }

    @Override
    public CompletableFuture<NetworkOperationResult> adminGiveTopRewards(Player actor, int rank) {
        Objects.requireNonNull(actor, "actor");
        return execute(Operations.ADMIN_GIVE_TOP_REWARD, actor,
                message -> message.data().addProperty("rank", rank));
    }

    @Override
    public CompletableFuture<NetworkOperationResult> disbandIsland(Player actor) {
        Objects.requireNonNull(actor, "actor");
        return execute(Operations.ISLAND_DISBAND, actor, null);
    }

    private CompletableFuture<NetworkOperationResult> execute(Operations operation,
                                                               Player actor,
                                                               NetworkPayloadCustomizer customizer) {
        try {
            CompletableFuture<RedisMessage> future = dispatcher.send(operation, actor, message -> {
                if (customizer != null) {
                    customizer.apply(message);
                }
            });
            return future.handle((message, throwable) -> {
                if (throwable != null) {
                    Throwable cause = throwable instanceof CompletionException ? throwable.getCause() : throwable;
                    String messageText = cause != null && cause.getMessage() != null ? cause.getMessage() : String.valueOf(cause);
                    return NetworkOperationResult.failure("INTERNAL", messageText, true);
                }
                return NetworkOperationResult.fromMessage(message);
            });
        } catch (Exception ex) {
            CompletableFuture<NetworkOperationResult> failed = new CompletableFuture<>();
            String messageText = ex.getMessage() != null ? ex.getMessage() : ex.toString();
            failed.complete(NetworkOperationResult.failure("INTERNAL", messageText, true));
            return failed;
        }
    }
}
