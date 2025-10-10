package wiki.creeper.superiorskyblockIntegeration.gateway.bridge;

import com.google.gson.JsonObject;

import java.math.BigDecimal;
import org.bukkit.Location;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import wiki.creeper.superiorskyblockIntegeration.common.errors.ErrorCode;
import wiki.creeper.superiorskyblockIntegeration.gateway.GatewayResponse;

final class FallbackBridge implements SuperiorSkyblockBridge {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public GatewayResponse createInvite(UUID actor, String targetIdentifier, JsonObject payload) {
        return notAvailable();
    }

    @Override
    public GatewayResponse acceptInvite(UUID actor, String inviteId, JsonObject payload) {
        return notAvailable();
    }

    @Override
    public GatewayResponse denyInvite(UUID actor, String inviteId, JsonObject payload) {
        return notAvailable();
    }

    @Override
    public GatewayResponse listPendingInvites(UUID actor, JsonObject payload) {
        return notAvailable();
    }

    @Override
    public GatewayResponse getIslandInfo(UUID requester, Optional<String> ownerIdentifier, JsonObject payload) {
        return notAvailable();
    }

    @Override
    public GatewayResponse listMembers(UUID requester, Optional<String> islandIdentifier, JsonObject payload) {
        return notAvailable();
    }

    @Override
    public GatewayResponse kickMember(UUID actorUuid, UUID targetUuid, String reason, JsonObject payload) {
        return notAvailable();
    }

    @Override
    public Optional<UUID> islandIdForPlayer(UUID playerUuid) {
        return Optional.empty();
    }

    @Override
    public Map<UUID, UUID> snapshotPlayerIslands() {
        return Map.of();
    }

    @Override
    public boolean canManageIslandQuests(UUID playerUuid) {
        return false;
    }

    @Override
    public int memberCount(UUID islandUuid) {
        return 0;
    }

    @Override
    public IslandDetails describeIsland(UUID islandUuid) {
        return null;
    }

    @Override
    public java.util.Optional<String> lookupPlayerName(String uuid) {
        return java.util.Optional.empty();
    }

    @Override
    public GatewayResponse toggleWorldBorder(UUID playerUuid) {
        return notAvailable();
    }

    @Override
    public GatewayResponse setBorderColor(UUID playerUuid, String color) {
        return notAvailable();
    }

    @Override
    public GatewayResponse borderState(UUID playerUuid) {
        return notAvailable();
    }

    @Override
    public void broadcastIslandMessage(UUID islandUuid, java.util.List<String> messages) {
        // ignored – SuperiorSkyblock is not available
    }

    @Override
    public GatewayResponse disbandIsland(UUID actorUuid) {
        return notAvailable();
    }

    @Override
    public GatewayResponse bankState(UUID actorUuid) {
        return notAvailable();
    }

    @Override
    public GatewayResponse bankDeposit(UUID actorUuid, BigDecimal amount) {
        return notAvailable();
    }

    @Override
    public GatewayResponse bankWithdraw(UUID actorUuid, BigDecimal amount) {
        return notAvailable();
    }

    @Override
    public GatewayResponse bankHistory(UUID actorUuid, int page, int pageSize) {
        return notAvailable();
    }

    @Override
    public GatewayResponse listCoopPlayers(UUID actorUuid) {
        return notAvailable();
    }

    @Override
    public GatewayResponse addCoopPlayer(UUID actorUuid, String targetIdentifier) {
        return notAvailable();
    }

    @Override
    public GatewayResponse removeCoopPlayer(UUID actorUuid, String targetIdentifier) {
        return notAvailable();
    }

    @Override
    public GatewayResponse listBannedPlayers(UUID actorUuid) {
        return notAvailable();
    }

    @Override
    public GatewayResponse addBannedPlayer(UUID actorUuid, String targetIdentifier) {
        return notAvailable();
    }

    @Override
    public GatewayResponse removeBannedPlayer(UUID actorUuid, String targetIdentifier) {
        return notAvailable();
    }

    @Override
    public GatewayResponse hopperState(UUID actorUuid) {
        return notAvailable();
    }

    @Override
    public GatewayResponse updateIslandRating(UUID actorUuid, int rating) {
        return notAvailable();
    }

    @Override
    public GatewayResponse listHomeWarps(UUID actorUuid) {
        return notAvailable();
    }

    @Override
    public GatewayResponse createHomeWarp(UUID actorUuid, String name, Location location) {
        return notAvailable();
    }

    @Override
    public GatewayResponse deleteHomeWarp(UUID actorUuid, String warpName) {
        return notAvailable();
    }

    @Override
    public GatewayResponse renameHomeWarp(UUID actorUuid, String warpName, String newName) {
        return notAvailable();
    }

    @Override
    public GatewayResponse toggleHomeWarpPrivacy(UUID actorUuid, String warpName) {
        return notAvailable();
    }

    @Override
    public GatewayResponse listPlayerWarps(UUID actorUuid, String targetIdentifier) {
        return notAvailable();
    }

    @Override
    public GatewayResponse listGlobalWarps(UUID actorUuid, int page, int pageSize) {
        return notAvailable();
    }

    @Override
    public GatewayResponse visitWarp(UUID actorUuid, String islandIdentifier, String warpName) {
        return notAvailable();
    }

    @Override
    public GatewayResponse listRolePermissions(UUID actorUuid) {
        return notAvailable();
    }

    @Override
    public GatewayResponse updateRolePermission(UUID actorUuid, String roleName, String privilegeName, boolean enabled) {
        return notAvailable();
    }

    @Override
    public GatewayResponse adminResetPermissions(UUID actorUuid, String playerIdentifier) {
        return notAvailable();
    }

    @Override
    public GatewayResponse adminLookupIslandUuid(String playerIdentifier) {
        return notAvailable();
    }

    @Override
    public GatewayResponse adminLookupIslandOwner(String islandIdentifier) {
        return notAvailable();
    }

    private GatewayResponse notAvailable() {
        return GatewayResponse.error(ErrorCode.INTERNAL.code(), "SuperiorSkyblock2 API not available", true);
    }
}
