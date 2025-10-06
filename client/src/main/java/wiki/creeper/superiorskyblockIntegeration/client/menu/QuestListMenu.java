package wiki.creeper.superiorskyblockIntegeration.client.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import wiki.creeper.superiorskyblockIntegeration.common.quest.QuestDefinition;
import wiki.creeper.superiorskyblockIntegeration.common.quest.QuestType;

/**
 * Displays progress for quests of a specific rotation.
 */
public final class QuestListMenu extends AbstractMenu {

    private final QuestType type;
    private final JsonObject block;

    public QuestListMenu(IslandMenuManager manager, QuestType type, JsonObject block) {
        super(manager);
        this.type = type;
        this.block = block != null ? block : new JsonObject();
    }

    @Override
    protected String title(Player player) {
        return type.isDaily() ? "§e일간 퀘스트 진행" : "§b주간 퀘스트 진행";
    }

    @Override
    protected int size() {
        return 54;
    }

    @Override
    protected void build(Player player, Inventory inventory) {
        ItemStack filler = icon(Material.GRAY_STAINED_GLASS_PANE, "&7");
        fill(filler);

        JsonArray quests = block.has("quests") && block.get("quests").isJsonArray()
                ? block.getAsJsonArray("quests")
                : new JsonArray();

        int slot = 10;
        for (JsonElement element : quests) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject quest = element.getAsJsonObject();
            inventory.setItem(slot, questIcon(quest));
            slot++;
            if (slot % 9 == 0) {
                slot += 2; // skip borders
            }
        }

        setItem(45, icon(Material.ARROW, "&a뒤로 가기", "&7퀘스트 허브로 돌아갑니다."));
        setItem(53, icon(Material.CLOCK, "&e새로 고침", "&7최신 진행도를 불러옵니다."));
    }

    @Override
    protected void onClick(Player player, InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot == 45) {
            manager().openQuestHub(player);
        } else if (slot == 53) {
            manager().openQuestList(player, type, null); // trigger refresh
        }
    }

    private ItemStack questIcon(JsonObject quest) {
        int questId = quest.has("questId") ? quest.get("questId").getAsInt() : 0;
        QuestDefinition definition = QuestDefinition.byId(questId).orElse(null);
        String name = definition != null ? definition.displayName() : "퀘스트 " + questId;
        int target = quest.has("target") ? quest.get("target").getAsInt() : 0;
        int progress = quest.has("progress") ? quest.get("progress").getAsInt() : 0;
        boolean completed = quest.has("completed") && quest.get("completed").getAsBoolean();
        int percentage = quest.has("percentage") ? quest.get("percentage").getAsInt() : (target > 0 ? (int) Math.min(100, Math.round(progress * 100.0 / target)) : 0);
        Material material = completed ? Material.ENDER_CHEST : Material.CHEST;

        List<String> lore = new ArrayList<>();
        lore.add("&f진행도: &e" + formatNumber(progress) + "&f / &e" + formatNumber(target));
        lore.add("&f진행률: &e" + percentage + "%");
        if (completed) {
            String finisher = quest.has("completedBy") ? contributorName(quest.get("completedBy").getAsString()) : "알 수 없음";
            lore.add("&a완료자: &f" + finisher);
        }
        lore.add(" ");
        List<Map.Entry<String, Integer>> top = contributions(quest);
        if (top.isEmpty()) {
            lore.add("&f기여도 정보가 없습니다.");
        } else {
            int total = totalContribution(quest);
            lore.add("&f기여도 상위 5인:");
            top.forEach(entry -> {
                String contributor = contributorName(entry.getKey());
                int amount = entry.getValue();
                double percent = total > 0 ? (amount * 100.0D) / total : 0.0D;
                lore.add("&7- &f" + contributor + "&7: &e" + formatNumber(amount) + " 회 &7(" + formatPercent(percent) + "%)");
            });
        }

        return icon(material, "&6" + name, lore.toArray(String[]::new));
    }

    private List<Map.Entry<String, Integer>> contributions(JsonObject quest) {
        if (!quest.has("contributions") || !quest.get("contributions").isJsonObject()) {
            return List.of();
        }
        return quest.getAsJsonObject("contributions").entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(), entry.getValue().getAsInt()))
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(5)
                .collect(Collectors.toList());
    }

    private int totalContribution(JsonObject quest) {
        if (!quest.has("contributions") || !quest.get("contributions").isJsonObject()) {
            return 0;
        }
        return quest.getAsJsonObject("contributions").entrySet().stream()
                .mapToInt(entry -> entry.getValue().getAsInt())
                .sum();
    }

    private String formatNumber(int value) {
        return String.format("%,d", value);
    }

    private String formatPercent(double percent) {
        if (percent <= 0.0D) {
            return "0.0";
        }
        return String.format(java.util.Locale.KOREA, "%.1f", percent);
    }

    private String contributorName(String rawUuid) {
        try {
            UUID uuid = UUID.fromString(rawUuid);
            OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
            String name = player != null ? player.getName() : null;
            return name != null ? name : rawUuid.substring(0, 8);
        } catch (IllegalArgumentException ex) {
            return rawUuid;
        }
    }
}
