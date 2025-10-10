package wiki.creeper.superiorskyblockIntegeration.client.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import wiki.creeper.superiorskyblockIntegeration.api.NetworkOperationResult;

final class PendingInvitesMenu extends AbstractMenu {

    PendingInvitesMenu(IslandMenuManager manager) {
        super(manager);
    }

    @Override
    protected String title(Player player) {
        return ChatColor.YELLOW + "받은 초대";
    }

    @Override
    protected int size() {
        return 54;
    }

    @Override
    protected void build(Player player, Inventory inventory) {
        decorateDefault(inventory);
        setItem(22, icon(Material.COMPASS, "&e초대 목록을 불러오는 중", "&7잠시만 기다려주세요."));
        placeNavigation(backButton("메인 메뉴"), icon(Material.NAME_TAG,
                "&b초대 보내기",
                "&7닉네임을 직접 입력해 초대를 보냅니다."), mainMenuButton());
    }

    @Override
    protected void onOpen(Player player) {
        manager().network().pendingInvites(player).thenAccept(result ->
                runSync(() -> applyResult(player, result)));
    }

    private void applyResult(Player player, NetworkOperationResult result) {
        if (!isViewing(player)) {
            return;
        }
        Inventory inventory = inventory();
        if (inventory == null) {
            return;
        }
        inventory.clear();
        decorateDefault(inventory);
        placeNavigation(backButton("메인 메뉴"), icon(Material.NAME_TAG,
                "&b초대 보내기",
                "&7닉네임을 직접 입력해 초대를 보냅니다."), mainMenuButton());

        if (result == null || result.failed()) {
            String code = result != null ? result.errorCode() : "UNKNOWN";
            String message = result != null ? result.errorMessage() : "오류가 발생했습니다.";
            setItem(22, icon(Material.BARRIER,
                    "&c초대 목록을 불러오지 못했습니다",
                    "&7코드: " + code,
                    "&7설명: " + (message != null && !message.isBlank() ? message : "알 수 없음")));
            return;
        }

        JsonObject data = result.data();
        JsonArray invites = data.has("invites") && data.get("invites").isJsonArray()
                ? data.getAsJsonArray("invites") : new JsonArray();
        if (invites.size() == 0) {
            setItem(22, icon(Material.MAP, "&7받은 초대가 없습니다", "&7초대를 받으면 이곳에 표시됩니다."));
            return;
        }

        int[] slots = primarySlots();
        int index = 0;
        for (JsonElement element : invites) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject invite = element.getAsJsonObject();
            InviteEntry entry = InviteEntry.from(invite);
            if (entry == null) {
                continue;
            }
            if (index >= slots.length) {
                break;
            }
            int slot = slots[index++];
            setItem(slot, withStringTag(icon(Material.BOOK,
                    "&a&l" + entry.islandName(),
                    "&7팜장: &f" + entry.ownerName(),
                    "&7멤버: &f" + entry.membersText(),
                    "",
                    "&e좌클릭 &7수락",
                    "&c우클릭 &7거절"),
                    "invite-id", entry.islandId()));
        }
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
        int middleSlot = size - 5;
        int mainSlot = size - 1;
        if (slot == backSlot) {
            manager().openMainMenu(player);
            return;
        }
        if (slot == middleSlot) {
            manager().openMenu(player, new InviteMenu(manager()));
            return;
        }
        if (slot == mainSlot) {
            manager().openMainMenu(player);
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) {
            return;
        }
        String inviteId = readStringTag(clicked, "invite-id");
        if (inviteId == null) {
            return;
        }
        if (event.isLeftClick()) {
            processInvite(player, slot, inviteId, true);
        } else if (event.isRightClick()) {
            processInvite(player, slot, inviteId, false);
        }
    }

    private void processInvite(Player player, int slot, String inviteId, boolean accept) {
        setItem(slot, icon(Material.CLOCK,
                accept ? "&e초대를 수락 중" : "&e초대를 거절 중",
                "&7잠시만 기다려주세요."));

        (accept ? manager().network().acceptInvite(player, inviteId)
                : manager().network().denyInvite(player, inviteId))
                .thenAccept(result -> runSync(() -> handleInviteResult(player, result, accept)));
    }

    private void handleInviteResult(Player player, NetworkOperationResult result, boolean accept) {
        if (result != null && result.success()) {
            player.sendMessage(ChatColor.GOLD + "[Skyblock] " + (accept ? "초대를 수락했습니다." : "초대를 거절했습니다."));
        } else {
            String code = result != null ? result.errorCode() : "UNKNOWN";
            String message = result != null ? result.errorMessage() : "오류가 발생했습니다.";
            player.sendMessage(ChatColor.RED + "[Skyblock] 처리 실패: " + code + " (" + message + ")");
        }
        manager().openMenu(player, new PendingInvitesMenu(manager()));
    }

    private record InviteEntry(String islandId, String islandName, String ownerName, int members, int limit) {

        static InviteEntry from(JsonObject json) {
            if (!json.has("islandId")) {
                return null;
            }
            String id = json.get("islandId").getAsString();
            String name = json.has("islandName") ? json.get("islandName").getAsString() : "이름 없음";
            String owner = json.has("ownerName") ? json.get("ownerName").getAsString() : "알 수 없음";
            int members = json.has("membersCount") ? json.get("membersCount").getAsInt() : 0;
            int limit = json.has("membersLimit") ? json.get("membersLimit").getAsInt() : 0;
            return new InviteEntry(id, name, owner, members, limit);
        }

        String membersText() {
            return limit > 0 ? members + " / " + limit : Integer.toString(members);
        }

    }
}
