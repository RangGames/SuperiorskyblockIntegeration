package wiki.creeper.superiorskyblockIntegeration.gateway.data;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import wiki.creeper.superiorskyblockIntegeration.api.PlayerProfileService;
import wiki.creeper.superiorskyblockIntegeration.common.model.PlayerProfile;

public final class GatewayPlayerProfileService implements PlayerProfileService {

    private final GatewayDataService dataService;
    private final Executor asyncExecutor;

    public GatewayPlayerProfileService(JavaPlugin plugin, GatewayDataService dataService) {
        this.dataService = dataService;
        this.asyncExecutor = command -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, command);
    }

    @Override
    public CompletableFuture<Optional<PlayerProfile>> lookupByName(String name) {
        return CompletableFuture.supplyAsync(() -> dataService.findProfileByName(name), asyncExecutor);
    }

    @Override
    public CompletableFuture<Optional<PlayerProfile>> lookupByUuid(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> dataService.findProfileByUuid(uuid.toString()), asyncExecutor);
    }
}
