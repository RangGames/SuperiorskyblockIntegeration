package wiki.creeper.superiorskyblockIntegeration.api;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import wiki.creeper.superiorskyblockIntegeration.common.model.PlayerProfile;

public final class NetworkPlayerProfiles {

    private NetworkPlayerProfiles() {
    }

    public static CompletableFuture<Optional<PlayerProfile>> lookupByName(String name) {
        return NetworkSkyblockAPI.profileService()
                .map(service -> service.lookupByName(name))
                .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty()));
    }

    public static CompletableFuture<Optional<PlayerProfile>> lookupByUuid(UUID uuid) {
        return NetworkSkyblockAPI.profileService()
                .map(service -> service.lookupByUuid(uuid))
                .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty()));
    }

    public static CompletableFuture<Optional<UUID>> resolveUuid(String name) {
        return lookupByName(name).thenApply(profile -> profile.map(PlayerProfile::uuid).map(UUID::fromString));
    }

    public static CompletableFuture<Optional<String>> resolveName(UUID uuid) {
        return lookupByUuid(uuid).thenApply(profile -> profile.map(PlayerProfile::name));
    }
}
