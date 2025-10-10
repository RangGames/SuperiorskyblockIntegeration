package wiki.creeper.superiorskyblockIntegeration.client.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import wiki.creeper.superiorskyblockIntegeration.api.NetworkOperationResult;
import wiki.creeper.superiorskyblockIntegeration.api.NetworkSkyblockService;
import wiki.creeper.superiorskyblockIntegeration.common.quest.QuestDefinition;
import wiki.creeper.superiorskyblockIntegeration.common.quest.QuestRewards;
import wiki.creeper.superiorskyblockIntegeration.common.quest.QuestType;

/**
 * Dispatches quest progress updates to the gateway and provides player feedback.
 */
public final class QuestProgressService {

    private final JavaPlugin plugin;
    private final NetworkSkyblockService network;
    private final Map<String, Set<String>> questCompletions = new ConcurrentHashMap<>();
    private final Set<String> rewardAnnouncements = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public QuestProgressService(JavaPlugin plugin, NetworkSkyblockService network) {
        this.plugin = plugin;
        this.network = network;
    }

    public void increment(Player player, QuestType type, int questId, int amount) {
        if (player == null || type == null || amount <= 0) {
            return;
        }
        UUID contributor = player.getUniqueId();
        network.questProgress(player, type, questId, amount, contributor.toString())
                .thenAccept(result -> handleResult(player, type, questId, amount, result))
                .exceptionally(ex -> {
                    plugin.getLogger().warning("Failed to submit quest progress for " + player.getName() + ": " + ex.getMessage());
                    return null;
                });
    }

    private void handleResult(Player player,
                              QuestType type,
                              int questId,
                              int amount,
                              NetworkOperationResult result) {
        if (result == null) {
            return;
        }
        if (result.failed()) {
            String code = result.errorCode();
            if (!"QUEST_NOT_ASSIGNED".equalsIgnoreCase(code) && !"QUEST_NOT_FOUND".equalsIgnoreCase(code)) {
                plugin.getLogger().fine("Quest progress failed for " + player.getName() + ": " + code + " - " + result.errorMessage());
            }
            return;
        }
        JsonObject data = result.data();
        plugin.getServer().getScheduler().runTask(plugin, () -> applyFeedback(player, type, questId, amount, data));
    }

    private void applyFeedback(Player player, QuestType type, int questId, int amount, JsonObject state) {
        if (player == null || !player.isOnline()) {
            return;
        }
        JsonObject block = extractBlock(state, type);
        if (!block.has("assigned") || !block.get("assigned").getAsBoolean()) {
            return;
        }

        JsonObject questJson = findQuest(block, questId);
        if (questJson == null) {
            return;
        }

        String islandId = state.has("islandId") ? state.get("islandId").getAsString() : null;
        int progress = questJson.has("progress") ? questJson.get("progress").getAsInt() : 0;
        int target = questJson.has("target") ? questJson.get("target").getAsInt() : 0;
        int percentage = questJson.has("percentage") ? questJson.get("percentage").getAsInt() : (target > 0 ? Math.min(100, Math.round(progress * 100.0f / target)) : 0);
        QuestDefinition definition = QuestDefinition.byId(questId).orElse(null);
        String questName = definition != null ? definition.displayName() : "퀘스트 " + questId;

        sendActionBar(player, type, questName, progress, target, percentage);
        maybeAnnounceQuestCompletion(player, islandId, type, questId, questName, questJson);
        maybeAnnounceReward(player, islandId, type, block);
    }

    private JsonObject extractBlock(JsonObject state, QuestType type) {
        if (state == null) {
            return new JsonObject();
        }
        String key = type.isDaily() ? "daily" : "weekly";
        return state.has(key) && state.get(key).isJsonObject() ? state.getAsJsonObject(key) : new JsonObject();
    }

    private JsonObject findQuest(JsonObject block, int questId) {
        if (!block.has("quests") || !block.get("quests").isJsonArray()) {
            return null;
        }
        JsonArray array = block.getAsJsonArray("quests");
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject quest = element.getAsJsonObject();
            if (quest.has("questId") && quest.get("questId").getAsInt() == questId) {
                return quest;
            }
        }
        return null;
    }

    private void sendActionBar(Player player,
                               QuestType type,
                               String questName,
                               int progress,
                               int target,
                               int percentage) {
        ChatColor primary = type.isDaily() ? ChatColor.LIGHT_PURPLE : ChatColor.DARK_AQUA;
        String label = type.isDaily() ? "[일간] " : "[주간] ";
        String message = primary + "" + ChatColor.BOLD + label + ChatColor.WHITE + questName + ChatColor.GRAY + " ("
                + formatNumber(progress) + "/" + formatNumber(target) + ") " + ChatColor.YELLOW + percentage + "%";
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
    }

    private void maybeAnnounceQuestCompletion(Player player,
                                              String islandId,
                                              QuestType type,
                                              int questId,
                                              String questName,
                                              JsonObject questJson) {
        if (!questJson.has("completed") || !questJson.get("completed").getAsBoolean()) {
            return;
        }
        String completedBy = questJson.has("completedBy") ? questJson.get("completedBy").getAsString() : null;
        if (completedBy == null || !completedBy.equalsIgnoreCase(player.getUniqueId().toString())) {
            return;
        }
        if (islandId == null) {
            return;
        }
        String key = completionKey(type, questId);
        Set<String> completed = questCompletions.computeIfAbsent(islandId, unused -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
        if (completed.contains(key)) {
            return;
        }
        completed.add(key);
        player.sendMessage(ChatColor.GREEN + "[Skyblock] " + questName + ChatColor.GRAY + " 퀘스트를 완료했습니다!");
    }

    private void maybeAnnounceReward(Player player, String islandId, QuestType type, JsonObject block) {
        if (!block.has("rewardGranted") || !block.get("rewardGranted").getAsBoolean()) {
            return;
        }
        if (islandId == null) {
            return;
        }
        String key = rewardKey(islandId, type);
        if (!rewardAnnouncements.add(key)) {
            return;
        }
        int questCount = block.has("questCount") ? block.get("questCount").getAsInt() : 0;
        int farmPoint = block.has("farmPointReward") ? block.get("farmPointReward").getAsInt()
                : QuestRewards.farmPointReward(type, questCount);
        int farmScore = block.has("farmScoreReward") ? block.get("farmScoreReward").getAsInt()
                : QuestRewards.farmScoreReward(type);
        player.sendMessage(ChatColor.GOLD + "[Skyblock] " + (type.isDaily() ? "일간" : "주간") + " 퀘스트를 전부 완료했습니다!");
        player.sendMessage(ChatColor.WHITE + " - 팜 포인트: " + ChatColor.YELLOW + formatNumber(farmPoint));
        player.sendMessage(ChatColor.WHITE + " - 팜 점수: " + ChatColor.YELLOW + formatNumber(farmScore));
    }

    private String completionKey(QuestType type, int questId) {
        return type.name() + ':' + questId;
    }

    private String rewardKey(String islandId, QuestType type) {
        return islandId + ':' + type.name();
    }

    private String formatNumber(int value) {
        return String.format("%,d", value);
    }

    public void resetForIsland(String islandId, QuestType type) {
        if (islandId == null || type == null) {
            return;
        }
        questCompletions.computeIfPresent(islandId, (id, completions) -> {
            completions.removeIf(key -> key.startsWith(type.name() + ':'));
            return completions;
        });
        rewardAnnouncements.remove(rewardKey(islandId, type));
    }
}
