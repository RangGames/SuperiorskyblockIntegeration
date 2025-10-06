package wiki.creeper.superiorskyblockIntegeration.api;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Exposes metadata utilities that mirror the Skript {@code metadata} storage semantics.
 */
public interface PlayerMetadataService {

    CompletableFuture<Void> put(UUID playerUuid, String key, String value, Duration ttl);

    CompletableFuture<Optional<String>> get(UUID playerUuid, String key);

    CompletableFuture<Void> delete(UUID playerUuid, String key);
}
