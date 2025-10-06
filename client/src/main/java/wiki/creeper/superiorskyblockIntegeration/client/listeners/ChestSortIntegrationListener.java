package wiki.creeper.superiorskyblockIntegeration.client.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Optional integration with ChestSort. Mirrors the legacy Skript behaviour where ops could right-click
 * outside the inventory to trigger a manual sort.
 */
public final class ChestSortIntegrationListener implements Listener {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final boolean enabled;
    private final Method sortInventoryMethod;
    private final Class<?> chestSortApiClass;

    public ChestSortIntegrationListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        Class<?> apiClass = null;
        Method method = null;
        boolean available = false;
        try {
            PluginManager pm = plugin.getServer().getPluginManager();
            if (pm.getPlugin("ChestSort") != null) {
                apiClass = Class.forName("de.jeff_media.chestsort.api.ChestSortAPI");
                method = apiClass.getMethod("sortInventory", Inventory.class);
                available = true;
            }
        } catch (Exception ex) {
            logger.fine("ChestSort API not available: " + ex.getMessage());
            apiClass = null;
            method = null;
            available = false;
        }
        this.chestSortApiClass = apiClass;
        this.sortInventoryMethod = method;
        this.enabled = available;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!enabled) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!player.isOp()) {
            return;
        }
        if (event.getClick() != ClickType.RIGHT || event.getSlot() >= 0) {
            return;
        }
        Inventory inventory = player.getInventory();
        try {
            sortInventoryMethod.invoke(null, inventory);
            player.sendMessage("§a[ChestSort] 인벤토리가 정리되었습니다.");
        } catch (Exception ex) {
            logger.warning("Failed to invoke ChestSort API: " + ex.getMessage());
        }
    }
}
