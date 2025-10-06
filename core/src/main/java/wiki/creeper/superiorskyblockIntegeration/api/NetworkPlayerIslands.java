package wiki.creeper.superiorskyblockIntegeration.api;

import java.time.Duration;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import wiki.creeper.superiorskyblockIntegeration.common.model.PlayerProfile;

/**
 * Convenience helpers for retrieving island mappings for players across the network.
 */
public final class NetworkPlayerIslands {

    private static final String ISLAND_KEY = "island.uuid";

    private NetworkPlayerIslands() {
    }

    public static CompletableFuture<Optional<UUID>> forPlayer(Player player) {
        if (player == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return forPlayer(player.getUniqueId());
    }

    public static CompletableFuture<Optional<UUID>> forPlayer(UUID playerUuid) {
        return NetworkSkyblockAPI.metadataService()
                .map(service -> service.get(playerUuid, ISLAND_KEY)
                        .thenCompose(optional -> optional
                                .flatMap(NetworkPlayerIslands::parseUuid)
                                .<CompletableFuture<Optional<UUID>>>map(value -> CompletableFuture.completedFuture(Optional.of(value)))
                                .orElseGet(() -> refresh(playerUuid))))
                .orElseGet(() -> refresh(playerUuid));
    }

    public static CompletableFuture<Optional<UUID>> forName(String name) {
        return NetworkPlayerProfiles.lookupByName(name)
                .thenCompose(profile -> profile.map(PlayerProfile::uuid)
                        .flatMap(NetworkPlayerIslands::parseUuid)
                        .map(NetworkPlayerIslands::forPlayer)
                        .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty())));
    }

    private static CompletableFuture<Optional<UUID>> refresh(UUID uuid) {
        return NetworkSkyblockAPI.service()
                .map(service -> service.lookupPlayerIsland(null, uuid.toString())
                        .thenApply(result -> {
                            if (result.success() && result.data().has("islandId")) {
                                return parseUuid(result.data().get("islandId").getAsString());
                            }
                            return Optional.<UUID>empty();
                        }))
                .orElseGet(() -> CompletableFuture.completedFuture(Optional.<UUID>empty()));
    }

    public static void cacheIsland(UUID uuid, UUID islandUuid, Duration ttl) {
        NetworkSkyblockAPI.metadataService().ifPresent(service ->
                service.put(uuid, ISLAND_KEY, islandUuid.toString(), ttl));
    }

    public static void clearCache(UUID uuid) {
        NetworkSkyblockAPI.metadataService().ifPresent(service -> service.delete(uuid, ISLAND_KEY));
    }

    private static Optional<UUID> parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
