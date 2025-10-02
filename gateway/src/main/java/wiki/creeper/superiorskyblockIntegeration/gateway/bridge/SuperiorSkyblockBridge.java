package wiki.creeper.superiorskyblockIntegeration.gateway.bridge;

import com.google.gson.JsonObject;

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

    GatewayResponse getIslandInfo(UUID requester, Optional<String> ownerIdentifier, JsonObject payload);

    GatewayResponse listMembers(UUID requester, Optional<String> islandIdentifier, JsonObject payload);

    Optional<UUID> islandIdForPlayer(UUID playerUuid);

    Map<UUID, UUID> snapshotPlayerIslands();
}
