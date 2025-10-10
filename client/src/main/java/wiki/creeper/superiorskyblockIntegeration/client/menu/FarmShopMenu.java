package wiki.creeper.superiorskyblockIntegeration.client.menu;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import wiki.creeper.superiorskyblockIntegeration.client.services.FarmShopService;

/**
 * Displays farm shop items retrieved from the gateway.
 */
public final class FarmShopMenu extends AbstractMenu {

    private final List<FarmShopService.ShopItem> items;

    public FarmShopMenu(IslandMenuManager manager, List<FarmShopService.ShopItem> items) {
        super(manager);
        this.items = items != null ? items : List.of();
    }

    @Override
    protected String title(Player player) {
        return ChatColor.GREEN + "팜 상점";
    }

    @Override
    protected int size() {
        return 45;
    }

    @Override
    protected void build(Player player, Inventory inventory) {
        decorateDefault(inventory);
        placeNavigation(backButton("메인 메뉴"), null, mainMenuButton());
        if (items.isEmpty()) {
            setItem(22, icon(Material.BARRIER,
                    ChatColor.YELLOW + "판매 중인 상품이 없습니다.",
                    ChatColor.GRAY + "관리자가 상점을 설정해야 합니다."));
        } else {
            int[] contentSlots = primarySlots();
            items.stream()
                    .sorted(Comparator.comparingInt(FarmShopService.ShopItem::slot))
                    .forEach(item -> {
                        int slot = item.slot();
                        if (!isUsableSlot(slot, contentSlots)) {
                            slot = nextAvailableSlot(contentSlots);
                        }
                        if (slot >= 0) {
                            setItem(slot, toIcon(item));
                        }
                    });
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
            return Material.BARRIER;
        }
        Material material = Material.matchMaterial(iconName.toUpperCase());
        return material != null ? material : Material.BARRIER;
    }

    private String formatNumber(int value) {
        return String.format("%,d", value);
    }

    private org.bukkit.inventory.ItemStack toIcon(FarmShopService.ShopItem item) {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.YELLOW + "가격: " + formatNumber(item.price()) + " " + formatCurrency(item.currency()));
        if (!item.lore().isEmpty()) {
            lore.add(" ");
            for (String line : item.lore()) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
        }
        if (item.command() != null && !item.command().isBlank()) {
            lore.add(" ");
            lore.add(ChatColor.GRAY + "구매 시 실행: " + ChatColor.YELLOW + item.command());
        }
        if (!item.enabled()) {
            lore.add(" ");
            lore.add(ChatColor.RED + "현재 구매할 수 없습니다.");
        }
        return icon(resolveIcon(item.icon()),
                ChatColor.translateAlternateColorCodes('&', item.title()),
                lore.toArray(new String[0]));
    }

    private String formatCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return "";
        }
        return switch (currency.toLowerCase()) {
            case "moonlight" -> "팜 포인트";
            case "farmpoint", "farm_points", "farm" -> "팜 포인트";
            case "none" -> "-";
            default -> currency;
        };
    }

    private boolean isUsableSlot(int slot, int[] contentSlots) {
        if (slot < 0 || slot >= size()) {
            return false;
        }
        for (int contentSlot : contentSlots) {
            if (contentSlot == slot) {
                return true;
            }
        }
        return false;
    }

    private int nextAvailableSlot(int[] contentSlots) {
        Inventory inventory = inventory();
        if (inventory == null) {
            return -1;
        }
        for (int slot : contentSlots) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.GRAY_STAINED_GLASS_PANE) {
                return slot;
            }
        }
        return -1;
    }
}
