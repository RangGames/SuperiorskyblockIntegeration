package wiki.creeper.superiorskyblockIntegeration.api;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import wiki.creeper.superiorskyblockIntegeration.common.model.PlayerProfile;

/**
 * Exposes asynchronous lookup utilities for player profiles stored in the shared Redis cache.
 */
public interface PlayerProfileService {

    CompletableFuture<Optional<PlayerProfile>> lookupByName(String name);

    CompletableFuture<Optional<PlayerProfile>> lookupByUuid(UUID uuid);
}
