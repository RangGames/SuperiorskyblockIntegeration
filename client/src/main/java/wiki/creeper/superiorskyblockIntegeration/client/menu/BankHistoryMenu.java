package wiki.creeper.superiorskyblockIntegeration.client.menu;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import wiki.creeper.superiorskyblockIntegeration.client.model.BankHistoryPage;

/**
 * Visualises a paged list of island bank transactions.
 */
final class BankHistoryMenu extends AbstractMenu {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final BankHistoryPage history;

    BankHistoryMenu(IslandMenuManager manager, BankHistoryPage history) {
        super(manager);
        this.history = history;
    }

    @Override
    protected String title(Player player) {
        String name = history.islandName() != null ? history.islandName() : "팜";
        return ChatColor.GREEN + "팜 은행 기록 - " + name + ChatColor.GRAY + " (" + history.page() + "/" + history.totalPages() + ")";
    }

    @Override
    protected int size() {
        return 54;
    }

    @Override
    protected void build(Player player, Inventory inventory) {
        decorateDefault(inventory);

        int[] slots = primarySlots();
        if (history.entries().isEmpty()) {
            setItem(slots[0], icon(Material.BARRIER,
                    ChatColor.RED + "기록이 없습니다",
                    ChatColor.GRAY + "은행 거래 기록이 비어 있습니다."));
        } else {
            int index = 0;
            for (BankHistoryPage.Entry entry : history.entries()) {
                if (index >= slots.length) {
                    break;
                }
                setItem(slots[index], entryIcon(entry));
                index++;
            }
        }

        placeNavigation(previousButton(), closeButton(), nextButton());
    }

    private Material materialForAction(String action) {
        if (action == null) {
            return Material.PAPER;
        }
        return switch (action) {
            case "DEPOSIT_COMPLETED" -> Material.EMERALD;
            case "DEPOSIT_FAILED" -> Material.REDSTONE;
            case "WITHDRAW_COMPLETED" -> Material.GOLD_INGOT;
            case "WITHDRAW_FAILED" -> Material.REDSTONE_BLOCK;
            default -> Material.PAPER;
        };
    }

    private String actionLabel(String action) {
        if (action == null) {
            return "알 수 없음";
        }
        return switch (action) {
            case "DEPOSIT_COMPLETED" -> "입금";
            case "DEPOSIT_FAILED" -> "입금 실패";
            case "WITHDRAW_COMPLETED" -> "출금";
            case "WITHDRAW_FAILED" -> "출금 실패";
            default -> action;
        };
    }

    private String actionColor(String action) {
        if (action == null) {
            return ChatColor.GRAY.toString();
        }
        return switch (action) {
            case "DEPOSIT_COMPLETED", "WITHDRAW_COMPLETED" -> ChatColor.GREEN.toString();
            case "DEPOSIT_FAILED", "WITHDRAW_FAILED" -> ChatColor.RED.toString();
            default -> ChatColor.GRAY.toString();
        };
    }

    private String formatTimestamp(long epochMillis) {
        if (epochMillis <= 0L) {
            return "N/A";
        }
        return TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis));
    }

    private ItemStack entryIcon(BankHistoryPage.Entry entry) {
        Material material = materialForAction(entry.action());
        String actor = entry.playerName() != null ? entry.playerName() : "팜 시스템";
        String actionLabel = actionLabel(entry.action());
        String amount = manager().formatMoney(entry.amount());
        String actionColor = actionColor(entry.action());
        String title = actionColor + actionLabel + ChatColor.WHITE + " - " + actor;
        String positionLine = entry.position() >= 0 ? ChatColor.DARK_GRAY + "#" + entry.position() : null;
        String timestamp = formatTimestamp(entry.time());

        if (positionLine != null) {
            return icon(material,
                    title,
                    ChatColor.GRAY + "금액: " + ChatColor.YELLOW + amount,
                    ChatColor.GRAY + "시각: " + ChatColor.AQUA + timestamp,
                    ChatColor.DARK_GRAY + "식별자: " + positionLine,
                    failureLine(entry));
        }
        return icon(material,
                title,
                ChatColor.GRAY + "금액: " + ChatColor.YELLOW + amount,
                ChatColor.GRAY + "시각: " + ChatColor.AQUA + timestamp,
                failureLine(entry));
    }

    private String failureLine(BankHistoryPage.Entry entry) {
        if (entry.failureReason() == null || entry.failureReason().isBlank()) {
            return ChatColor.GREEN + "상태: 정상";
        }
        return ChatColor.RED + "상태: " + entry.failureReason();
    }

    private ItemStack previousButton() {
        if (!history.hasPrevious()) {
            return glass(Material.BLACK_STAINED_GLASS_PANE);
        }
        return icon(Material.ARROW,
                ChatColor.AQUA + "이전 페이지",
                ChatColor.GRAY + "페이지를 " + (history.page() - 1) + "로 이동합니다.");
    }

    private ItemStack nextButton() {
        if (!history.hasNext()) {
            return glass(Material.BLACK_STAINED_GLASS_PANE);
        }
        return icon(Material.ARROW,
                ChatColor.AQUA + "다음 페이지",
                ChatColor.GRAY + "페이지를 " + (history.page() + 1) + "로 이동합니다.");
    }

    @Override
    protected void onClick(Player player, InventoryClickEvent event) {
        super.onClick(player, event);
        int slot = event.getRawSlot();
        int size = inventory().getSize();
        int prevSlot = size - 9;
        int closeSlot = size - 5;
        int nextSlot = size - 1;

        if (slot == prevSlot && history.hasPrevious()) {
            manager().openBankHistory(player, history.page() - 1);
            return;
        }
        if (slot == nextSlot && history.hasNext()) {
            manager().openBankHistory(player, history.page() + 1);
            return;
        }
        if (slot == closeSlot) {
            manager().openBankMenu(player);
        }
    }
}
