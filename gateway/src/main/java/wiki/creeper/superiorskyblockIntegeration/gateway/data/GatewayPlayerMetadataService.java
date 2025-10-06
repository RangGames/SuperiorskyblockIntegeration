package wiki.creeper.superiorskyblockIntegeration.gateway.data;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import wiki.creeper.superiorskyblockIntegeration.api.PlayerMetadataService;

/**
 * Local gateway implementation of the metadata service.
 */
public final class GatewayPlayerMetadataService implements PlayerMetadataService {

    private final GatewayDataService dataService;

    public GatewayPlayerMetadataService(GatewayDataService dataService) {
        this.dataService = dataService;
    }

    @Override
    public CompletableFuture<Void> put(UUID playerUuid, String key, String value, Duration ttl) {
        return CompletableFuture.runAsync(() -> dataService.setData(namespace(playerUuid), key, value, ttl));
    }

    @Override
    public CompletableFuture<Optional<String>> get(UUID playerUuid, String key) {
        return CompletableFuture.supplyAsync(() -> dataService.getData(namespace(playerUuid), key));
    }

    @Override
    public CompletableFuture<Void> delete(UUID playerUuid, String key) {
        return CompletableFuture.runAsync(() -> dataService.deleteData(namespace(playerUuid), key));
    }

    private static String namespace(UUID uuid) {
        return "metadata:" + uuid.toString().toLowerCase();
    }
}
