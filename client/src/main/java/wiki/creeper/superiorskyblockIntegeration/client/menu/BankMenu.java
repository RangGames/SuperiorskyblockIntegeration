package wiki.creeper.superiorskyblockIntegeration.client.menu;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import wiki.creeper.superiorskyblockIntegeration.client.model.BankSnapshot;

/**
 * Simple island bank controller menu (balance, deposit, withdraw).
 */
final class BankMenu extends AbstractMenu {

    private final BankSnapshot snapshot;

    BankMenu(IslandMenuManager manager, BankSnapshot snapshot) {
        super(manager);
        this.snapshot = snapshot;
    }

    @Override
    protected String title(Player player) {
        String name = snapshot.islandName() != null ? snapshot.islandName() : "팜";
        return ChatColor.GREEN + "팜 은행 - " + name;
    }

    @Override
    protected int size() {
        return 45;
    }

    @Override
    protected void build(Player player, Inventory inventory) {
        decorateDefault(inventory);
        placeNavigation(backButton("메인 메뉴"), null, mainMenuButton());

        String balanceText = manager().formatMoney(snapshot.balance());
        String title = ChatColor.GOLD + "현재 잔액";
        if (snapshot.islandName() != null) {
            title += ChatColor.GRAY + " (" + snapshot.islandName() + ")";
        }
        setItem(22, icon(Material.GOLD_INGOT,
                title,
                ChatColor.YELLOW + balanceText));

        if (snapshot.hasLimit()) {
            setItem(24, icon(Material.PAPER,
                    ChatColor.AQUA + "은행 한도",
                    ChatColor.YELLOW + manager().formatMoney(snapshot.limit())));
        }

        Material lockMaterial = snapshot.locked() ? Material.REDSTONE_BLOCK : Material.LIME_DYE;
        String lockState = snapshot.locked() ? ChatColor.RED + "잠금" : ChatColor.GREEN + "해제";
        String manageHint = snapshot.canManageLock()
                ? ChatColor.YELLOW + "클릭하여 잠금을 " + (snapshot.locked() ? "해제" : "설정") + "합니다."
                : ChatColor.GRAY + "팜장만 잠금 상태를 변경할 수 있습니다.";
        setItem(20, icon(lockMaterial,
                ChatColor.AQUA + "금고 잠금",
                ChatColor.GRAY + "현재 상태: " + lockState,
                manageHint));

        setItem(30, icon(Material.EMERALD,
                ChatColor.GREEN + "입금",
                ChatColor.GRAY + "사인 입력창에 금액을 입력해 은행에 입금합니다."));

        setItem(32, icon(Material.REDSTONE,
                ChatColor.RED + "출금",
                snapshot.locked()
                        ? ChatColor.RED + "현재 잠금 상태에서는 출금할 수 없습니다."
                        : ChatColor.GRAY + "사인 입력창에 금액을 입력해 은행에서 출금합니다."));

        setItem(40, icon(Material.WRITABLE_BOOK,
                ChatColor.GOLD + "거래 기록",
                ChatColor.GRAY + "최근 거래 내역을 확인합니다."));
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
        int lockSlot = 20;
        int depositSlot = 30;
        int withdrawSlot = 32;
        int historySlot = 40;
        if (slot == backSlot) {
            manager().openMainMenu(player);
            return;
        }
        if (slot == mainSlot) {
            manager().openMainMenu(player);
            return;
        }
        if (slot == lockSlot) {
            if (snapshot.canManageLock()) {
                manager().setBankLock(player, !snapshot.locked(), () -> manager().openBankMenu(player));
            } else {
                player.sendMessage(ChatColor.RED + "[Skyblock] 금고 잠금은 팜장만 변경할 수 있습니다.");
            }
            return;
        }
        if (slot == depositSlot) {
            manager().beginBankPrompt(player, IslandMenuManager.BankPromptType.DEPOSIT,
                    () -> manager().openBankMenu(player));
            return;
        }
        if (slot == withdrawSlot) {
            if (snapshot.locked()) {
                player.sendMessage(ChatColor.RED + "[Skyblock] 금고가 잠겨 있어 출금할 수 없습니다.");
                return;
            }
            manager().beginBankPrompt(player, IslandMenuManager.BankPromptType.WITHDRAW,
                    () -> manager().openBankMenu(player));
            return;
        }
        if (slot == historySlot) {
            manager().openBankHistory(player, 1);
        }
    }
}
