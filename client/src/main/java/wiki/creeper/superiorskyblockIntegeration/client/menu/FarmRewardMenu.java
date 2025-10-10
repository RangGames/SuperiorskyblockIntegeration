package wiki.creeper.superiorskyblockIntegeration.client.menu;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

import wiki.creeper.superiorskyblockIntegeration.client.services.FarmRewardService;

/**
 * Displays farm ranking rewards retrieved from the gateway.
 */
public final class FarmRewardMenu extends AbstractMenu {

    private final List<FarmRewardService.RewardTier> rewards;

    public FarmRewardMenu(IslandMenuManager manager, List<FarmRewardService.RewardTier> rewards) {
        super(manager);
        this.rewards = rewards;
    }

    @Override
    protected String title(Player player) {
        return ChatColor.GOLD + "팜 순위 보상";
    }

    @Override
    protected int size() {
        return 45;
    }

    @Override
    protected void build(Player player, Inventory inventory) {
        decorateDefault(inventory);
        placeNavigation(backButton("메인 메뉴"), null, mainMenuButton());
        if (rewards == null || rewards.isEmpty()) {
            setItem(22, icon(Material.BARRIER,
                    ChatColor.YELLOW + "등록된 보상이 없습니다.",
                    ChatColor.GRAY + "관리자가 설정을 확인해야 합니다."));
        } else {
            int[] slots = primarySlots();
            for (int i = 0; i < rewards.size() && i < slots.length; i++) {
                setItem(slots[i], toIcon(rewards.get(i)));
            }
        }
    }

    @Override
    protected void onClick(Player player, InventoryClickEvent event) {
        super.onClick(player, event);
        Inventory inventory = inventory();
        if (inventory == null) {
            return;
        }
        int slot = event.getRawSlot();
        int size = inventory.getSize();
        int backSlot = size - 9;
        int mainSlot = size - 1;
        if (slot == backSlot) {
            manager().openMainMenu(player);
        } else if (slot == mainSlot) {
            manager().openMainMenu(player);
        }
    }

    private Material resolveIcon(String iconName) {
        if (iconName == null || iconName.isBlank()) {
            return Material.CHEST;
        }
        Material material = Material.matchMaterial(iconName.toUpperCase());
        return material != null ? material : Material.CHEST;
    }

    private String formatRankRange(FarmRewardService.RewardTier tier) {
        if (tier.minRank() == tier.maxRank()) {
            return tier.minRank() + "위";
        }
        return tier.minRank() + "위 ~ " + tier.maxRank() + "위";
    }

    private String formatNumber(int value) {
        return String.format("%,d", value);
    }

    private org.bukkit.inventory.ItemStack toIcon(FarmRewardService.RewardTier tier) {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GOLD + formatRankRange(tier));
        lore.add(ChatColor.YELLOW + "팜 포인트 " + formatNumber(tier.farmPoints()));
        if (!tier.lore().isEmpty()) {
            lore.add(" ");
            for (String line : tier.lore()) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
        }
        return icon(resolveIcon(tier.icon()),
                ChatColor.translateAlternateColorCodes('&', tier.title()),
                lore.toArray(new String[0]));
    }
}
