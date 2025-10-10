package wiki.creeper.superiorskyblockIntegeration.client.menu;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import wiki.creeper.superiorskyblockIntegeration.api.NetworkOperationResult;

final class InviteMenu extends AbstractMenu {

    private static final int MANUAL_INPUT_SLOT = 49;

    InviteMenu(IslandMenuManager manager) {
        super(manager);
    }

    @Override
    protected String title(Player player) {
        return ChatColor.GREEN + "초대 보내기";
    }

    @Override
    protected int size() {
        return 54;
    }

    @Override
    protected void build(Player player, Inventory inventory) {
        decorateDefault(inventory);
        placeNavigation(backButton("메인 메뉴"), null, mainMenuButton());
        populateQuickTargets(player);
        setManualInviteItem();
    }

    private void populateQuickTargets(Player viewer) {
        Inventory inventory = inventory();
        if (inventory == null) {
            return;
        }
        int[] slots = primarySlots();
        int index = 0;
        for (Player candidate : manager().plugin().getServer().getOnlinePlayers()) {
            if (candidate.equals(viewer)) {
                continue;
            }
            if (index >= slots.length) {
                break;
            }
            int slot = slots[index++];
            setItem(slot, withStringTag(playerHead(candidate.getUniqueId(), candidate.getName(),
                    "&a" + candidate.getName(),
                    "&7온라인", "", "&e클릭 &7초대 전송"),
                    "invite-target", candidate.getName()));
        }
    }

    private void setManualInviteItem() {
        setItem(MANUAL_INPUT_SLOT, icon(Material.NAME_TAG,
                "&a&l닉네임 직접 입력",
                "&7다른 서버의 플레이어도 초대할 수 있습니다.",
                "&7클릭 후 채팅에 닉네임을 입력하세요."));
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
        int mainSlot = size - 1;
        if (slot == backSlot) {
            manager().openMainMenu(player);
            return;
        }
        if (slot == mainSlot) {
            manager().openMainMenu(player);
            return;
        }
        if (slot == MANUAL_INPUT_SLOT) {
            manager().beginInvitePrompt(player,
                    () -> manager().openMenu(player, new InviteMenu(manager())),
                    input -> sendInvite(player, input.trim(), MANUAL_INPUT_SLOT));
            player.closeInventory();
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) {
            return;
        }
        String target = readStringTag(clicked, "invite-target");
        if (target == null) {
            return;
        }
        sendInvite(player, target, slot);
    }

    private void sendInvite(Player player, String target, int slot) {
        if (target == null || target.isBlank()) {
            player.sendMessage(ChatColor.RED + "[Skyblock] 닉네임이 올바르지 않습니다.");
            manager().openMenu(player, new InviteMenu(manager()));
            return;
        }
        if (isViewing(player)) {
            setItem(slot, icon(Material.CLOCK, "&e초대 전송 중", "&7대상: &f" + target));
        }
        manager().network().invite(player, target).thenAccept(result ->
                runSync(() -> handleInviteResult(player, target, result)));
    }

    private void handleInviteResult(Player player, String target, NetworkOperationResult result) {
        if (result != null && result.success()) {
            player.sendMessage(ChatColor.GOLD + "[Skyblock] " + target + "님에게 초대를 전송했습니다.");
        } else {
            String code = result != null ? result.errorCode() : "UNKNOWN";
            String message = result != null ? result.errorMessage() : "오류가 발생했습니다.";
            player.sendMessage(ChatColor.RED + "[Skyblock] 초대 실패: " + code + " (" + message + ")");
        }
        manager().openMenu(player, new InviteMenu(manager()));
    }
}
