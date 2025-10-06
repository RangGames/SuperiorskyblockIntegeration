package wiki.creeper.superiorskyblockIntegeration.client.menu;

import com.google.gson.JsonObject;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import wiki.creeper.superiorskyblockIntegeration.api.NetworkOperationResult;

final class IslandInfoMenu extends AbstractMenu {

    IslandInfoMenu(IslandMenuManager manager) {
        super(manager);
    }

    @Override
    protected String title(Player player) {
        return ChatColor.AQUA + "팜 정보";
    }

    @Override
    protected int size() {
        return 27;
    }

    @Override
    protected void build(Player player, Inventory inventory) {
        fill(icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " "));
        setItem(13, icon(Material.COMPASS, "&e정보 로딩 중...", "&7잠시만 기다려주세요."));
    }

    @Override
    protected void onOpen(Player player) {
        manager().network().islandInfo(player, null).thenAccept(result ->
                runSync(() -> applyResult(player, result)));
    }

    private void applyResult(Player player, NetworkOperationResult result) {
        if (!isViewing(player)) {
            return;
        }
        if (result == null || result.failed()) {
            String code = result != null ? result.errorCode() : "UNKNOWN";
            String message = result != null ? result.errorMessage() : "오류가 발생했습니다.";
            setItem(13, icon(Material.BARRIER,
                    "&c정보를 불러오지 못했습니다",
                    "&7코드: " + code,
                    "&7설명: " + (message != null && !message.isBlank() ? message : "알 수 없음")));
            return;
        }

        JsonObject data = result.data();
        String islandName = data.has("islandName") ? data.get("islandName").getAsString() : "이름 없음";
        String ownerName = data.has("ownerName") ? data.get("ownerName").getAsString() : player.getName();
        java.util.UUID ownerUuid = null;
        if (data.has("ownerUuid")) {
            try {
                ownerUuid = java.util.UUID.fromString(data.get("ownerUuid").getAsString());
            } catch (IllegalArgumentException ignored) {
                ownerUuid = null;
            }
        }
        double level = data.has("level") ? data.get("level").getAsDouble() : 0.0D;
        int membersCount = data.has("membersCount") ? data.get("membersCount").getAsInt() : 0;
        int membersLimit = data.has("membersLimit") ? data.get("membersLimit").getAsInt() : 0;

        setItem(11, playerHead(ownerUuid, ownerName,
                "&a&l팜장",
                "&f" + ownerName));
        setItem(13, icon(Material.GRASS_BLOCK,
                "&a&l" + islandName,
                "&7레벨: &f" + String.format("%.2f", level),
                "&7멤버: &f" + membersCount + (membersLimit > 0 ? " / " + membersLimit : "")));
        String islandId = data.has("islandId") ? data.get("islandId").getAsString() : "알 수 없음";
        setItem(15, icon(Material.PAPER,
                "&a&l식별자",
                "&7ID: &f" + islandId));
    }
}
