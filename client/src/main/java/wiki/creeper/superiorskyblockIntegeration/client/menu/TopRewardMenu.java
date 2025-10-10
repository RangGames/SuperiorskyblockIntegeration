package wiki.creeper.superiorskyblockIntegeration.client.menu;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Editor menu for island top-ranking rewards.
 */
final class TopRewardMenu extends AbstractMenu {

    private final int rank;
    private final List<ItemStack> initialItems;

    TopRewardMenu(IslandMenuManager manager, int rank, List<ItemStack> initialItems) {
        super(manager);
        this.rank = rank;
        this.initialItems = initialItems != null
                ? initialItems.stream().map(ItemStack::clone).collect(Collectors.toList())
                : List.of();
    }

    @Override
    protected String title(Player player) {
        return ChatColor.GREEN + "순위 보상 편집 - #" + rank;
    }

    @Override
    protected int size() {
        return 27;
    }

    @Override
    protected void build(Player player, Inventory inventory) {
        // leave empty for editing
    }

    @Override
    protected void onOpen(Player player) {
        Inventory inv = inventory();
        if (inv != null) {
            inv.clear();
            for (ItemStack stack : initialItems) {
                if (stack != null) {
                    inv.addItem(stack.clone());
                }
            }
        }
        player.sendMessage(ChatColor.YELLOW + "[Skyblock] 보상으로 지급할 아이템을 배치하고 인벤토리를 닫으면 저장됩니다.");
    }

    @Override
    protected void onClick(Player player, InventoryClickEvent event) {
        event.setCancelled(false);
    }

    @Override
    protected void onClose(Player player) {
        Inventory inv = inventory();
        if (inv == null) {
            return;
        }
        List<ItemStack> items = Arrays.stream(inv.getContents())
                .filter(Objects::nonNull)
                .filter(stack -> !stack.getType().isAir())
                .map(ItemStack::clone)
                .limit(27)
                .collect(Collectors.toList());
        manager().saveTopRewards(player, rank, items);
    }
}
