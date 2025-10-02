package wiki.creeper.superiorskyblockIntegeration.gateway.bridge;

import com.google.gson.JsonObject;

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
    public GatewayResponse getIslandInfo(UUID requester, Optional<String> ownerIdentifier, JsonObject payload) {
        return notAvailable();
    }

    @Override
    public GatewayResponse listMembers(UUID requester, Optional<String> islandIdentifier, JsonObject payload) {
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

    private GatewayResponse notAvailable() {
        return GatewayResponse.error(ErrorCode.INTERNAL.code(), "SuperiorSkyblock2 API not available", true);
    }
}
