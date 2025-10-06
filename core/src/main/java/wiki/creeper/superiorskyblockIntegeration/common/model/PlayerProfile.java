package wiki.creeper.superiorskyblockIntegeration.common.model;

import com.google.gson.JsonObject;

import java.util.Objects;

/**
 * Minimal representation of a player's public profile stored in Redis.
 */
public final class PlayerProfile {

    private final String uuid;
    private final String name;
    private final long lastSeen;

    public PlayerProfile(String uuid, String name, long lastSeen) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.name = Objects.requireNonNull(name, "name");
        this.lastSeen = lastSeen;
    }

    public String uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public long lastSeen() {
        return lastSeen;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("uuid", uuid);
        json.addProperty("name", name);
        json.addProperty("lastSeen", lastSeen);
        return json;
    }

    public static PlayerProfile fromJson(JsonObject json) {
        String uuid = json.get("uuid").getAsString();
        String name = json.get("name").getAsString();
        long lastSeen = json.has("lastSeen") ? json.get("lastSeen").getAsLong() : 0L;
        return new PlayerProfile(uuid, name, lastSeen);
    }
}
