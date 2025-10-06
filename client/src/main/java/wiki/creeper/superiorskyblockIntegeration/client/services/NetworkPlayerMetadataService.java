package wiki.creeper.superiorskyblockIntegeration.client.services;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import wiki.creeper.superiorskyblockIntegeration.api.NetworkOperationResult;
import wiki.creeper.superiorskyblockIntegeration.api.PlayerMetadataService;
import wiki.creeper.superiorskyblockIntegeration.client.ClientNetworkService;

/**
 * Client-side metadata service backed by the gateway network operations.
 */
public final class NetworkPlayerMetadataService implements PlayerMetadataService {

    private final ClientNetworkService network;

    public NetworkPlayerMetadataService(ClientNetworkService network) {
        this.network = network;
    }

    @Override
    public CompletableFuture<Void> put(UUID playerUuid, String key, String value, Duration ttl) {
        return network.putData(null, namespace(playerUuid), key, value, ttl != null ? ttl.toSeconds() : 0L)
                .thenApply(NetworkPlayerMetadataService::verifySuccess);
    }

    @Override
    public CompletableFuture<Optional<String>> get(UUID playerUuid, String key) {
        return network.getData(null, namespace(playerUuid), key)
                .thenApply(result -> {
                    if (result.failed()) {
                        return Optional.empty();
                    }
                    return result.data().has("value")
                            ? Optional.of(result.data().get("value").getAsString())
                            : Optional.empty();
                });
    }

    @Override
    public CompletableFuture<Void> delete(UUID playerUuid, String key) {
        return network.deleteData(null, namespace(playerUuid), key)
                .thenApply(NetworkPlayerMetadataService::verifySuccess);
    }

    private static String namespace(UUID uuid) {
        return "metadata:" + uuid.toString().toLowerCase();
    }

    private static Void verifySuccess(NetworkOperationResult result) {
        if (result.failed()) {
            throw new IllegalStateException("Metadata operation failed: " + result.errorCode() + " (" + result.errorMessage() + ")");
        }
        return null;
    }
}
