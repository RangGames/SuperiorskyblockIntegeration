package wiki.creeper.superiorskyblockIntegeration.client.menu;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import wiki.creeper.superiorskyblockIntegeration.api.NetworkOperationResult;

/**
 * Menu that allows players to toggle and recolor their island world border.
 */
public final class BorderMenu extends AbstractMenu {

    private final boolean enabled;
    private final String color;

    public BorderMenu(IslandMenuManager manager, boolean enabled, String color) {
        super(manager);
        this.enabled = enabled;
        this.color = color != null ? color.toUpperCase() : "GREEN";
    }

    @Override
    protected String title(Player player) {
        return ChatColor.GREEN + "팜 경계선 설정";
    }

    @Override
    protected int size() {
        return 45;
    }

    @Override
    protected void build(Player player, Inventory inventory) {
        decorateDefault(inventory);
        String status = enabled ? ChatColor.GREEN + "활성화됨" : ChatColor.RED + "비활성화됨";
        Material toggleMaterial = enabled ? Material.LIME_DYE : Material.RED_DYE;
        setItem(21, icon(toggleMaterial,
                ChatColor.YELLOW + "경계선 토글",
                ChatColor.WHITE + "현재 상태: " + status,
                "",
                ChatColor.GRAY + "클릭 시 경계선을 " + (enabled ? "비활성화" : "활성화") + " 합니다."));

        setItem(23, icon(Material.PAPER,
                ChatColor.AQUA + "현재 색상",
                ChatColor.WHITE + colorLabel(color)));

        setItem(29, createColorIcon("GREEN", Material.LIME_STAINED_GLASS_PANE));
        setItem(31, createColorIcon("RED", Material.RED_STAINED_GLASS_PANE));
        setItem(33, createColorIcon("BLUE", Material.BLUE_STAINED_GLASS_PANE));

        placeNavigation(backButton("메인 메뉴"), null, mainMenuButton());
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
            manager().openMainMenu(player);
            return;
        }
        if (slot == mainSlot) {
            manager().openMainMenu(player);
            return;
        }

        switch (slot) {
            case 21 -> toggle(player);
            case 29 -> setColor(player, "GREEN");
            case 31 -> setColor(player, "RED");
            case 33 -> setColor(player, "BLUE");
            default -> {
            }
        }
    }

    private void toggle(Player player) {
        manager().network().toggleWorldBorder(player).thenAccept(result ->
                runSync(() -> handleResult(player, result))).exceptionally(ex -> {
            runSync(() -> notifyFailure(player, ex.getMessage()));
            return null;
        });
    }

    private void setColor(Player player, String targetColor) {
        manager().network().setWorldBorderColor(player, targetColor).thenAccept(result ->
                runSync(() -> handleResult(player, result))).exceptionally(ex -> {
            runSync(() -> notifyFailure(player, ex.getMessage()));
            return null;
        });
    }

    private void handleResult(Player player, NetworkOperationResult result) {
        if (result == null) {
            return;
        }
        if (result.failed()) {
            notifyFailure(player, result.errorMessage());
            return;
        }
        manager().openBorderMenu(player);
    }

    private void notifyFailure(Player player, String message) {
        String detail = (message != null && !message.isBlank()) ? message : "요청을 처리하지 못했습니다.";
        player.sendMessage(ChatColor.RED + "[Skyblock] " + detail);
    }

    private String colorLabel(String raw) {
        return switch (raw == null ? "" : raw.toUpperCase()) {
            case "RED" -> "빨간색";
            case "BLUE" -> "파란색";
            default -> "연두색";
        };
    }

    private ItemStack createColorIcon(String targetColor, Material material) {
        boolean selected = targetColor.equalsIgnoreCase(color);
        return icon(material,
                ChatColor.YELLOW + "경계선 색상 - " + colorLabel(targetColor) + (selected ? ChatColor.GRAY + " (선택됨)" : ""),
                ChatColor.GRAY + "클릭 시 색상을 변경합니다.");
    }
}
