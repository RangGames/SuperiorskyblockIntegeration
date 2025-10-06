package wiki.creeper.superiorskyblockIntegeration.client.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

import wiki.creeper.superiorskyblockIntegeration.api.NetworkOperationResult;

final class MembersMenu extends AbstractMenu {

    MembersMenu(IslandMenuManager manager) {
        super(manager);
    }

    @Override
    protected String title(Player player) {
        return ChatColor.GREEN + "팜 구성원";
    }

    @Override
    protected int size() {
        return 54;
    }

    @Override
    protected void build(Player player, Inventory inventory) {
        fill(icon(Material.BLACK_STAINED_GLASS_PANE, " "));
        setItem(22, icon(Material.COMPASS, "&e멤버 정보를 불러오는 중", "&7잠시만 기다려주세요."));
    }

    @Override
    protected void onOpen(Player player) {
        manager().network().listMembers(player, null).thenAccept(result ->
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
        if (result == null || result.failed()) {
            String code = result != null ? result.errorCode() : "UNKNOWN";
            String message = result != null ? result.errorMessage() : "오류가 발생했습니다.";
            setItem(22, icon(Material.BARRIER,
                    "&c멤버 목록을 불러오지 못했습니다",
                    "&7코드: " + code,
                    "&7설명: " + (message != null && !message.isBlank() ? message : "알 수 없음")));
            return;
        }

        JsonObject data = result.data();
        JsonArray members = data.has("members") && data.get("members").isJsonArray()
                ? data.getAsJsonArray("members") : new JsonArray();
        if (members.size() == 0) {
            setItem(22, icon(Material.MAP, "&7멤버가 없습니다", "&7초대한 구성원이 없습니다."));
            return;
        }

        int slot = 0;
        for (JsonElement element : members) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject member = element.getAsJsonObject();
            String name = member.has("name") ? member.get("name").getAsString() : "알 수 없음";
            String role = member.has("role") ? member.get("role").getAsString() : "UNKNOWN";
            UUID uuid = parseUuid(member, "uuid");
            boolean online = uuid != null && manager().plugin().getServer().getPlayer(uuid) != null;

            setItem(slot, playerHead(uuid, name,
                    "&a" + name,
                    "&7등급: &f" + role,
                    "&7상태: &f" + (online ? "온라인" : "오프라인")));
            slot++;
            if (slot >= inventory.getSize()) {
                break;
            }
        }
    }

    private UUID parseUuid(JsonObject object, String field) {
        if (!object.has(field) || object.get(field).isJsonNull()) {
            return null;
        }
        try {
            return UUID.fromString(object.get(field).getAsString());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}

