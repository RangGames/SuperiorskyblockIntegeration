package wiki.creeper.superiorskyblockIntegeration.api;

import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

import wiki.creeper.superiorskyblockIntegeration.common.Operations;

/**
 * Public contract that allows other plugins to invoke network-wide SuperiorSkyblock operations
 * from client servers.
 */
public interface NetworkSkyblockService {

    CompletableFuture<NetworkOperationResult> invite(Player actor, String targetName);

    CompletableFuture<NetworkOperationResult> acceptInvite(Player actor, String inviteId);

    CompletableFuture<NetworkOperationResult> denyInvite(Player actor, String inviteId);

    CompletableFuture<NetworkOperationResult> listMembers(Player actor, String islandId);

    CompletableFuture<NetworkOperationResult> islandInfo(Player actor, String ownerIdentifier);

    CompletableFuture<NetworkOperationResult> executeRaw(Operations operation,
                                                         Player actor,
                                                         NetworkPayloadCustomizer customizer);
}
