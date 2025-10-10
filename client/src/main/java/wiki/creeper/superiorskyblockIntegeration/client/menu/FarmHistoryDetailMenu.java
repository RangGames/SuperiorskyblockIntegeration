package wiki.creeper.superiorskyblockIntegeration.client.menu;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.TimeZone;

import wiki.creeper.superiorskyblockIntegeration.client.services.FarmHistoryService;

/**
 * Displays historical ranking snapshot for a given period.
 */
public final class FarmHistoryDetailMenu extends AbstractMenu {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    static {
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
    }

    private final FarmHistoryService.HistoryDetail detail;

    public FarmHistoryDetailMenu(IslandMenuManager manager,
                                 FarmHistoryService.HistoryDetail detail) {
        super(manager);
        this.detail = detail;
    }

    @Override
    protected String title(Player player) {
        return ChatColor.GREEN + "히스토리 상세";
    }

    @Override
    protected int size() {
        return 54;
    }

    @Override
    protected void build(Player player, Inventory inventory) {
        decorateDefault(inventory);
        placeNavigation(backButton("히스토리"), icon(Material.CLOCK,
                "&b선택된 시즌",
                "&7" + (detail.period().displayName().isBlank() ? detail.period().periodId() : detail.period().displayName())),
                mainMenuButton());

        FarmHistoryService.Period period = detail.period();
        setItem(13, icon(Material.CLOCK,
                "&a" + (period.displayName().isBlank() ? period.periodId() : period.displayName()),
                "&7생성: &f" + DATE_FORMAT.format(period.createdAt()),
                "&7등록된 팜: &f" + period.entries()));

        List<FarmHistoryService.Entry> entries = detail.entries();
        if (entries == null || entries.isEmpty()) {
            setItem(22, icon(Material.BARRIER, "&c데이터 없음", "&7해당 기간의 기록이 없습니다."));
            return;
        }

        int[] slots = primarySlots();
        for (int i = 0; i < entries.size() && i < slots.length; i++) {
            FarmHistoryService.Entry entry = entries.get(i);
            String rankColor = entry.rank() == 1 ? "&c" : (entry.rank() == 2 ? "&b" : (entry.rank() == 3 ? "&e" : "&a"));
            ItemStack item = icon(Material.PLAYER_HEAD,
                    rankColor + "&l[" + entry.rank() + "위] &f" + displayName(entry),
                    "&7팜장: &f" + safe(entry.ownerName()),
                    "&7총 팜 점수: &f" + format(entry.points()),
                    "&7일간 팜 점수: &f" + format(entry.dailyPoints()),
                    "&7주간 팜 점수: &f" + format(entry.weeklyPoints()));
            setItem(slots[i], item);
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
            manager().openFarmHistory(player, manager().getHistoryPage(player));
            return;
        }
        if (slot == mainSlot) {
            manager().openMainMenu(player);
        }
    }

    private String displayName(FarmHistoryService.Entry entry) {
        String name = entry.islandName();
        return name != null && !name.isBlank() ? name : entry.islandId().substring(0, Math.min(8, entry.islandId().length()));
    }

    private String safe(String value) {
        return value != null ? value : "알 수 없음";
    }

    private String format(long value) {
        return String.format("%,d", value);
    }
}
