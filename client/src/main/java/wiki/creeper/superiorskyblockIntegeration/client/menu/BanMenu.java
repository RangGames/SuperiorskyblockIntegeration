package wiki.creeper.superiorskyblockIntegeration.client.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import wiki.creeper.superiorskyblockIntegeration.api.NetworkOperationResult;
import wiki.creeper.superiorskyblockIntegeration.client.model.PlayerSummary;

/**
 * Menu for managing banned players.
 */
final class BanMenu extends AbstractMenu {

    private final Map<Integer, PlayerSummary> slotEntries = new HashMap<>();

    BanMenu(IslandMenuManager manager) {
        super(manager);
    }

    @Override
    protected String title(Player player) {
        return ChatColor.RED + "팜 차단 관리";
    }

    @Override
    protected int size() {
        return 54;
    }

    @Override
    protected void build(Player player, Inventory inventory) {
        decorateDefault(inventory);
        setItem(22, icon(Material.COMPASS, "&e차단 정보를 불러오는 중", "&7잠시만 기다려주세요."));
        placeNavigation(backButton("메인 메뉴"), icon(Material.BARRIER,
                "&c플레이어 차단",
                "&7클릭하여 플레이어를 차단합니다."), mainMenuButton());
    }

    @Override
    protected void onOpen(Player player) {
        manager().network().listBannedPlayers(player).thenAccept(result ->
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
        placeNavigation(backButton("메인 메뉴"), icon(Material.BARRIER,
                "&c플레이어 차단",
                "&7클릭하여 플레이어를 차단합니다."), mainMenuButton());

        slotEntries.clear();

        if (result == null || result.failed()) {
            String code = result != null ? result.errorCode() : "UNKNOWN";
            String message = result != null ? result.errorMessage() : "오류가 발생했습니다.";
            setItem(22, icon(Material.BARRIER,
                    "&c차단 정보를 불러오지 못했습니다",
                    "&7코드: " + code,
                    "&7설명: " + (message != null && !message.isBlank() ? message : "알 수 없음")));
            return;
        }

        JsonObject data = result.data();
        JsonArray playersArray = data != null && data.has("players") && data.get("players").isJsonArray()
                ? data.getAsJsonArray("players")
                : new JsonArray();
        List<PlayerSummary> entries = PlayerSummary.from(playersArray);

        if (entries.isEmpty()) {
            setItem(22, icon(Material.MAP,
                    "&7차단된 플레이어가 없습니다",
                    "&7하단 버튼으로 새로운 차단을 추가할 수 있습니다."));
            return;
        }

        int[] slots = primarySlots();
        int index = 0;
        for (PlayerSummary summary : entries) {
            if (index >= slots.length) {
                break;
            }
            int slot = slots[index++];
            slotEntries.put(slot, summary);
            setItem(slot, buildEntryItem(summary));
            applyTextureAsync(slot, summary);
        }
    }

    private ItemStack buildEntryItem(PlayerSummary summary) {
        String status = summary.online()
                ? ChatColor.GREEN + "온라인"
                : ChatColor.RED + "오프라인";
        if (summary.server() != null && !summary.server().isBlank()) {
            status += ChatColor.GRAY + " @" + ChatColor.YELLOW + summary.server();
        }
        return playerHead(summary.uuid(), summary.name(),
                "&c" + summary.name(),
                "&7역할: &f" + summary.role(),
                "&7상태: " + status,
                "",
                "&a좌클릭: 차단 해제");
    }

    private void applyTextureAsync(int slot, PlayerSummary summary) {
        UUID uuid = summary.uuid();
        if (uuid == null) {
            return;
        }
        if (summary.skinTexture() != null && !summary.skinTexture().isBlank()) {
            ItemStack current = inventory() != null ? inventory().getItem(slot) : null;
            if (current == null) {
                return;
            }
            setItem(slot, applyTexture(current, uuid, summary.name(), summary.skinTexture()));
            return;
        }
        manager().headDataService().ifPresent(service ->
                service.requestHeadData(uuid, summary.name(), value -> {
                    if (value == null || value.isBlank()) {
                        return;
                    }
                    runSync(() -> {
                        Player currentViewer = viewer();
                        if (currentViewer == null || !slotEntries.containsKey(slot) || !isViewing(currentViewer)) {
                            return;
                        }
                        ItemStack current = inventory() != null ? inventory().getItem(slot) : null;
                        if (current == null) {
                            return;
                        }
                        setItem(slot, applyTexture(current, uuid, summary.name(), value));
                    });
                }));
    }

    private ItemStack applyTexture(ItemStack original, UUID uuid, String name, String texture) {
        if (texture == null || texture.isBlank()) {
            return original;
        }
        ItemStack clone = original.clone();
        ItemMeta meta = clone.getItemMeta();
        if (!(meta instanceof SkullMeta skullMeta)) {
            return original;
        }
        try {
            GameProfile profile = new GameProfile(uuid != null ? uuid : UUID.randomUUID(), name != null ? name : "ban");
            profile.getProperties().put("textures", new Property("textures", texture));
            Field profileField = skullMeta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(skullMeta, profile);
            clone.setItemMeta(skullMeta);
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            manager().plugin().getLogger().warning("Failed to apply head texture: " + ex.getMessage());
            return original;
        }
        return clone;
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
        int addSlot = size - 5;
        int mainSlot = size - 1;

        if (slot == backSlot) {
            manager().openMainMenu(player);
            return;
        }
        if (slot == addSlot) {
            manager().beginBanPrompt(player, () -> manager().openBanMenu(player));
            return;
        }
        if (slot == mainSlot) {
            manager().openMainMenu(player);
            return;
        }

        PlayerSummary summary = slotEntries.get(slot);
        if (summary == null) {
            return;
        }
        String identifier = summary.identifier();
        manager().network().removeBannedPlayer(player, identifier).thenAccept(result ->
                runSync(() -> {
                    if (result == null || result.failed()) {
                        manager().notifyFailure(player, "차단 해제에 실패했습니다", result);
                        return;
                    }
                    player.sendMessage(ChatColor.GREEN + "[Skyblock] " + summary.name() + "님의 차단을 해제했습니다.");
                    manager().openBanMenu(player);
                })
        ).exceptionally(ex -> {
            manager().plugin().getLogger().warning("Failed to unban player for " + player.getName() + ": " + ex.getMessage());
            runSync(() -> player.sendMessage(ChatColor.RED + "[Skyblock] 내부 오류로 차단 해제에 실패했습니다."));
            return null;
        });
    }
}
