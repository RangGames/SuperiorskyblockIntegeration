package wiki.creeper.superiorskyblockIntegeration.client.menu;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;

import wiki.creeper.superiorskyblockIntegeration.client.model.RolePermissionSnapshot;

final class RolePermissionDetailMenu extends AbstractMenu {

    private final RolePermissionSnapshot snapshot;
    private final RolePermissionSnapshot.Role role;

    RolePermissionDetailMenu(IslandMenuManager manager,
                             RolePermissionSnapshot snapshot,
                             RolePermissionSnapshot.Role role) {
        super(manager);
        this.snapshot = snapshot;
        this.role = role;
    }

    @Override
    protected String title(Player player) {
        return ChatColor.GREEN + role.displayName() + ChatColor.DARK_GRAY + " | 권한";
    }

    @Override
    protected int size() {
        return 54;
    }

    @Override
    protected void build(Player player, Inventory inventory) {
        decorateDefault(inventory);
        placeNavigation(backButton("권한 관리"), headerItem(), mainMenuButton());

        List<RolePermissionSnapshot.Privilege> privileges = role.privileges();
        if (privileges.isEmpty()) {
            setItem(22, icon(Material.BARRIER,
                    "&c표시할 권한이 없습니다",
                    "&7이 역할에는 정의된 권한이 없습니다."));
            return;
        }

        int[] slots = primarySlots();
        for (int i = 0; i < privileges.size() && i < slots.length; i++) {
            setItem(slots[i], privilegeIcon(privileges.get(i)));
        }
    }

    private org.bukkit.inventory.ItemStack headerItem() {
        long enabled = role.enabledCount();
        int total = role.totalPrivileges();
        String manage = snapshot.canManage()
                ? ChatColor.GRAY + "권한을 클릭하여 전환할 수 있습니다."
                : ChatColor.RED + "권한을 변경할 권한이 없습니다.";
        return icon(Material.BOOK,
                ChatColor.AQUA + role.displayName(),
                ChatColor.YELLOW + "활성화 " + enabled + ChatColor.GRAY + " / " + ChatColor.YELLOW + total,
                manage);
    }

    private org.bukkit.inventory.ItemStack privilegeIcon(RolePermissionSnapshot.Privilege privilege) {
        boolean enabled = privilege.enabled();
        Material material = enabled ? Material.LIME_DYE : Material.RED_DYE;
        String state = enabled ? ChatColor.GREEN + "허용" : ChatColor.RED + "차단";
        String action = snapshot.canManage()
                ? ChatColor.YELLOW + "클릭하여 상태를 전환합니다."
                : ChatColor.RED + "변경 권한이 없습니다.";
        return icon(material,
                ChatColor.GOLD + formatPrivilegeName(privilege.name()),
                ChatColor.GRAY + "현재 상태: " + state,
                "",
                action);
    }

    private String formatPrivilegeName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "알 수 없음";
        }
        return raw.replace('_', ' ');
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
        int headerSlot = size - 5;
        int mainSlot = size - 1;
        if (slot == backSlot) {
            manager().openRolePermissions(player);
            return;
        }
        if (slot == mainSlot) {
            manager().openMainMenu(player);
            return;
        }
        if (slot == headerSlot) {
            return;
        }
        int[] slots = primarySlots();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot && i < role.privileges().size()) {
                if (!snapshot.canManage()) {
                    player.sendMessage(ChatColor.RED + "[Skyblock] 해당 역할의 권한을 변경할 권한이 없습니다.");
                    return;
                }
                toggle(player, role.privileges().get(i), slot);
                return;
            }
        }
    }

    private void toggle(Player player, RolePermissionSnapshot.Privilege privilege, int slot) {
        boolean target = !privilege.enabled();
        setItem(slot, icon(Material.CLOCK, "&e적용 중...", "&7잠시만 기다려주세요."));
        manager().network().updateRolePermission(player, role.name(), privilege.name(), target)
                .thenAccept(result -> manager().plugin().getServer().getScheduler().runTask(manager().plugin(), () -> {
                    if (result == null || result.failed()) {
                        manager().notifyFailure(player, "권한 변경에 실패했습니다", result);
                        setItem(slot, privilegeIcon(privilege));
                        return;
                    }
                    privilege.setEnabled(target);
                    setItem(slot, privilegeIcon(privilege));
                    setItem(size() - 5, headerItem());
                    player.sendMessage(ChatColor.GOLD + "[Skyblock] " + role.displayName() + " 역할의 " + formatPrivilegeName(privilege.name()) + " 권한을 " + (target ? "허용" : "차단") + "했습니다.");
                }))
                .exceptionally(ex -> {
                    manager().plugin().getLogger().warning("Failed to update role permission: " + ex.getMessage());
                    manager().plugin().getServer().getScheduler().runTask(manager().plugin(), () -> {
                        String message = ex.getMessage() != null ? ex.getMessage() : "알 수 없음";
                        player.sendMessage(ChatColor.RED + "[Skyblock] 권한 변경에 실패했습니다: " + message);
                        setItem(slot, privilegeIcon(privilege));
                    });
                    return null;
                });
    }

}
