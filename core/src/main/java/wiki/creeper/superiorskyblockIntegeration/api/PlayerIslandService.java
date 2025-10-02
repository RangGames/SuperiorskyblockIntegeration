package wiki.creeper.superiorskyblockIntegeration.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Provides fast lookups from player UUID to their island UUID for ranking and analytics components.
 */
public interface PlayerIslandService {

    /**
     * Returns the UUID of the island the specified player belongs to, if any.
     */
    Optional<UUID> islandId(UUID playerUuid);

    /**
     * Returns a snapshot of player UUIDs that belong to the specified island.
     */
    Collection<UUID> members(UUID islandUuid);

    /**
     * Returns an immutable snapshot of the entire player→island mapping.
     */
    Map<UUID, UUID> snapshot();

    /**
     * Refreshes the cached mapping for the given player directly from the authoritative source.
     */
    void refresh(UUID playerUuid);
}
