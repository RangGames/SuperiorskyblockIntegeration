package wiki.creeper.superiorskyblockIntegeration.gateway.bridge;

import com.google.gson.JsonObject;

import java.math.BigDecimal;
import org.bukkit.Location;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import wiki.creeper.superiorskyblockIntegeration.gateway.GatewayResponse;

/**
 * Contract for interacting with SuperiorSkyblock2 from the gateway server.
 */
public interface SuperiorSkyblockBridge {

    boolean isAvailable();

    GatewayResponse createInvite(UUID actor, String targetIdentifier, JsonObject payload);

    GatewayResponse acceptInvite(UUID actor, String inviteId, JsonObject payload);

    GatewayResponse denyInvite(UUID actor, String inviteId, JsonObject payload);

    GatewayResponse listPendingInvites(UUID actor, JsonObject payload);

    GatewayResponse getIslandInfo(UUID requester, Optional<String> ownerIdentifier, JsonObject payload);

    GatewayResponse listMembers(UUID requester, Optional<String> islandIdentifier, JsonObject payload);

    GatewayResponse kickMember(UUID actorUuid, UUID targetUuid, String reason, JsonObject payload);

    Optional<UUID> islandIdForPlayer(UUID playerUuid);

    Map<UUID, UUID> snapshotPlayerIslands();

    boolean canManageIslandQuests(UUID playerUuid);

    int memberCount(UUID islandUuid);

    IslandDetails describeIsland(UUID islandUuid);

    java.util.Optional<String> lookupPlayerName(String uuid);

    GatewayResponse toggleWorldBorder(UUID playerUuid);

    GatewayResponse setBorderColor(UUID playerUuid, String color);

    GatewayResponse borderState(UUID playerUuid);

    void broadcastIslandMessage(UUID islandUuid, java.util.List<String> messages);

    GatewayResponse disbandIsland(UUID actorUuid);

    GatewayResponse bankState(UUID actorUuid);

    GatewayResponse bankDeposit(UUID actorUuid, BigDecimal amount);

    GatewayResponse bankWithdraw(UUID actorUuid, BigDecimal amount);

    GatewayResponse bankHistory(UUID actorUuid, int page, int pageSize);

    GatewayResponse hopperState(UUID actorUuid);

    GatewayResponse updateIslandRating(UUID actorUuid, int rating);

    GatewayResponse listHomeWarps(UUID actorUuid);

    GatewayResponse createHomeWarp(UUID actorUuid, String name, Location location);

    GatewayResponse deleteHomeWarp(UUID actorUuid, String warpName);

    GatewayResponse renameHomeWarp(UUID actorUuid, String warpName, String newName);

    GatewayResponse toggleHomeWarpPrivacy(UUID actorUuid, String warpName);

    GatewayResponse listPlayerWarps(UUID actorUuid, String targetIdentifier);

    GatewayResponse listGlobalWarps(UUID actorUuid, int page, int pageSize);

    GatewayResponse visitWarp(UUID actorUuid, String islandIdentifier, String warpName);

    GatewayResponse listRolePermissions(UUID actorUuid);

    GatewayResponse updateRolePermission(UUID actorUuid, String roleName, String privilegeName, boolean enabled);

    GatewayResponse listCoopPlayers(UUID actorUuid);

    GatewayResponse addCoopPlayer(UUID actorUuid, String targetIdentifier);

    GatewayResponse removeCoopPlayer(UUID actorUuid, String targetIdentifier);

    GatewayResponse listBannedPlayers(UUID actorUuid);

    GatewayResponse addBannedPlayer(UUID actorUuid, String targetIdentifier);

    GatewayResponse removeBannedPlayer(UUID actorUuid, String targetIdentifier);

    GatewayResponse adminResetPermissions(UUID actorUuid, String playerIdentifier);

    GatewayResponse adminLookupIslandUuid(String playerIdentifier);

    GatewayResponse adminLookupIslandOwner(String islandIdentifier);

    record IslandDetails(UUID islandUuid, String name, UUID ownerUuid, String ownerName) {
    }
}
