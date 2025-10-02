package wiki.creeper.superiorskyblockIntegeration.api;

import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicesManager;

import java.util.Optional;

/**
 * Entry point for retrieving the {@link NetworkSkyblockService} implementation exposed by the
 * SuperiorskyblockIntegeration plugin when running in client mode.
 */
public final class NetworkSkyblockAPI {

    private NetworkSkyblockAPI() {
    }

    public static Optional<NetworkSkyblockService> service() {
        ServicesManager services = Bukkit.getServer().getServicesManager();
        NetworkSkyblockService service = services.load(NetworkSkyblockService.class);
        return Optional.ofNullable(service);
    }

    public static NetworkSkyblockService require() {
        return service().orElseThrow(() ->
                new IllegalStateException("NetworkSkyblockService not registered"));
    }

    public static boolean isAvailable() {
        return service().isPresent();
    }
}
