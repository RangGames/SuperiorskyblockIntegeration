package wiki.creeper.superiorskyblockIntegeration.common.quest;

import com.google.gson.JsonObject;

import java.util.Objects;

/**
 * Aggregated quest data for an island. Stored under a single Redis key per island.
 */
public final class IslandQuestData {

    private final String islandUuid;
    private final IslandQuestSet daily;
    private final IslandQuestSet weekly;

    public IslandQuestData(String islandUuid,
                           IslandQuestSet daily,
                           IslandQuestSet weekly) {
        this.islandUuid = Objects.requireNonNull(islandUuid, "islandUuid");
        this.daily = daily != null ? daily : IslandQuestSet.unassigned(QuestType.DAILY);
        this.weekly = weekly != null ? weekly : IslandQuestSet.unassigned(QuestType.WEEKLY);
    }

    public String islandUuid() {
        return islandUuid;
    }

    public IslandQuestSet daily() {
        return daily;
    }

    public IslandQuestSet weekly() {
        return weekly;
    }

    public IslandQuestData withDaily(IslandQuestSet set) {
        return new IslandQuestData(islandUuid, set, weekly);
    }

    public IslandQuestData withWeekly(IslandQuestSet set) {
        return new IslandQuestData(islandUuid, daily, set);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("islandUuid", islandUuid);
        json.add("daily", daily.toJson());
        json.add("weekly", weekly.toJson());
        return json;
    }

    public static IslandQuestData fromJson(JsonObject json) {
        Objects.requireNonNull(json, "json");
        String islandUuid = json.get("islandUuid").getAsString();
        IslandQuestSet daily = json.has("daily")
                ? IslandQuestSet.fromJson(json.getAsJsonObject("daily"))
                : IslandQuestSet.unassigned(QuestType.DAILY);
        IslandQuestSet weekly = json.has("weekly")
                ? IslandQuestSet.fromJson(json.getAsJsonObject("weekly"))
                : IslandQuestSet.unassigned(QuestType.WEEKLY);
        return new IslandQuestData(islandUuid, daily, weekly);
    }
}
