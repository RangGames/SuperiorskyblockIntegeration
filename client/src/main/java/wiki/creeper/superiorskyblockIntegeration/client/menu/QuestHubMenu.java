package wiki.creeper.superiorskyblockIntegeration.client.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import wiki.creeper.superiorskyblockIntegeration.common.quest.QuestDefinition;
import wiki.creeper.superiorskyblockIntegeration.common.quest.QuestRewards;
import wiki.creeper.superiorskyblockIntegeration.common.quest.QuestType;

/**
 * Hub menu for island quests. Allows selecting daily/weekly quests, viewing progress, and refreshing state.
 */
public final class QuestHubMenu extends AbstractMenu {

    private final JsonObject state;
    private final boolean canManage;

    public QuestHubMenu(IslandMenuManager manager, JsonObject state) {
        super(manager);
        JsonObject snapshot = state != null ? state : new JsonObject();
        this.state = snapshot;
        this.canManage = snapshot.has("canManage") && snapshot.get("canManage").getAsBoolean();
    }

    @Override
    protected String title(Player player) {
        return "§6팜 퀘스트";
    }

    @Override
    protected int size() {
        return 45;
    }

    @Override
    protected void build(Player player, Inventory inventory) {
        decorateDefault(inventory);
        placeNavigation(backButton("메인 메뉴"), null, mainMenuButton());

        setItem(13, icon(Material.PAPER, "&f팜 퀘스트 허브", "&7일간/주간 퀘스트 상태를 확인합니다."));
        JsonObject daily = questBlock(QuestType.DAILY);
        JsonObject weekly = questBlock(QuestType.WEEKLY);

        setItem(19, describeBlock(daily, QuestType.DAILY));
        setItem(22, describeBlock(weekly, QuestType.WEEKLY));
        setItem(25, icon(Material.CHAIN, "&6&l[!] &f잠금", "&7추후 업데이트 예정입니다."));
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
        int mainSlot = size - 1;
        if (slot == backSlot) {
            manager().openMainMenu(player);
            return;
        }
        if (slot == mainSlot) {
            manager().openMainMenu(player);
            return;
        }
        if (slot == 19) {
            handleBlockClick(player, QuestType.DAILY);
        } else if (slot == 22) {
            handleBlockClick(player, QuestType.WEEKLY);
        }
    }

    private void handleBlockClick(Player player, QuestType type) {
        JsonObject block = questBlock(type);
        boolean assigned = block.has("assigned") && block.get("assigned").getAsBoolean();
        if (assigned) {
            manager().openQuestList(player, type, block);
        } else {
            if (!canManage) {
                player.sendMessage(ChatColor.RED + "[Skyblock] 팜장 또는 부팜장만 퀘스트를 발급할 수 있습니다.");
                return;
            }
            manager().openQuestSelect(player, type, canManage);
        }
    }

    private JsonObject questBlock(QuestType type) {
        String key = type.isDaily() ? "daily" : "weekly";
        return state.has(key) && state.get(key).isJsonObject()
                ? state.getAsJsonObject(key)
                : new JsonObject();
    }

    private ItemStack describeBlock(JsonObject data, QuestType type) {
        boolean assigned = data.has("assigned") && data.get("assigned").getAsBoolean();
        Material material = assigned ? Material.CHEST : Material.ENDER_CHEST;
        return icon(material, buildTitle(type, assigned), buildLore(data, type, assigned));
    }

    private String buildTitle(QuestType type, boolean assigned) {
        String label = type.isDaily() ? "일간" : "주간";
        return "&6&l[!] &f" + label + " 퀘스트" + (assigned ? " &7&o(진행 중)" : "");
    }

    private String[] buildLore(JsonObject data, QuestType type, boolean assigned) {
        List<String> lore = new ArrayList<>();
        lore.add(" ");
        if (!assigned) {
            lore.add("&a&l| &f현재 팜 " + (type.isDaily() ? "일간" : "주간") + " 퀘스트를 받지 않았습니다.");
            if (canManage) {
                lore.add("&a&l| &f클릭 시 " + (type.isDaily() ? "일간" : "주간") + " 퀘스트를 발급 받습니다.");
            } else {
                lore.add("&c&l| &f팜장 또는 부팜장만 퀘스트를 발급할 수 있습니다.");
            }
            return lore.toArray(String[]::new);
        }

        int questCount = data.has("questCount") ? data.get("questCount").getAsInt() : 0;
        int completed = countCompleted(data);
        int percent = questCount > 0 ? Math.round((completed / (float) questCount) * 100F) : 0;
        int farmPoint = data.has("farmPointReward") ? data.get("farmPointReward").getAsInt()
                : QuestRewards.farmPointReward(type, questCount);
        int farmScore = data.has("farmScoreReward") ? data.get("farmScoreReward").getAsInt()
                : QuestRewards.farmScoreReward(type);
        boolean rewardGranted = data.has("rewardGranted") && data.get("rewardGranted").getAsBoolean();

        lore.add("&a&l| &f진행도: " + formatNumber(completed) + " / " + formatNumber(questCount) + " &7&o(" + percent + "%%)");
        lore.add(" ");
        lore.add("&6&l| &f클리어 보상");
        lore.add("&f  - 팜 포인트: &e" + formatNumber(farmPoint));
        lore.add("&f  - 팜 점수: &e" + formatNumber(farmScore));
        lore.add(rewardGranted ? "&a&l| &f보상이 지급되었습니다." : "&c&l| &f보상이 아직 지급되지 않았습니다.");
        lore.add(" ");
        lore.add("&a&l| &f클릭 시 수락 받은 " + (type.isDaily() ? "일간" : "주간") + " 퀘스트 목록을 확인합니다.");
        appendTopContributors(lore, data);
        return lore.toArray(String[]::new);
    }

    private void appendTopContributors(List<String> lore, JsonObject data) {
        if (!data.has("quests") || !data.get("quests").isJsonArray()) {
            return;
        }
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (JsonElement element : data.getAsJsonArray("quests")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject quest = element.getAsJsonObject();
            if (!quest.has("contributions") || !quest.get("contributions").isJsonObject()) {
                continue;
            }
            JsonObject contrib = quest.getAsJsonObject("contributions");
            for (Map.Entry<String, JsonElement> entry : contrib.entrySet()) {
                totals.merge(entry.getKey(), entry.getValue().getAsInt(), Integer::sum);
            }
        }
        if (totals.isEmpty()) {
            return;
        }
        lore.add(" ");
        lore.add("&e&l| &f상위 기여도 (상위 3명)");
        totals.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(3)
                .forEach(entry -> lore.add("&7- &f" + contributorName(entry.getKey()) + "&7: &e" + formatNumber(entry.getValue()) + " 회"));
    }

    private int countCompleted(JsonObject data) {
        if (!data.has("quests") || !data.get("quests").isJsonArray()) {
            return 0;
        }
        JsonArray array = data.getAsJsonArray("quests");
        int completed = 0;
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject quest = element.getAsJsonObject();
            if (quest.has("completed") && quest.get("completed").getAsBoolean()) {
                completed++;
            }
        }
        return completed;
    }

    private String formatNumber(int value) {
        return String.format("%,d", value);
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
