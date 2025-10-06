package wiki.creeper.superiorskyblockIntegeration.gateway.listeners;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.upgrades.Upgrade;
import com.bgsoftware.superiorskyblock.api.upgrades.UpgradeLevel;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

import wiki.creeper.superiorskyblockIntegeration.config.PluginConfig;

/**
 * Applies island furnace upgrades to vanilla furnace events.
 */
public final class GatewayFurnaceListener implements Listener {

    private final Logger logger;
    private final Upgrade burnUpgrade;
    private final Upgrade speedUpgrade;
    private final double burnBonusPerLevel;
    private final double cookReductionPerLevel;

    public GatewayFurnaceListener(JavaPlugin plugin, PluginConfig.QuestSettings questSettings) {
        this.logger = plugin.getLogger();
        PluginConfig.FurnaceSettings settings = questSettings != null ? questSettings.furnace() : null;
        if (settings == null) {
            this.burnUpgrade = null;
            this.speedUpgrade = null;
            this.burnBonusPerLevel = 0.0D;
            this.cookReductionPerLevel = 0.0D;
            return;
        }
        this.burnUpgrade = resolveUpgrade(settings.burnUpgrade());
        this.speedUpgrade = resolveUpgrade(settings.speedUpgrade());
        this.burnBonusPerLevel = Math.max(0.0D, settings.burnBonusPerLevel());
        this.cookReductionPerLevel = Math.max(0.0D, settings.cookReductionPerLevel());
    }

    public boolean isEnabled() {
        return burnUpgrade != null || speedUpgrade != null;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onFurnaceBurn(FurnaceBurnEvent event) {
        if (burnUpgrade == null || burnBonusPerLevel <= 0.0D) {
            return;
        }
        Island island = islandAt(event.getBlock().getLocation());
        if (island == null) {
            return;
        }
        int level = upgradeLevel(island, burnUpgrade);
        if (level <= 0) {
            return;
        }
        int original = event.getBurnTime();
        double multiplier = 1.0D + (burnBonusPerLevel * level);
        int adjusted = (int) Math.max(1, Math.round(original * multiplier));
        event.setBurnTime(adjusted);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onFurnaceStartSmelt(FurnaceStartSmeltEvent event) {
        if (speedUpgrade == null || cookReductionPerLevel <= 0.0D) {
            return;
        }
        Island island = islandAt(event.getBlock().getLocation());
        if (island == null) {
            return;
        }
        int level = upgradeLevel(island, speedUpgrade);
        if (level <= 0) {
            return;
        }
        double reduction = cookReductionPerLevel * level;
        double factor = Math.max(0.05D, 1.0D - reduction);
        int original = event.getTotalCookTime();
        int adjusted = (int) Math.max(1, Math.round(original * factor));
        event.setTotalCookTime(adjusted);
    }

    private Upgrade resolveUpgrade(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return null;
        }
        Upgrade upgrade = SuperiorSkyblockAPI.getSuperiorSkyblock()
                .getUpgrades()
                .getUpgrade(rawName);
        if (upgrade == null) {
            logger.warning("Unable to find island upgrade named '" + rawName + "' for furnace handling.");
        }
        return upgrade;
    }

    private Island islandAt(Location location) {
        if (location == null) {
            return null;
        }
        return SuperiorSkyblockAPI.getIslandAt(location);
    }

    private int upgradeLevel(Island island, Upgrade upgrade) {
        if (island == null || upgrade == null) {
            return 0;
        }
        UpgradeLevel level = island.getUpgradeLevel(upgrade);
        return level != null ? level.getLevel() : 0;
    }
}
