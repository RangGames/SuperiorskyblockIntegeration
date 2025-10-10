package wiki.creeper.superiorskyblockIntegeration.client.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

final class WarpBrowserMenu extends AbstractMenu {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withLocale(Locale.KOREA)
            .withZone(ZoneId.systemDefault());

    private final int page;
    private final int pageSize;
    private final int total;
    private final List<IslandEntry> entries;

    WarpBrowserMenu(IslandMenuManager manager, JsonObject payload) {
        super(manager);
        Objects.requireNonNull(payload, "payload");
        this.page = payload.has("page") ? Math.max(1, payload.get("page").getAsInt()) : 1;
        this.pageSize = payload.has("pageSize") ? Math.max(1, payload.get("pageSize").getAsInt()) : 36;
        this.total = payload.has("total") ? Math.max(0, payload.get("total").getAsInt()) : 0;
        this.entries = parseEntries(payload.has("islands") ? payload.getAsJsonArray("islands") : new JsonArray());
    }

    @Override
    protected String title(Player player) {
        return ChatColor.translateAlternateColorCodes('&', "&a&l팜 워프 목록");
    }

    @Override
    protected int size() {
        return 54;
    }

    @Override
    protected void build(Player player, Inventory inventory) {
        decorateDefault(inventory);
        int[] slots = primarySlots();
        for (int i = 0; i < slots.length && i < entries.size(); i++) {
            setItem(slots[i], createIslandIcon(entries.get(i), player));
        }
        int prevSlot = size() - 9;
        int indicatorSlot = size() - 5;
        int nextSlot = size() - 1;
        ItemStack previous = page > 1
                ? icon(Material.ARROW, "&a이전 페이지", "&7페이지 " + (page - 1))
                : glass(Material.GRAY_STAINED_GLASS_PANE);
        ItemStack indicator = icon(Material.PAPER, "&f현재 페이지 &a" + page,
                "&7총 " + total + "개의 공개 팜");
        boolean hasNext = page * pageSize < total;
        ItemStack next = hasNext
                ? icon(Material.ARROW, "&a다음 페이지", "&7페이지 " + (page + 1))
                : glass(Material.GRAY_STAINED_GLASS_PANE);
        setItem(prevSlot, previous);
        setItem(indicatorSlot, indicator);
        setItem(nextSlot, next);
        setItem(size() - 4, mainMenuButton());
    }

    @Override
    protected void onClick(Player player, InventoryClickEvent event) {
        ClickType click = event.getClick();
        int slot = event.getSlot();
        int[] slots = primarySlots();
        for (int i = 0; i < slots.length && i < entries.size(); i++) {
            if (slot == slots[i]) {
                IslandEntry entry = entries.get(i);
                if (entry.ownerUuid() != null && entry.ownerUuid().equals(player.getUniqueId())) {
                    manager().openHomeWarps(player);
                } else {
                    manager().openPlayerWarps(player, Optional.ofNullable(entry.ownerUuid()).map(UUID::toString).orElse(entry.islandId()));
                }
                return;
            }
        }

        if (slot == size() - 9 && click == ClickType.LEFT && page > 1) {
            manager().openGlobalWarpBrowser(player, page - 1);
        } else if (slot == size() - 1 && click == ClickType.LEFT && page * pageSize < total) {
            manager().openGlobalWarpBrowser(player, page + 1);
        } else if (slot == size() - 4 && click == ClickType.LEFT) {
            manager().openMainMenu(player);
        }
    }

    private ItemStack createIslandIcon(IslandEntry entry, Player viewer) {
        List<String> lore = new ArrayList<>();
        lore.add("" );
        lore.add("&a&l[팜장] &f" + entry.ownerName());
        lore.add("&a&l[평가] &f" + String.format(Locale.KOREA, "%.1f", entry.rating()));
        lore.add("&a&l[인원] &f" + entry.memberCount() + "명");
        lore.add("&a&l[생성 일시] &f" + DATE_FORMAT.format(Instant.ofEpochMilli(entry.creation())));
        lore.add("&a&l[공개 홈 수] &f" + entry.warpCount());
        lore.add("" );
        lore.add("&7좌클릭: 해당 팜의 홈 목록 열기");
        if (entry.ownerUuid() != null && entry.ownerUuid().equals(viewer.getUniqueId())) {
            lore.add("&a(내 팜)");
        }
        ItemStack base;
        if (entry.ownerUuid() != null) {
            base = playerHead(entry.ownerUuid(), entry.ownerName(), "&a&l[팜] &f" + entry.displayName(), lore.toArray(new String[0]));
        } else {
            base = icon(Material.GRASS_BLOCK, "&a&l[팜] &f" + entry.displayName(), lore.toArray(new String[0]));
        }
        return base;
    }

    private List<IslandEntry> parseEntries(JsonArray array) {
        List<IslandEntry> list = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject obj = element.getAsJsonObject();
            String islandId = obj.has("islandId") ? obj.get("islandId").getAsString() : null;
            String islandName = obj.has("islandName") && !obj.get("islandName").isJsonNull()
                    ? obj.get("islandName").getAsString()
                    : "이름 없음";
            UUID ownerUuid = null;
            if (obj.has("ownerUuid") && !obj.get("ownerUuid").isJsonNull()) {
                try {
                    ownerUuid = UUID.fromString(obj.get("ownerUuid").getAsString());
                } catch (IllegalArgumentException ignored) {
                    ownerUuid = null;
                }
            }
            String ownerName = obj.has("ownerName") && !obj.get("ownerName").isJsonNull()
                    ? obj.get("ownerName").getAsString()
                    : "알 수 없음";
            double rating = obj.has("totalRating") ? obj.get("totalRating").getAsDouble() : 0.0D;
            int members = obj.has("members") ? obj.get("members").getAsInt() : 0;
            long creation = obj.has("creation") ? obj.get("creation").getAsLong() : 0L;
            int warpCount = obj.has("warpCount") ? obj.get("warpCount").getAsInt() : 0;
            list.add(new IslandEntry(islandId, islandName, ownerUuid, ownerName, rating, members, creation, warpCount));
        }
        return list;
    }

    private record IslandEntry(String islandId,
                               String displayName,
                               UUID ownerUuid,
                               String ownerName,
                               double rating,
                               int memberCount,
                               long creation,
                               int warpCount) {
    }
}
