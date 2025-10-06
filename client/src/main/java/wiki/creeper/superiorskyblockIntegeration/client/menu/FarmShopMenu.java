package wiki.creeper.superiorskyblockIntegeration.client.menu;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

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
        return 27;
    }

    @Override
    protected void build(Player player, Inventory inventory) {
        fill(icon(Material.GRAY_STAINED_GLASS_PANE, " "));

        if (items.isEmpty()) {
            setItem(13, icon(Material.BARRIER,
                    ChatColor.YELLOW + "판매 중인 상품이 없습니다.",
                    ChatColor.GRAY + "관리자가 상점을 설정해야 합니다."));
        } else {
            items.stream()
                    .sorted(Comparator.comparingInt(FarmShopService.ShopItem::slot))
                    .forEach(item -> {
                        int slot = item.slot();
                        if (slot < 0 || slot >= size()) {
                            slot = nextAvailableSlot();
                        }
                        if (slot >= 0 && slot < size()) {
                            setItem(slot, toIcon(item));
                        }
                    });
        }

        setItem(22, icon(Material.ARROW, ChatColor.GREEN + "돌아가기", ChatColor.GRAY + "메인 메뉴로 돌아갑니다."));
    }

    @Override
    protected void onClick(Player player, InventoryClickEvent event) {
        if (event.getRawSlot() == 22) {
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
            case "moonlight" -> "달빛";
            case "farmpoint", "farm_points", "farm" -> "팜 포인트";
            case "none" -> "-";
            default -> currency;
        };
    }

    private int nextAvailableSlot() {
        for (int i = 0; i < size(); i++) {
            if (inventory().getItem(i) == null || inventory().getItem(i).getType() == Material.GRAY_STAINED_GLASS_PANE) {
                return i;
            }
        }
        return -1;
    }
}
