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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class HomeWarpMenu extends AbstractMenu {

    private static final int[] WARP_SLOTS = {2, 6};

    private final String islandId;
    private final int maxWarps;
    private final List<WarpEntry> warps;

    HomeWarpMenu(IslandMenuManager manager, JsonObject payload) {
        super(manager);
        Objects.requireNonNull(payload, "payload");
        this.islandId = payload.has("islandId") && !payload.get("islandId").isJsonNull()
                ? payload.get("islandId").getAsString()
                : null;
        this.maxWarps = payload.has("maxWarps") ? payload.get("maxWarps").getAsInt() : WARP_SLOTS.length;
        this.warps = parseWarps(payload.has("warps") ? payload.getAsJsonArray("warps") : new JsonArray());
    }

    @Override
    protected String title(Player player) {
        return ChatColor.translateAlternateColorCodes('&', "&a&l홈 워프 관리");
    }

    @Override
    protected int size() {
        return 9;
    }

    @Override
    protected void build(Player player, Inventory inventory) {
        decorateDefault(inventory);
        setItem(0, icon(Material.PAPER, "&a홈 슬롯 현황", "&f" + warps.size() + "&7 / &f" + maxWarps));
        ItemStack placeholder = icon(Material.BARRIER, "&f&l[설정되지 않은 홈]", "&7/p팜 홈 설정 명령으로", "&7새로운 홈을 추가할 수 있습니다.");
        for (int slot : WARP_SLOTS) {
            setItem(slot, placeholder.clone());
        }
        for (int i = 0; i < Math.min(warps.size(), WARP_SLOTS.length); i++) {
            WarpEntry entry = warps.get(i);
            setItem(WARP_SLOTS[i], createWarpIcon(entry));
        }
        setItem(4, icon(Material.EMERALD, "&a새 홈 생성",
                "&7현재 위치를 홈으로 등록합니다.",
                "&7명령: /팜 홈 설정"));
        setItem(8, mainMenuButton());
    }

    @Override
    protected void onClick(Player player, InventoryClickEvent event) {
        int slot = event.getSlot();
        for (int i = 0; i < WARP_SLOTS.length; i++) {
            if (slot == WARP_SLOTS[i]) {
                WarpEntry entry = i < warps.size() ? warps.get(i) : null;
                if (entry == null) {
                    manager().requestHomeWarpCreation(player);
                    return;
                }
                ClickType click = event.getClick();
                if (click == ClickType.LEFT) {
                    manager().visitWarp(player, islandId, entry.name());
                } else if (click == ClickType.RIGHT) {
                    manager().toggleHomeWarpPrivacy(player, entry.name());
                } else if (click == ClickType.SHIFT_LEFT) {
                    manager().beginWarpRenamePrompt(player, entry.name());
                } else if (click == ClickType.SHIFT_RIGHT) {
                    manager().deleteHomeWarp(player, entry.name());
                }
                return;
            }
        }
        if (slot == 4) {
            manager().requestHomeWarpCreation(player);
        } else if (slot == 8) {
            manager().openMainMenu(player);
        }
    }

    private ItemStack createWarpIcon(WarpEntry entry) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("&a&l[공개 상태]");
        lore.add(entry.privateFlag() ? "&c비공개" : "&f공개");
        if (entry.world() != null) {
            lore.add("");
            lore.add("&a&l[좌표]");
            lore.add("&f" + entry.world() + " &7(" + entry.formattedLocation() + ")");
        }
        lore.add("");
        lore.add("&7좌클릭: 해당 홈으로 이동");
        lore.add("&7우클릭: 공개 상태 전환");
        lore.add("&7쉬프트+좌클릭: 이름 변경");
        lore.add("&7쉬프트+우클릭: 홈 삭제");
        return icon(Material.BOOK, "&a&l[홈] &f" + entry.name(), lore.toArray(new String[0]));
    }

    private List<WarpEntry> parseWarps(JsonArray array) {
        List<WarpEntry> result = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject obj = element.getAsJsonObject();
            String name = obj.has("name") ? obj.get("name").getAsString() : "이름 없음";
            boolean privateFlag = obj.has("private") && obj.get("private").getAsBoolean();
            String world = null;
            double x = 0.0D;
            double y = 0.0D;
            double z = 0.0D;
            if (obj.has("location") && obj.get("location").isJsonObject()) {
                JsonObject loc = obj.getAsJsonObject("location");
                world = loc.has("world") && !loc.get("world").isJsonNull() ? loc.get("world").getAsString() : null;
                x = loc.has("x") ? loc.get("x").getAsDouble() : 0.0D;
                y = loc.has("y") ? loc.get("y").getAsDouble() : 0.0D;
                z = loc.has("z") ? loc.get("z").getAsDouble() : 0.0D;
            }
            result.add(new WarpEntry(name, privateFlag, world, x, y, z));
        }
        return result;
    }

    private record WarpEntry(String name, boolean privateFlag, String world, double x, double y, double z) {

        String formattedLocation() {
            return Math.round(x) + ", " + Math.round(y) + ", " + Math.round(z);
        }
    }
}
