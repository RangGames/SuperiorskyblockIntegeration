package wiki.creeper.superiorskyblockIntegeration.common.quest;

import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable snapshot of a single quest entry for an island.
 */
public final class IslandQuestEntry {

    private final int questId;
    private final int target;
    private final int progress;
    private final long completedAt;
    private final String completedBy;
    private final Map<String, Integer> contributions;

    private IslandQuestEntry(int questId,
                             int target,
                             int progress,
                             long completedAt,
                             String completedBy,
                             Map<String, Integer> contributions) {
        this.questId = questId;
        this.target = target;
        this.progress = progress;
        this.completedAt = completedAt;
        this.completedBy = completedBy;
        this.contributions = contributions.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(contributions);
    }

    public static IslandQuestEntry of(int questId, int target) {
        return new IslandQuestEntry(questId, target, 0, 0L, null, Collections.emptyMap());
    }

    public int questId() {
        return questId;
    }

    public int target() {
        return target;
    }

    public int progress() {
        return progress;
    }

    public long completedAt() {
        return completedAt;
    }

    public String completedBy() {
        return completedBy;
    }

    public Map<String, Integer> contributions() {
        return contributions;
    }

    public boolean completed() {
        return completedAt > 0L || progress >= target;
    }

    public IslandQuestEntry increment(int amount, String contributorUuid) {
        int newProgress = Math.max(0, Math.min(target, progress + Math.max(0, amount)));
        Map<String, Integer> updated = new HashMap<>(contributions);
        if (contributorUuid != null && !contributorUuid.isBlank()) {
            updated.merge(contributorUuid, amount, Integer::sum);
        }
        boolean completedNow = newProgress >= target && completedAt <= 0L;
        long completedTimestamp = completedNow ? Instant.now().toEpochMilli() : completedAt;
        String finisher = completedNow && contributorUuid != null && !contributorUuid.isBlank() ? contributorUuid : completedBy;
        return new IslandQuestEntry(questId, target, newProgress, completedTimestamp, finisher, updated);
    }

    public IslandQuestEntry withProgress(int newProgress) {
        int bounded = Math.max(0, Math.min(target, newProgress));
        long completedTimestamp = bounded >= target && completedAt <= 0L ? Instant.now().toEpochMilli() : completedAt;
        return new IslandQuestEntry(questId, target, bounded, completedTimestamp, completedBy, new HashMap<>(contributions));
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("questId", questId);
        json.addProperty("target", target);
        json.addProperty("progress", progress);
        if (completedAt > 0L) {
            json.addProperty("completedAt", completedAt);
        }
        if (completedBy != null) {
            json.addProperty("completedBy", completedBy);
        }
        if (!contributions.isEmpty()) {
            JsonObject contributionJson = new JsonObject();
            contributions.forEach(contributionJson::addProperty);
            json.add("contributions", contributionJson);
        }
        return json;
    }

    public static IslandQuestEntry fromJson(JsonObject json) {
        Objects.requireNonNull(json, "json");
        int questId = json.get("questId").getAsInt();
        int target = json.get("target").getAsInt();
        int progress = json.has("progress") ? json.get("progress").getAsInt() : 0;
        long completedAt = json.has("completedAt") ? json.get("completedAt").getAsLong() : 0L;
        String completedBy = json.has("completedBy") ? json.get("completedBy").getAsString() : null;
        Map<String, Integer> contributions = new HashMap<>();
        if (json.has("contributions") && json.get("contributions").isJsonObject()) {
            json.getAsJsonObject("contributions").entrySet()
                    .forEach(entry -> contributions.put(entry.getKey(), entry.getValue().getAsInt()));
        }
        return new IslandQuestEntry(questId, target, progress, completedAt, completedBy, contributions);
    }
}

