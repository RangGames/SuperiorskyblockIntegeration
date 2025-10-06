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
        return 27;
    }

    @Override
    protected void build(Player player, Inventory inventory) {
        fill(icon(Material.GRAY_STAINED_GLASS_PANE, " "));

        String status = enabled ? ChatColor.GREEN + "활성화됨" : ChatColor.RED + "비활성화됨";
        Material toggleMaterial = enabled ? Material.LIME_DYE : Material.RED_DYE;
        setItem(11, icon(toggleMaterial,
                ChatColor.YELLOW + "경계선 토글",
                ChatColor.WHITE + "현재 상태: " + status,
                "",
                ChatColor.GRAY + "클릭 시 경계선을 "+ (enabled ? "비활성화" : "활성화") + " 합니다."));

        setItem(13, icon(Material.PAPER,
                ChatColor.AQUA + "현재 색상",
                ChatColor.WHITE + colorLabel(color)));

        setItem(15, icon(Material.ARROW, ChatColor.GREEN + "돌아가기", ChatColor.GRAY + "메인 메뉴로 돌아갑니다."));

        setItem(19, createColorIcon("GREEN", Material.LIME_STAINED_GLASS_PANE));
        setItem(21, createColorIcon("RED", Material.RED_STAINED_GLASS_PANE));
        setItem(23, createColorIcon("BLUE", Material.BLUE_STAINED_GLASS_PANE));
    }

    @Override
    protected void onClick(Player player, InventoryClickEvent event) {
        int slot = event.getRawSlot();
        switch (slot) {
            case 11 -> toggle(player);
            case 15 -> manager().openMainMenu(player);
            case 19 -> setColor(player, "GREEN");
            case 21 -> setColor(player, "RED");
            case 23 -> setColor(player, "BLUE");
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
