package wiki.creeper.superiorskyblockIntegeration.api;

import com.google.gson.JsonArray;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
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

    CompletableFuture<NetworkOperationResult> kickMember(Player actor,
                                                         String targetIdentifier,
                                                         String reason);

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

    CompletableFuture<NetworkOperationResult> farmPoints(Player actor);

    CompletableFuture<NetworkOperationResult> farmHopperState(Player actor);

    CompletableFuture<NetworkOperationResult> farmRateIsland(Player actor, int rating);

    CompletableFuture<NetworkOperationResult> listHomeWarps(Player actor);

    CompletableFuture<NetworkOperationResult> createHomeWarp(Player actor, String name, Location location);

    CompletableFuture<NetworkOperationResult> deleteHomeWarp(Player actor, String warpName);

    CompletableFuture<NetworkOperationResult> renameHomeWarp(Player actor, String warpName, String newName);

    CompletableFuture<NetworkOperationResult> toggleHomeWarpPrivacy(Player actor, String warpName);

    CompletableFuture<NetworkOperationResult> listPlayerWarps(Player actor, String targetIdentifier);

    CompletableFuture<NetworkOperationResult> listGlobalWarps(Player actor, int page, int pageSize);

    CompletableFuture<NetworkOperationResult> visitWarp(Player actor, String islandId, String warpName);

    CompletableFuture<NetworkOperationResult> listIslandRules(Player actor);

    CompletableFuture<NetworkOperationResult> addIslandRule(Player actor, String ruleText);

    CompletableFuture<NetworkOperationResult> removeIslandRule(Player actor, int index);

    CompletableFuture<NetworkOperationResult> listCoopPlayers(Player actor);

    CompletableFuture<NetworkOperationResult> addCoopPlayer(Player actor, String targetIdentifier);

    CompletableFuture<NetworkOperationResult> removeCoopPlayer(Player actor, String targetIdentifier);

    CompletableFuture<NetworkOperationResult> listBannedPlayers(Player actor);

    CompletableFuture<NetworkOperationResult> addBannedPlayer(Player actor, String targetIdentifier);

    CompletableFuture<NetworkOperationResult> removeBannedPlayer(Player actor, String targetIdentifier);

    CompletableFuture<NetworkOperationResult> farmChat(Player actor, String message);

    CompletableFuture<NetworkOperationResult> rolePermissions(Player actor);

    CompletableFuture<NetworkOperationResult> updateRolePermission(Player actor,
                                                                   String roleName,
                                                                   String privilege,
                                                                   boolean enabled);

    CompletableFuture<NetworkOperationResult> bankState(Player actor);

    CompletableFuture<NetworkOperationResult> bankDeposit(Player actor, BigDecimal amount);

    CompletableFuture<NetworkOperationResult> bankWithdraw(Player actor, BigDecimal amount);

    default CompletableFuture<NetworkOperationResult> bankHistory(Player actor, int page) {
        return bankHistory(actor, page, 10);
    }

    CompletableFuture<NetworkOperationResult> bankHistory(Player actor, int page, int pageSize);

    CompletableFuture<NetworkOperationResult> bankSetLock(Player actor, boolean locked);

    CompletableFuture<NetworkOperationResult> adminResetPermissions(Player actor, String targetIdentifier);

    CompletableFuture<NetworkOperationResult> adminLookupIslandUuid(Player actor, String targetIdentifier);

    CompletableFuture<NetworkOperationResult> adminLookupIslandOwner(Player actor, String islandUuid);

    CompletableFuture<NetworkOperationResult> adminToggleGambling(Player actor, String targetIdentifier);

    CompletableFuture<NetworkOperationResult> adminLoadPowerRewards(Player actor, int tier);

    CompletableFuture<NetworkOperationResult> adminSavePowerRewards(Player actor, int tier, JsonArray items);

    CompletableFuture<NetworkOperationResult> adminLoadTopRewards(Player actor, int rank);

    CompletableFuture<NetworkOperationResult> adminSaveTopRewards(Player actor, int rank, JsonArray items);

    CompletableFuture<NetworkOperationResult> adminGiveTopRewards(Player actor, int rank);

    CompletableFuture<NetworkOperationResult> disbandIsland(Player actor);
}
