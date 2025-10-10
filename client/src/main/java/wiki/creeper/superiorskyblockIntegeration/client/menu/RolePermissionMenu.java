package wiki.creeper.superiorskyblockIntegeration.client.menu;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;

import wiki.creeper.superiorskyblockIntegeration.client.model.RolePermissionSnapshot;

final class RolePermissionMenu extends AbstractMenu {

    private final RolePermissionSnapshot snapshot;

    RolePermissionMenu(IslandMenuManager manager, RolePermissionSnapshot snapshot) {
        super(manager);
        this.snapshot = snapshot;
    }

    @Override
    protected String title(Player player) {
        String island = snapshot.islandName() != null ? snapshot.islandName() : "팜";
        return ChatColor.GREEN + "권한 관리 - " + island;
    }

    @Override
    protected int size() {
        return 54;
    }

    @Override
    protected void build(Player player, Inventory inventory) {
        decorateDefault(inventory);
        placeNavigation(backButton("메인 메뉴"), null, mainMenuButton());

        List<RolePermissionSnapshot.Role> roles = snapshot.roles();
        if (roles.isEmpty()) {
            setItem(22, icon(Material.BARRIER,
                    "&c권한 정보를 불러오지 못했습니다",
                    "&7팜 역할 정보가 존재하지 않습니다."));
            return;
        }

        int[] slots = primarySlots();
        for (int i = 0; i < roles.size() && i < slots.length; i++) {
            RolePermissionSnapshot.Role role = roles.get(i);
            setItem(slots[i], roleIcon(role));
        }
    }

    private org.bukkit.inventory.ItemStack roleIcon(RolePermissionSnapshot.Role role) {
        long enabled = role.enabledCount();
        int total = role.totalPrivileges();
        String status = ChatColor.YELLOW + "활성화 " + enabled + ChatColor.GRAY + " / " + ChatColor.YELLOW + total;
        String action = snapshot.canManage()
                ? ChatColor.GRAY + "클릭하여 권한을 편집합니다."
                : ChatColor.RED + "권한을 변경할 수 있는 권한이 없습니다.";
        return icon(Material.WRITABLE_BOOK,
                ChatColor.AQUA + role.displayName(),
                status,
                "",
                action);
    }

    @Override
    protected void onClick(Player player, InventoryClickEvent event) {
        super.onClick(player, event);
        Inventory inventory = inventory();
        if (inventory == null) {
            return;
        }
        int size = inventory.getSize();
        int slot = event.getRawSlot();
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
        int[] slots = primarySlots();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot && i < snapshot.roles().size()) {
                manager().openMenu(player, new RolePermissionDetailMenu(manager(), snapshot, snapshot.roles().get(i)));
                return;
            }
        }
    }
}
