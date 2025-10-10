package wiki.creeper.superiorskyblockIntegeration.client.menu;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

import wiki.creeper.superiorskyblockIntegeration.client.services.FarmRankingService;

/**
 * Shows contribution ranking for a specific island.
 */
public final class FarmMemberRankingMenu extends AbstractMenu {

    private final String islandId;
    private final List<FarmRankingService.MemberEntry> members;

    public FarmMemberRankingMenu(IslandMenuManager manager,
                                 String islandId,
                                 List<FarmRankingService.MemberEntry> members) {
        super(manager);
        this.islandId = islandId;
        this.members = members;
    }

    @Override
    protected String title(Player player) {
        return ChatColor.GREEN + "팜 기여도";
    }

    @Override
    protected int size() {
        return 54;
    }

    @Override
    protected void build(Player player, Inventory inventory) {
        decorateDefault(inventory);
        placeNavigation(backButton("팜 순위"), null, mainMenuButton());

        if (members == null || members.isEmpty()) {
            setItem(22, icon(Material.PAPER, "&c데이터 없음", "&7기여도 데이터가 없습니다."));
        } else {
            int[] slots = primarySlots();
            for (int i = 0; i < members.size() && i < slots.length; i++) {
                FarmRankingService.MemberEntry entry = members.get(i);
                ItemStack item = icon(Material.PLAYER_HEAD,
                        "&e" + (i + 1) + "위 &f" + displayName(entry),
                        "&7기여도: &f" + format(entry.points()));
                setItem(slots[i], item);
            }
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
            manager().openFarmRanking(player);
            return;
        }
        if (slot == mainSlot) {
            manager().openMainMenu(player);
        }
    }

    private String displayName(FarmRankingService.MemberEntry entry) {
        String name = entry.playerName();
        return name != null && !name.isBlank() ? name : entry.playerUuid().substring(0, Math.min(8, entry.playerUuid().length()));
    }

    private String format(long value) {
        return String.format("%,d", value);
    }
}
