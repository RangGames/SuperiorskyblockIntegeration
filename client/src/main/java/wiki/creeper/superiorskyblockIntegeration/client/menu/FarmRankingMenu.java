package wiki.creeper.superiorskyblockIntegeration.client.menu;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

import wiki.creeper.superiorskyblockIntegeration.client.services.FarmRankingService;

/**
 * Displays farm ranking top list.
 */
public final class FarmRankingMenu extends AbstractMenu {

    private final List<FarmRankingService.IslandEntry> entries;

    public FarmRankingMenu(IslandMenuManager manager,
                           List<FarmRankingService.IslandEntry> entries) {
        super(manager);
        this.entries = entries;
    }

    @Override
    protected String title(Player player) {
        return ChatColor.GREEN + "팜 순위";
    }

    @Override
    protected int size() {
        return 54;
    }

    @Override
    protected void build(Player player, Inventory inventory) {
        decorateDefault(inventory);
        placeNavigation(backButton("메인 메뉴"), null, mainMenuButton());

        if (entries == null || entries.isEmpty()) {
            setItem(22, icon(Material.BARRIER, "&c데이터 없음", "&7아직 순위 데이터가 없습니다."));
            return;
        }

        int[] slots = primarySlots();
        for (int i = 0; i < entries.size() && i < slots.length; i++) {
            FarmRankingService.IslandEntry entry = entries.get(i);
            String rankColor = i == 0 ? "&c" : (i == 1 ? "&b" : (i == 2 ? "&e" : "&a"));
            List<String> lore = new ArrayList<>();
            lore.add("&7팜장: &f" + safe(entry.ownerName()));
            lore.add("&7총 팜 점수: &f" + format(entry.points()));
            if (entry.dailyPoints() > 0) {
                lore.add("&7일간 점수: &f" + format(entry.dailyPoints()));
            }
            if (entry.weeklyPoints() > 0) {
                lore.add("&7주간 점수: &f" + format(entry.weeklyPoints()));
            }
            lore.add(" ");
            lore.add("&a클릭 시 기여도 상세를 확인합니다.");
            ItemStack item = icon(Material.PLAYER_HEAD,
                    rankColor + "&l[" + (i + 1) + "위] &f" + displayName(entry),
                    lore.toArray(String[]::new));
            setItem(slots[i], withStringTag(item, "target-island", entry.islandId()));
        }
    }

    @Override
    protected void onClick(Player player, InventoryClickEvent event) {
        super.onClick(player, event);
        int slot = event.getRawSlot();
        Inventory inventory = inventory();
        if (inventory == null) {
            return;
        }
        int size = inventory.getSize();
        int backSlot = size - 9;
        int mainSlot = size - 1;
        if (slot == backSlot) {
            manager().openMainMenu(player);
            return;
        }
        if (slot == mainSlot) {
            manager().openMainMenu(player);
            return;
        }
        ItemStack item = event.getCurrentItem();
        String islandId = readStringTag(item, "target-island");
        if (islandId != null) {
            manager().openFarmMemberRanking(player, islandId);
        }
    }

    private String displayName(FarmRankingService.IslandEntry entry) {
        String name = safe(entry.islandName());
        return name.isBlank() ? entry.islandId().substring(0, Math.min(8, entry.islandId().length())) : name;
    }

    private String safe(String value) {
        return value != null ? value : "알 수 없음";
    }

    private String format(long value) {
        return String.format("%,d", value);
    }
}
