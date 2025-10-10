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
 * Editor menu for island power (마력) rewards.
 */
final class PowerRewardMenu extends AbstractMenu {

    private final int tier;
    private final List<ItemStack> initialItems;

    PowerRewardMenu(IslandMenuManager manager, int tier, List<ItemStack> initialItems) {
        super(manager);
        this.tier = tier;
        this.initialItems = initialItems != null
                ? initialItems.stream().map(ItemStack::clone).collect(Collectors.toList())
                : List.of();
    }

    @Override
    protected String title(Player player) {
        return ChatColor.GREEN + "점수 보상 편집 - " + tier;
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
        player.sendMessage(ChatColor.YELLOW + "[Skyblock] 인벤토리에 원하는 보상 아이템을 배치한 뒤 인벤토리를 닫으면 저장됩니다.");
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
        manager().savePowerRewards(player, tier, items);
    }
}
