package wiki.creeper.superiorskyblockIntegeration.client;

import org.bukkit.entity.Player;

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
