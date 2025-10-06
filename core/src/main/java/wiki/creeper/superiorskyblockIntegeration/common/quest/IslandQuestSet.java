package wiki.creeper.superiorskyblockIntegeration.common.quest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents the quest block (daily or weekly) assigned to an island.
 */
public final class IslandQuestSet {

    private final QuestType type;
    private final long assignedAt;
    private final int questCount;
    private final List<IslandQuestEntry> quests;
    private final boolean rewardGranted;
    private final long rewardGrantedAt;

    private IslandQuestSet(QuestType type,
                           long assignedAt,
                           int questCount,
                           List<IslandQuestEntry> quests,
                           boolean rewardGranted,
                           long rewardGrantedAt) {
        this.type = Objects.requireNonNull(type, "type");
        this.assignedAt = assignedAt;
        this.questCount = questCount;
        this.quests = Collections.unmodifiableList(new ArrayList<>(quests));
        this.rewardGranted = rewardGranted;
        this.rewardGrantedAt = rewardGrantedAt;
    }

    public static IslandQuestSet unassigned(QuestType type) {
        return new IslandQuestSet(type, 0L, 0, Collections.emptyList(), false, 0L);
    }

    public static IslandQuestSet assign(QuestType type,
                                        int questCount,
                                        List<IslandQuestEntry> quests) {
        return new IslandQuestSet(type, Instant.now().toEpochMilli(), questCount, quests, false, 0L);
    }

    public QuestType type() {
        return type;
    }

    public long assignedAt() {
        return assignedAt;
    }

    public int questCount() {
        return questCount;
    }

    public List<IslandQuestEntry> quests() {
        return quests;
    }

    public boolean rewardGranted() {
        return rewardGranted;
    }

    public long rewardGrantedAt() {
        return rewardGrantedAt;
    }

    public boolean assigned() {
        return assignedAt > 0L && !quests.isEmpty();
    }

    public boolean completed() {
        return quests.stream().allMatch(IslandQuestEntry::completed) && !quests.isEmpty();
    }

    public Optional<IslandQuestEntry> quest(int questId) {
        return quests.stream().filter(entry -> entry.questId() == questId).findFirst();
    }

    public IslandQuestSet replace(IslandQuestEntry updatedEntry) {
        List<IslandQuestEntry> updated = new ArrayList<>(quests.size());
        boolean replaced = false;
        for (IslandQuestEntry quest : quests) {
            if (quest.questId() == updatedEntry.questId()) {
                updated.add(updatedEntry);
                replaced = true;
            } else {
                updated.add(quest);
            }
        }
        if (!replaced) {
            updated.add(updatedEntry);
        }
        return new IslandQuestSet(type, assignedAt, questCount, updated, rewardGranted, rewardGrantedAt);
    }

    public IslandQuestSet withRewardGranted() {
        if (rewardGranted) {
            return this;
        }
        return new IslandQuestSet(type, assignedAt, questCount, quests, true, Instant.now().toEpochMilli());
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", type.name());
        if (assignedAt > 0L) {
            json.addProperty("assignedAt", assignedAt);
        }
        json.addProperty("questCount", questCount);
        json.addProperty("rewardGranted", rewardGranted);
        if (rewardGrantedAt > 0L) {
            json.addProperty("rewardGrantedAt", rewardGrantedAt);
        }
        JsonArray questsArray = new JsonArray();
        for (IslandQuestEntry quest : quests) {
            questsArray.add(quest.toJson());
        }
        json.add("quests", questsArray);
        return json;
    }

    public static IslandQuestSet fromJson(JsonObject json) {
        Objects.requireNonNull(json, "json");
        QuestType type = QuestType.valueOf(json.get("type").getAsString());
        long assignedAt = json.has("assignedAt") ? json.get("assignedAt").getAsLong() : 0L;
        int questCount = json.has("questCount") ? json.get("questCount").getAsInt() : 0;
        boolean rewardGranted = json.has("rewardGranted") && json.get("rewardGranted").getAsBoolean();
        long rewardGrantedAt = json.has("rewardGrantedAt") ? json.get("rewardGrantedAt").getAsLong() : 0L;
        List<IslandQuestEntry> quests = new ArrayList<>();
        if (json.has("quests") && json.get("quests").isJsonArray()) {
            JsonArray array = json.getAsJsonArray("quests");
            for (JsonElement element : array) {
                quests.add(IslandQuestEntry.fromJson(element.getAsJsonObject()));
            }
        }
        return new IslandQuestSet(type, assignedAt, questCount, quests, rewardGranted, rewardGrantedAt);
    }
}

