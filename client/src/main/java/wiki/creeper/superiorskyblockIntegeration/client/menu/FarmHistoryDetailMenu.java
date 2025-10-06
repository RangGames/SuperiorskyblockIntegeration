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
        fill(icon(Material.GRAY_STAINED_GLASS_PANE, " "));

        FarmHistoryService.Period period = detail.period();
        setItem(4, icon(Material.CLOCK,
                "&a" + (period.displayName().isBlank() ? period.periodId() : period.displayName()),
                "&7생성: &f" + DATE_FORMAT.format(period.createdAt()),
                "&7등록된 섬: &f" + period.entries()));

        List<FarmHistoryService.Entry> entries = detail.entries();
        int[] slots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        for (int i = 0; i < entries.size() && i < slots.length; i++) {
            FarmHistoryService.Entry entry = entries.get(i);
            String rankColor = entry.rank() == 1 ? "&c" : (entry.rank() == 2 ? "&b" : (entry.rank() == 3 ? "&e" : "&a"));
            ItemStack item = icon(Material.PLAYER_HEAD,
                    rankColor + "&l[" + entry.rank() + "위] &f" + displayName(entry),
                    "&7섬장: &f" + safe(entry.ownerName()),
                    "&7총 달빛: &f" + format(entry.points()),
                    "&7일간 달빛: &f" + format(entry.dailyPoints()),
                    "&7주간 달빛: &f" + format(entry.weeklyPoints()));
            setItem(slots[i], item);
        }

        setItem(45, icon(Material.ARROW, "&a뒤로", "&7히스토리 목록으로 돌아갑니다."));
    }

    @Override
    protected void onClick(Player player, InventoryClickEvent event) {
        super.onClick(player, event);
        if (event.getRawSlot() == 45) {
            manager().openFarmHistory(player, manager().getHistoryPage(player));
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

