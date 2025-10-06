package wiki.creeper.superiorskyblockIntegeration.client.listeners;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.player.PlayerFishEvent;

import wiki.creeper.superiorskyblockIntegeration.client.services.QuestProgressService;
import wiki.creeper.superiorskyblockIntegeration.common.quest.QuestType;

/**
 * Listens for gameplay events that contribute towards island quests.
 */
public final class QuestProgressListener implements Listener {

    private final QuestProgressService progressService;

    public QuestProgressListener(QuestProgressService progressService) {
        this.progressService = progressService;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        Material type = event.getItemType();
        int amount = Math.max(0, event.getItemAmount());
        if (amount <= 0) {
            return;
        }
        switch (type) {
            case IRON_INGOT -> incrementBoth(player, 7, amount);
            case GOLD_INGOT -> incrementBoth(player, 8, amount);
            default -> {
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material material = block.getType();

        if (isMatureCrop(material, block)) {
            switch (material) {
                case WHEAT -> incrementBoth(player, 1, 1);
                case POTATOES -> incrementBoth(player, 3, 1);
                case CARROTS -> incrementBoth(player, 4, 1);
                case PUMPKIN -> incrementBoth(player, 5, 1);
                case MELON -> incrementBoth(player, 6, 1);
                default -> {
                }
            }
            return;
        }

        if (isTargetOre(material)) {
            if (isDiamondOre(material)) {
                incrementBoth(player, 9, 1);
            } else if (isEmeraldOre(material)) {
                incrementBoth(player, 10, 1);
            }
            return;
        }

        if (isLog(material)) {
            incrementBoth(player, 12, 1);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        incrementBoth(player, 11, 1);
    }

    private boolean isMatureCrop(Material material, Block block) {
        if (material == Material.WHEAT || material == Material.POTATOES || material == Material.CARROTS) {
            return isFullyGrown(block);
        }
        if (material == Material.PUMPKIN || material == Material.MELON) {
            return true;
        }
        return false;
    }

    private boolean isFullyGrown(Block block) {
        BlockState state = block.getState();
        if (!(state.getBlockData() instanceof Ageable ageable)) {
            return true;
        }
        return ageable.getAge() >= ageable.getMaximumAge();
    }

    private boolean isTargetOre(Material material) {
        return isDiamondOre(material) || isEmeraldOre(material);
    }

    private boolean isDiamondOre(Material material) {
        return material == Material.DIAMOND_ORE || material == Material.DEEPSLATE_DIAMOND_ORE;
    }

    private boolean isEmeraldOre(Material material) {
        return material == Material.EMERALD_ORE || material == Material.DEEPSLATE_EMERALD_ORE;
    }

    private boolean isLog(Material material) {
        return Tag.LOGS.isTagged(material);
    }

    private void incrementBoth(Player player, int questId, int amount) {
        progressService.increment(player, QuestType.DAILY, questId, amount);
        progressService.increment(player, QuestType.WEEKLY, questId, amount);
    }
}

