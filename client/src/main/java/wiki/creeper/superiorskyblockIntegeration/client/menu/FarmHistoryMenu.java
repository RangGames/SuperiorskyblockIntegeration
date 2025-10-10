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
 * Displays available farm history periods with pagination.
 */
public final class FarmHistoryMenu extends AbstractMenu {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    static {
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
    }

    private final int page;
    private final List<FarmHistoryService.Period> periods;

    public FarmHistoryMenu(IslandMenuManager manager,
                           int page,
                           List<FarmHistoryService.Period> periods) {
        super(manager);
        this.page = page;
        this.periods = periods;
    }

    @Override
    protected String title(Player player) {
        return ChatColor.GREEN + "팜 히스토리";
    }

    @Override
    protected int size() {
        return 54;
    }

    @Override
    protected void build(Player player, Inventory inventory) {
        decorateDefault(inventory);
        placeNavigation(backButton("메인 메뉴"), icon(Material.PAPER, "&7페이지: &f" + page),
                icon(Material.ARROW, "&a다음 페이지", "&7다음 페이지로 이동합니다."));
        setItem(size() - 2, icon(Material.ARROW, "&a이전 페이지", "&7이전 페이지로 이동합니다."));

        if (periods == null || periods.isEmpty()) {
            setItem(22, icon(Material.BARRIER, "&c데이터 없음", "&7기록된 시즌이 없습니다."));
            return;
        }

        int[] slots = primarySlots();
        for (int i = 0; i < periods.size() && i < slots.length; i++) {
            FarmHistoryService.Period period = periods.get(i);
            ItemStack item = icon(Material.BOOK,
                    "&a" + (period.displayName().isBlank() ? period.periodId() : period.displayName()),
                    "&7생성: &f" + DATE_FORMAT.format(period.createdAt()),
                    "&7기록된 팜: &f" + period.entries(),
                    "",
                    "&a클릭 시 상세 기록을 확인합니다.");
            setItem(slots[i], withStringTag(item, "history-period", period.periodId()));
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
        int pageSlot = size - 5;
        int prevSlot = size - 2;
        int nextSlot = size - 1;
        if (slot == backSlot) {
            manager().openMainMenu(player);
            return;
        }
        if (slot == prevSlot) {
            manager().openFarmHistory(player, Math.max(1, page - 1));
            return;
        }
        if (slot == nextSlot) {
            manager().openFarmHistory(player, page + 1);
            return;
        }
        if (slot == pageSlot) {
            return;
        }
        ItemStack item = event.getCurrentItem();
        String periodId = readStringTag(item, "history-period");
        if (periodId != null) {
            manager().openFarmHistoryDetail(player, periodId);
        }
    }
}
