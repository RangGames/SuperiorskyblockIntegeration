package wiki.creeper.superiorskyblockIntegeration.client.menu;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

final class MainMenu extends AbstractMenu {

    MainMenu(IslandMenuManager manager) {
        super(manager);
    }

    @Override
    protected String title(Player player) {
        return ChatColor.GREEN + "팜 관리";
    }

    @Override
    protected int size() {
        return 27;
    }

    @Override
    protected void build(Player player, Inventory inventory) {
        ItemStack filler = icon(Material.GRAY_STAINED_GLASS_PANE, " ");
        fill(filler);

        setItem(10, icon(Material.GRASS_BLOCK,
                "&a&l섬 정보",
                "&7현재 팜 정보를 확인합니다."));
        setItem(12, icon(Material.PLAYER_HEAD,
                "&a&l멤버 관리",
                "&7팜 구성원을 확인합니다."));
        setItem(14, icon(Material.WRITABLE_BOOK,
                "&a&l초대함",
                "&7받은 초대를 확인하고 처리합니다."));
        setItem(16, icon(Material.PAPER,
                "&a&l초대 보내기",
                "&7다른 플레이어에게 팜 초대를 전송합니다."));
        setItem(18, icon(Material.TOTEM_OF_UNDYING,
                "&a&l팜 순위",
                "&7팜 달빛 순위와 기여도를 확인합니다."));
        setItem(20, icon(Material.BEACON,
                "&a&l경계선 설정",
                "&7팜 경계선을 토글하거나 색상을 변경합니다."));
        setItem(22, icon(Material.TROPICAL_FISH,
                "&a&l순위 보상",
                "&7팜 순위별 보상을 미리 확인합니다."));
        setItem(24, icon(Material.EMERALD,
                "&a&l팜 상점",
                "&7향후 업데이트될 팜 상점 미리보기."));
        setItem(26, icon(Material.WRITTEN_BOOK,
                "&a&l히스토리",
                "&7과거 순위를 확인합니다."));
    }

    @Override
    protected void onClick(Player player, InventoryClickEvent event) {
        super.onClick(player, event);
        int slot = event.getRawSlot();
        switch (slot) {
            case 10 -> manager().openMenu(player, new IslandInfoMenu(manager()));
            case 12 -> manager().openMembersMenu(player);
            case 14 -> manager().openPendingInvites(player);
            case 16 -> manager().openMenu(player, new InviteMenu(manager()));
            case 18 -> manager().openFarmRanking(player);
            case 20 -> manager().openBorderMenu(player);
            case 22 -> manager().openFarmRewards(player);
            case 24 -> manager().openFarmShop(player);
            case 26 -> manager().openFarmHistory(player, manager().getHistoryPage(player));
            default -> {
                // ignore other slots
            }
        }
    }
}
