package wiki.creeper.superiorskyblockIntegeration.api;

import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import wiki.creeper.superiorskyblockIntegeration.common.Operations;
import wiki.creeper.superiorskyblockIntegeration.common.quest.QuestType;

/**
 * Public contract that allows other plugins to invoke network-wide SuperiorSkyblock operations
 * from client servers.
 */
public interface NetworkSkyblockService {

    CompletableFuture<NetworkOperationResult> invite(Player actor, String targetName);

    CompletableFuture<NetworkOperationResult> acceptInvite(Player actor, String inviteId);

    CompletableFuture<NetworkOperationResult> denyInvite(Player actor, String inviteId);

    CompletableFuture<NetworkOperationResult> pendingInvites(Player actor);

    CompletableFuture<NetworkOperationResult> listMembers(Player actor, String islandId);

    CompletableFuture<NetworkOperationResult> islandInfo(Player actor, String ownerIdentifier);

    CompletableFuture<NetworkOperationResult> executeRaw(Operations operation,
                                                         Player actor,
                                                         NetworkPayloadCustomizer customizer);

    CompletableFuture<NetworkOperationResult> putData(Player actor,
                                                      String namespace,
                                                      String key,
                                                      String value,
                                                      long ttlSeconds);

    CompletableFuture<NetworkOperationResult> getData(Player actor,
                                                      String namespace,
                                                      String key);

    CompletableFuture<NetworkOperationResult> deleteData(Player actor,
                                                         String namespace,
                                                         String key);

    CompletableFuture<NetworkOperationResult> registerPlayerProfile(Player actor);

    CompletableFuture<NetworkOperationResult> lookupPlayerProfile(Player actor, String query);

    CompletableFuture<NetworkOperationResult> lookupPlayerIsland(Player actor, String playerUuid);

    CompletableFuture<NetworkOperationResult> questState(Player actor);

    CompletableFuture<NetworkOperationResult> questAssign(Player actor,
                                                          QuestType type,
                                                          int questCount);

    CompletableFuture<NetworkOperationResult> questProgress(Player actor,
                                                            QuestType type,
                                                            int questId,
                                                            int amount,
                                                            String contributorUuid);

    default CompletableFuture<NetworkOperationResult> farmRankingTop(Player actor) {
        return farmRankingTop(actor, 10);
    }

    CompletableFuture<NetworkOperationResult> farmRankingTop(Player actor, int limit);

    default CompletableFuture<NetworkOperationResult> farmRankingMembers(Player actor, String islandId) {
        return farmRankingMembers(actor, islandId, 25);
    }

    CompletableFuture<NetworkOperationResult> farmRankingMembers(Player actor, String islandId, int limit);

    CompletableFuture<NetworkOperationResult> farmRankingIncrement(Player actor,
                                                                   UUID islandUuid,
                                                                   long totalIncrement,
                                                                   long dailyIncrement,
                                                                   long weeklyIncrement,
                                                                   UUID contributorUuid);

    CompletableFuture<NetworkOperationResult> farmRankingSnapshot(Player actor, String periodId, String displayName, int limit);

    CompletableFuture<NetworkOperationResult> farmHistoryList(Player actor, int page, int pageSize);

    CompletableFuture<NetworkOperationResult> farmHistoryDetail(Player actor, String periodId);

    CompletableFuture<NetworkOperationResult> getWorldBorderState(Player actor);

    CompletableFuture<NetworkOperationResult> toggleWorldBorder(Player actor);

    CompletableFuture<NetworkOperationResult> setWorldBorderColor(Player actor, String color);

    CompletableFuture<NetworkOperationResult> farmRewardTable(Player actor);

    CompletableFuture<NetworkOperationResult> farmShopTable(Player actor);

    CompletableFuture<NetworkOperationResult> disbandIsland(Player actor);
}
