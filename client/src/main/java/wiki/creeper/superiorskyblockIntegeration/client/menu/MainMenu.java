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
        return 54;
    }

    @Override
    protected void build(Player player, Inventory inventory) {
        decorateDefault(inventory);

        setItem(10, icon(Material.GRASS_BLOCK,
                "&a&l팜 개요",
                "&7팜 이름과 기본 정보를 확인합니다."));
        setItem(12, icon(Material.PLAYER_HEAD,
                "&a&l구성원 관리",
                "&7구성원 목록과 상태를 살펴봅니다."));
        setItem(14, icon(Material.LEVER,
                "&a&l권한 관리",
                "&7역할별 권한을 설정하고 조정합니다."));
        setItem(16, icon(Material.TARGET,
                "&a&l퀘스트 허브",
                "&7진행 가능한 팜 퀘스트를 관리합니다."));

        setItem(19, icon(Material.WRITABLE_BOOK,
                "&a&l받은 초대",
                "&7다른 팜에서 보낸 초대를 확인합니다."));
        setItem(21, icon(Material.NAME_TAG,
                "&a&l초대 보내기",
                "&7원하는 플레이어에게 초대를 전송합니다."));
        setItem(23, icon(Material.TOTEM_OF_UNDYING,
                "&a&l팜 순위",
                "&7현재 시즌의 순위를 조회합니다."));
        setItem(25, icon(Material.CHEST,
                "&a&l순위 보상",
                "&7달성 보상을 미리 확인합니다."));

        setItem(28, icon(Material.EMERALD,
                "&a&l팜 상점",
                "&7팜 전용 상점을 둘러봅니다."));
        setItem(30, icon(Material.WRITTEN_BOOK,
                "&a&l히스토리",
                "&7과거 시즌 기록을 확인합니다."));
        setItem(32, icon(Material.BOOKSHELF,
                "&a&l팜 규칙",
                "&7팜 규칙을 확인하고 편집합니다."));
        setItem(34, icon(Material.BEACON,
                "&a&l경계선 설정",
                "&7경계선 활성화 및 색상을 변경합니다."));

        setItem(37, icon(Material.GOLD_BLOCK,
                "&a&l팜 금고",
                "&7팜 금고를 확인하고 관리합니다."));

        placeNavigation(null, closeButton(), null);
    }

    @Override
    protected void onClick(Player player, InventoryClickEvent event) {
        super.onClick(player, event);
        int slot = event.getRawSlot();
        switch (slot) {
            case 10 -> manager().openMenu(player, new IslandInfoMenu(manager()));
            case 12 -> manager().openMembersMenu(player);
            case 14 -> manager().openRolePermissions(player);
            case 16 -> manager().openQuestHub(player);
            case 19 -> manager().openPendingInvites(player);
            case 21 -> manager().openMenu(player, new InviteMenu(manager()));
            case 23 -> manager().openFarmRanking(player);
            case 25 -> manager().openFarmRewards(player);
            case 28 -> manager().openFarmShop(player);
            case 30 -> manager().openFarmHistory(player, manager().getHistoryPage(player));
            case 32 -> manager().openRulesMenu(player);
            case 34 -> manager().openBorderMenu(player);
            case 37 -> manager().openBankMenu(player);
            case 49 -> player.closeInventory();
            default -> {
                // ignore other slots
            }
        }
    }
}
