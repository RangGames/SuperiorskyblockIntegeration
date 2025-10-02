package wiki.creeper.superiorskyblockIntegeration.client;

import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import wiki.creeper.superiorskyblockIntegeration.api.NetworkOperationResult;
import wiki.creeper.superiorskyblockIntegeration.api.NetworkPayloadCustomizer;
import wiki.creeper.superiorskyblockIntegeration.api.NetworkSkyblockService;
import wiki.creeper.superiorskyblockIntegeration.client.messaging.ClientRequestDispatcher;
import wiki.creeper.superiorskyblockIntegeration.common.Operations;
import wiki.creeper.superiorskyblockIntegeration.redis.RedisMessage;

final class ClientNetworkService implements NetworkSkyblockService {

    private final ClientRequestDispatcher dispatcher;

    ClientNetworkService(ClientRequestDispatcher dispatcher) {
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
