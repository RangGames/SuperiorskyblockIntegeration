package wiki.creeper.superiorskyblockIntegeration.gateway.data;

import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import wiki.creeper.superiorskyblockIntegeration.api.PlayerMetadataService;

/**
 * Local gateway implementation of the metadata service.
 */
public final class GatewayPlayerMetadataService implements PlayerMetadataService {

    private final GatewayDataService dataService;
    private final Executor asyncExecutor;

    public GatewayPlayerMetadataService(JavaPlugin plugin, GatewayDataService dataService) {
        this.dataService = dataService;
        this.asyncExecutor = command -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, command);
    }

    @Override
    public CompletableFuture<Void> put(UUID playerUuid, String key, String value, Duration ttl) {
        return CompletableFuture.runAsync(() -> dataService.setData(namespace(playerUuid), key, value, ttl), asyncExecutor);
    }

    @Override
    public CompletableFuture<Optional<String>> get(UUID playerUuid, String key) {
        return CompletableFuture.supplyAsync(() -> dataService.getData(namespace(playerUuid), key), asyncExecutor);
    }

    @Override
    public CompletableFuture<Void> delete(UUID playerUuid, String key) {
        return CompletableFuture.runAsync(() -> dataService.deleteData(namespace(playerUuid), key), asyncExecutor);
    }

    private static String namespace(UUID uuid) {
        return "metadata:" + uuid.toString().toLowerCase();
    }
}
