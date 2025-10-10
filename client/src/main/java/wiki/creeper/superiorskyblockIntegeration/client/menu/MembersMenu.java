package wiki.creeper.superiorskyblockIntegeration.client.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import wiki.creeper.superiorskyblockIntegeration.api.NetworkOperationResult;
import wiki.creeper.superiorskyblockIntegeration.client.services.ClientHeadDataService;
import wiki.creeper.superiorskyblockIntegeration.client.services.PlayerPresenceService;

final class MembersMenu extends AbstractMenu {

    private final List<MemberEntry> entries = new ArrayList<>();

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
        decorateDefault(inventory);
        setItem(22, icon(Material.COMPASS, "&e멤버 정보를 불러오는 중", "&7잠시만 기다려주세요."));
        placeNavigation(backButton("메인 메뉴"), icon(Material.WRITABLE_BOOK,
                "&b초대함",
                "&7받은 초대를 열어봅니다."), refreshButton());
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
        int refreshSlot = size - 1;
        int coopSlot = size - 8;
        int banSlot = size - 2;
        if (slot == backSlot) {
            manager().openMainMenu(player);
            return;
        }
        if (slot == middleSlot) {
            manager().openPendingInvites(player);
            return;
        }
        if (slot == refreshSlot) {
            manager().openMembersMenu(player);
            return;
        }
        if (slot == coopSlot) {
            manager().openCoopMenu(player);
            return;
        }
        if (slot == banSlot) {
            manager().openBanMenu(player);
            return;
        }
        int[] primary = primarySlots();
        for (int i = 0; i < primary.length; i++) {
            if (primary[i] != slot) {
                continue;
            }
            if (i >= entries.size()) {
                return;
            }
            MemberEntry entry = entries.get(i);
            sendMemberSummary(player, entry);
            return;
        }
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
        decorateDefault(inventory);
        placeNavigation(backButton("메인 메뉴"), icon(Material.WRITABLE_BOOK,
                "&b초대함",
                "&7받은 초대를 열어봅니다."), refreshButton());
        int coopSlot = inventory.getSize() - 8;
        int banSlot = inventory.getSize() - 2;
        setItem(coopSlot, icon(Material.LIME_DYE,
                "&a알바 관리",
                "&7알바 목록을 확인하고 관리합니다."));
        setItem(banSlot, icon(Material.REDSTONE,
                "&c차단 관리",
                "&7차단된 플레이어를 관리합니다."));

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
        JsonArray membersArray = data.has("members") && data.get("members").isJsonArray()
                ? data.getAsJsonArray("members") : new JsonArray();

        entries.clear();
        for (JsonElement element : membersArray) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject member = element.getAsJsonObject();
            String name = member.has("name") && !member.get("name").isJsonNull()
                    ? member.get("name").getAsString()
                    : "알 수 없음";
            String role = member.has("role") && !member.get("role").isJsonNull()
                    ? member.get("role").getAsString()
                    : "UNKNOWN";
            int roleWeight = member.has("roleWeight") && !member.get("roleWeight").isJsonNull()
                    ? member.get("roleWeight").getAsInt()
                    : Integer.MAX_VALUE;
            UUID uuid = parseUuid(member, "uuid");
            boolean online = member.has("online") && !member.get("online").isJsonNull()
                    ? member.get("online").getAsBoolean()
                    : uuid != null && manager().plugin().getServer().getPlayer(uuid) != null;
            String server = member.has("server") && !member.get("server").isJsonNull()
                    ? member.get("server").getAsString()
                    : null;
            String texture = member.has("skinTexture") && !member.get("skinTexture").isJsonNull()
                    ? member.get("skinTexture").getAsString()
                    : null;
            entries.add(new MemberEntry(uuid, name, role, roleWeight, online, server, texture));
        }

        if (entries.isEmpty()) {
            setItem(22, icon(Material.MAP, "&7멤버가 없습니다", "&7초대한 구성원이 없습니다."));
            return;
        }

        entries.sort(Comparator
                .comparingInt(MemberEntry::roleWeight).reversed()
                .thenComparing(MemberEntry::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(MemberEntry::name));

        int[] slots = primarySlots();
        PlayerPresenceService presenceService = manager().presenceService().orElse(null);
        ClientHeadDataService headDataService = manager().headDataService().orElse(null);

        for (int i = 0; i < entries.size() && i < slots.length; i++) {
            final int entryIndex = i;
            final int currentSlot = slots[i];
            MemberEntry entry = entries.get(i);
            setItem(currentSlot, buildMemberItem(entry.uuid(), entry.name(), entry.role(), entry.online(), entry.server(), entry.texture()));

            if (presenceService != null && entry.uuid() != null) {
                presenceService.lookup(entry.uuid()).thenAccept(presence -> runSync(() -> {
                    if (!isViewing(player)) {
                        return;
                    }
                    if (entryIndex >= entries.size()) {
                        return;
                    }
                    MemberEntry currentEntry = entries.get(entryIndex);
                    MemberEntry updatedEntry = currentEntry.withPresence(presence.online(), presence.server());
                    entries.set(entryIndex, updatedEntry);
                    setItem(currentSlot, buildMemberItem(updatedEntry.uuid(), updatedEntry.name(), updatedEntry.role(), updatedEntry.online(), updatedEntry.server(), updatedEntry.texture()));
                }));
            }

            if (headDataService != null && (entry.texture() == null || entry.texture().isBlank()) && entry.uuid() != null) {
                headDataService.requestHeadData(entry.uuid(), entry.name(), newTexture -> {
                    if (newTexture == null || newTexture.isBlank()) {
                        return;
                    }
                    runSync(() -> {
                        if (!isViewing(player)) {
                            return;
                        }
                        if (entryIndex >= entries.size()) {
                            return;
                        }
                        MemberEntry currentEntry = entries.get(entryIndex);
                        MemberEntry updatedEntry = currentEntry.withTexture(newTexture);
                        entries.set(entryIndex, updatedEntry);
                        setItem(currentSlot, buildMemberItem(updatedEntry.uuid(), updatedEntry.name(), updatedEntry.role(), updatedEntry.online(), updatedEntry.server(), updatedEntry.texture()));
                    });
                });
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

    private ItemStack buildMemberItem(UUID uuid,
                                      String name,
                                      String role,
                                      boolean online,
                                      String server,
                                      String texture) {
        String status = "&7상태: " + (online ? "&a온라인" : "&c오프라인");
        String friendlyServer = formatServerName(server);
        if (friendlyServer != null && !friendlyServer.isBlank()) {
            status += " &8(" + friendlyServer + ")";
        }
        ItemStack head = playerHead(uuid, name,
                "&a" + name,
                "&7등급: &f" + formatRole(role),
                status);
        if (texture == null || texture.isBlank()) {
            return head;
        }
        return applyTexture(head, uuid, name, texture);
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
            GameProfile legacy = new GameProfile(uuid != null ? uuid : UUID.randomUUID(), name != null ? name : "unknown");
            legacy.getProperties().put("textures", new Property("textures", texture));
            try {
                Method setProfile = skullMeta.getClass().getDeclaredMethod("setProfile", GameProfile.class);
                setProfile.setAccessible(true);
                setProfile.invoke(skullMeta, legacy);
            } catch (NoSuchMethodException methodMissing) {
                Field profileField = skullMeta.getClass().getDeclaredField("profile");
                profileField.setAccessible(true);
                profileField.set(skullMeta, legacy);
            }
            clone.setItemMeta(skullMeta);
            return clone;
        } catch (Exception ex) {
            manager().plugin().getLogger().log(Level.FINE, "Failed to apply skin texture for " + name, ex);
            return original;
        }
    }

    private String formatRole(String role) {
        if (role == null || role.isBlank()) {
            return "알 수 없음";
        }
        String normalized = role.replace('_', ' ').toLowerCase(Locale.KOREAN);
        StringBuilder builder = new StringBuilder();
        for (String word : normalized.split(" ")) {
            if (word.isBlank()) {
                continue;
            }
            builder.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                builder.append(word.substring(1));
            }
            builder.append(' ');
        }
        return builder.isEmpty() ? role : builder.toString().trim();
    }

    private String formatServerName(String server) {
        if (server == null || server.isBlank()) {
            return null;
        }
        String normalized = server.replace('_', ' ').replace('-', ' ');
        StringBuilder builder = new StringBuilder();
        for (String word : normalized.split(" ")) {
            if (word.isBlank()) {
                continue;
            }
            builder.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                builder.append(word.substring(1));
            }
            builder.append(' ');
        }
        return builder.isEmpty() ? server : builder.toString().trim();
    }

    private void sendMemberSummary(Player player, MemberEntry entry) {
        String prefix = ChatColor.GOLD + "[Skyblock] " + ChatColor.WHITE;
        String role = formatRole(entry.role());
        String status = entry.online() ? ChatColor.GREEN + "온라인" : ChatColor.RED + "오프라인";
        String server = formatServerName(entry.server());
        player.sendMessage(prefix + entry.name());
        player.sendMessage(ChatColor.YELLOW + " - 역할: " + ChatColor.WHITE + role);
        player.sendMessage(ChatColor.YELLOW + " - 상태: " + status +
                (server != null ? ChatColor.GRAY + " (" + server + ")" : ""));
    }

    private record MemberEntry(UUID uuid,
                               String name,
                               String role,
                               int roleWeight,
                               boolean online,
                               String server,
                               String texture) {
        MemberEntry withPresence(boolean online, String server) {
            return new MemberEntry(uuid, name, role, roleWeight, online, server, texture);
        }

        MemberEntry withTexture(String texture) {
            return new MemberEntry(uuid, name, role, roleWeight, online, server, texture);
        }
    }
}
