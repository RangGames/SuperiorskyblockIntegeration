package wiki.creeper.superiorskyblockIntegeration.client.services;

import com.google.gson.JsonObject;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import wiki.creeper.superiorskyblockIntegeration.api.NetworkOperationResult;
import wiki.creeper.superiorskyblockIntegeration.api.PlayerProfileService;
import wiki.creeper.superiorskyblockIntegeration.client.ClientNetworkService;
import wiki.creeper.superiorskyblockIntegeration.common.model.PlayerProfile;

public final class NetworkPlayerProfileService implements PlayerProfileService {

    private final ClientNetworkService networkService;

    public NetworkPlayerProfileService(ClientNetworkService networkService) {
        this.networkService = networkService;
    }

    @Override
    public CompletableFuture<Optional<PlayerProfile>> lookupByName(String name) {
        return query(name);
    }

    @Override
    public CompletableFuture<Optional<PlayerProfile>> lookupByUuid(UUID uuid) {
        return query(uuid.toString());
    }

    private CompletableFuture<Optional<PlayerProfile>> query(String identifier) {
        return networkService.lookupPlayerProfile(null, identifier)
                .thenApply(NetworkPlayerProfileService::extractProfile);
    }

    private static Optional<PlayerProfile> extractProfile(NetworkOperationResult result) {
        if (result.failed()) {
            return Optional.empty();
        }
        JsonObject data = result.data();
        if (!data.has("profile") || !data.get("profile").isJsonObject()) {
            return Optional.empty();
        }
        return Optional.of(PlayerProfile.fromJson(data.getAsJsonObject("profile")));
    }
}
