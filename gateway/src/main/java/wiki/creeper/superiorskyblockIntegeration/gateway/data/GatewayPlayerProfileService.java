package wiki.creeper.superiorskyblockIntegeration.gateway.data;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import wiki.creeper.superiorskyblockIntegeration.api.PlayerProfileService;
import wiki.creeper.superiorskyblockIntegeration.common.model.PlayerProfile;

public final class GatewayPlayerProfileService implements PlayerProfileService {

    private final GatewayDataService dataService;

    public GatewayPlayerProfileService(GatewayDataService dataService) {
        this.dataService = dataService;
    }

    @Override
    public CompletableFuture<Optional<PlayerProfile>> lookupByName(String name) {
        return CompletableFuture.supplyAsync(() -> dataService.findProfileByName(name));
    }

    @Override
    public CompletableFuture<Optional<PlayerProfile>> lookupByUuid(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> dataService.findProfileByUuid(uuid.toString()));
    }
}
