package wiki.creeper.superiorskyblockIntegeration.gateway.listeners;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles island-specific block generators (magma/crying obsidian) and related utility events.
 */
public final class IslandBlockGeneratorListener implements Listener {

    private final JavaPlugin plugin;

    public IslandBlockGeneratorListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        Material type = block.getType();
        if (!isGeneratorBlock(type)) {
            return;
        }
        Island island = SuperiorSkyblockAPI.getIslandAt(block.getLocation());
        if (island == null) {
            return;
        }
        Block target = block.getRelative(BlockFace.UP);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (block.getType() != type) {
                return;
            }
            if (!target.isEmpty()) {
                return;
            }
            Material generated = generateOre(type);
            target.setType(generated, false);
        }, 12L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Island island = SuperiorSkyblockAPI.getIslandAt(block.getLocation());
        if (island == null) {
            return;
        }
        handleRawOreDrops(event, block);
        Block base = block.getRelative(BlockFace.DOWN);
        Material baseType = base.getType();
        if (!isGeneratorBlock(baseType)) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> regenerateOre(block, baseType), 10L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        for (int i = 0; i < 4; i++) {
            String line = event.getLine(i);
            if (line != null && !line.isBlank()) {
                event.setLine(i, ChatColor.translateAlternateColorCodes('&', line));
            }
        }
    }

    private void regenerateOre(Block block, Material baseType) {
        Block base = block.getRelative(BlockFace.DOWN);
        if (base.getType() != baseType || !block.isEmpty()) {
            return;
        }
        Material material = generateOre(baseType);
        block.setType(material, false);
    }

    private boolean isGeneratorBlock(Material material) {
        return material == Material.MAGMA_BLOCK || material == Material.CRYING_OBSIDIAN;
    }

    private void handleRawOreDrops(BlockBreakEvent event, Block block) {
        Material type = block.getType();
        if (type != Material.IRON_ORE && type != Material.GOLD_ORE) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        if (!isValidPickaxe(player.getInventory().getItemInMainHand().getType())) {
            return;
        }
        event.setDropItems(false);
        Material dropMaterial = type == Material.IRON_ORE ? Material.RAW_IRON : Material.RAW_GOLD;
        ItemStack drop = new ItemStack(dropMaterial, 1);
        block.getWorld().dropItemNaturally(block.getLocation(), drop);
    }

    private boolean isValidPickaxe(Material material) {
        return material == Material.STONE_PICKAXE
                || material == Material.IRON_PICKAXE
                || material == Material.GOLDEN_PICKAXE
                || material == Material.DIAMOND_PICKAXE
                || material == Material.NETHERITE_PICKAXE;
    }

    private Material generateOre(Material baseType) {
        double value = ThreadLocalRandom.current().nextDouble(0.0D, 1.0D);
        if (baseType == Material.CRYING_OBSIDIAN) {
            if (value <= 0.72D) {
                return Material.STONE;
            } else if (value <= 0.85D) {
                return Material.IRON_ORE;
            } else if (value <= 0.96D) {
                return Material.GOLD_ORE;
            } else if (value <= 0.988D) {
                return Material.DIAMOND_ORE;
            } else {
                return Material.EMERALD_ORE;
            }
        }
        if (value <= 0.65D) {
            return Material.STONE;
        } else if (value <= 0.78D) {
            return Material.COAL_ORE;
        } else if (value <= 0.86D) {
            return Material.IRON_ORE;
        } else if (value <= 0.92D) {
            return Material.GOLD_ORE;
        } else if (value <= 0.97D) {
            return Material.LAPIS_ORE;
        } else if (value <= 0.995D) {
            return Material.DIAMOND_ORE;
        } else {
            return Material.EMERALD_ORE;
        }
    }
}
