package wiki.creeper.superiorskyblockIntegeration.api;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class NetworkPlayerMetadata {

    private NetworkPlayerMetadata() {
    }

    public static CompletableFuture<Void> put(UUID uuid, String key, String value, Duration ttl) {
        return NetworkSkyblockAPI.metadataService()
                .map(service -> service.put(uuid, key, value, ttl))
                .orElseGet(() -> CompletableFuture.completedFuture(null));
    }

    public static CompletableFuture<Optional<String>> get(UUID uuid, String key) {
        return NetworkSkyblockAPI.metadataService()
                .map(service -> service.get(uuid, key))
                .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty()));
    }

    public static CompletableFuture<Void> delete(UUID uuid, String key) {
        return NetworkSkyblockAPI.metadataService()
                .map(service -> service.delete(uuid, key))
                .orElseGet(() -> CompletableFuture.completedFuture(null));
    }
}
