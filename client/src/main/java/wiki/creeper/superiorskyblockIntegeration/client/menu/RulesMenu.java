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

final class RulesMenu extends AbstractMenu {

    private static final int[] RULE_SLOTS = MenuLayouts.primarySlots(27);

    private final String islandName;
    private final List<String> rules;
    private final boolean editable;
    private final int max;

    RulesMenu(IslandMenuManager manager, JsonObject payload) {
        super(manager);
        Objects.requireNonNull(payload, "payload");
        this.islandName = payload.has("islandName") && !payload.get("islandName").isJsonNull()
                ? payload.get("islandName").getAsString()
                : "내 팜";
        this.editable = payload.has("editable") && payload.get("editable").getAsBoolean();
        this.max = payload.has("max") ? Math.max(1, payload.get("max").getAsInt()) : 5;
        this.rules = parseRules(payload.has("rules") ? payload.getAsJsonArray("rules") : new JsonArray());
    }

    @Override
    protected String title(Player player) {
        return ChatColor.translateAlternateColorCodes('&', "&a&l팜 규칙");
    }

    @Override
    protected int size() {
        return 27;
    }

    @Override
    protected void build(Player player, Inventory inventory) {
        decorateDefault(inventory);

        setItem(0, icon(Material.PAPER,
                "&a규칙 현황",
                "&f" + rules.size() + " &7/ &f" + max,
                "",
                "&7좌클릭: 아무 동작 없음",
                "&7쉬프트+우클릭: 해당 규칙 삭제"));

        for (int i = 0; i < RULE_SLOTS.length; i++) {
            int slot = RULE_SLOTS[i];
            if (i < rules.size()) {
                setItem(slot, createRuleItem(i + 1, rules.get(i)));
            } else {
                setItem(slot, null);
            }
        }

        if (editable) {
            setItem(22, icon(Material.EMERALD,
                    "&a규칙 추가",
                    "&7새로운 규칙을 등록합니다.",
                    "&7클릭 후 채팅으로 내용을 입력하세요."));
        } else {
            setItem(22, icon(Material.BARRIER,
                    "&7규칙 편집 불가",
                    "&c팜장만 규칙을 수정할 수 있습니다."));
        }

        setItem(size() - 1, mainMenuButton());
    }

    @Override
    protected void onClick(Player player, InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot == size() - 1) {
            manager().openMainMenu(player);
            return;
        }

        if (slot == 22) {
            if (!editable) {
                player.sendMessage(ChatColor.RED + "해당 팜의 규칙은 팜장만 변경할 수 있습니다.");
                return;
            }
            if (rules.size() >= max) {
                player.sendMessage(ChatColor.RED + "규칙은 최대 " + max + "개까지 등록할 수 있습니다.");
                return;
            }
            manager().beginRuleAddPrompt(player);
            return;
        }

        for (int i = 0; i < RULE_SLOTS.length; i++) {
            if (slot != RULE_SLOTS[i]) {
                continue;
            }
            if (i >= rules.size()) {
                return;
            }
            if (!editable) {
                return;
            }
            if (event.getClick() == ClickType.SHIFT_RIGHT) {
                manager().removeIslandRule(player, i + 1);
            } else if (event.getClick() == ClickType.RIGHT) {
                player.sendMessage(ChatColor.GRAY + "규칙 삭제는 쉬프트+우클릭으로 진행하세요.");
            }
            return;
        }
    }

    private ItemStack createRuleItem(int index, String rule) {
        return icon(Material.WRITTEN_BOOK,
                "&a규칙 #" + index,
                "&f" + rule,
                "",
                "&7쉬프트+우클릭: 규칙 삭제");
    }

    private List<String> parseRules(JsonArray array) {
        List<String> list = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (element == null || element.isJsonNull()) {
                continue;
            }
            try {
                list.add(element.getAsString());
            } catch (Exception ex) {
                list.add(element.toString());
            }
        }
        return list;
    }
}
