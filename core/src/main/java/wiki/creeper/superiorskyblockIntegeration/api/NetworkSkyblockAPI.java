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

    public static Optional<PlayerMetadataService> metadataService() {
        ServicesManager services = Bukkit.getServer().getServicesManager();
        PlayerMetadataService service = services.load(PlayerMetadataService.class);
        return Optional.ofNullable(service);
    }

    public static Optional<PlayerProfileService> profileService() {
        ServicesManager services = Bukkit.getServer().getServicesManager();
        PlayerProfileService service = services.load(PlayerProfileService.class);
        return Optional.ofNullable(service);
    }

    public static Optional<PlayerIslandService> islandService() {
        ServicesManager services = Bukkit.getServer().getServicesManager();
        PlayerIslandService service = services.load(PlayerIslandService.class);
        return Optional.ofNullable(service);
    }
}
