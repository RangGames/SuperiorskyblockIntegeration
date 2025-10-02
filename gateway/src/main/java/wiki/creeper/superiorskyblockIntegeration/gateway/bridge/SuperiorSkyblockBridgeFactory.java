package wiki.creeper.superiorskyblockIntegeration.gateway.bridge;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

/**
 * Creates a bridge implementation depending on whether SuperiorSkyblock2 is present.
 */
public final class SuperiorSkyblockBridgeFactory {

    private SuperiorSkyblockBridgeFactory() {
    }

    public static SuperiorSkyblockBridge create(JavaPlugin plugin) {
        PluginManager pm = plugin.getServer().getPluginManager();
        Plugin ssb = pm.getPlugin("SuperiorSkyblock2");
        if (ssb == null || !ssb.isEnabled()) {
            plugin.getLogger().warning("SuperiorSkyblock2 not detected; gateway operations will fail");
            return new FallbackBridge();
        }
        try {
            return new ApiSuperiorSkyblockBridge();
        } catch (Exception ex) {
            Logger logger = plugin.getLogger();
            logger.warning("Failed to initialise SuperiorSkyblock bridge: " + ex.getMessage());
            return new FallbackBridge();
        }
    }
}
