package wiki.creeper.superiorskyblockIntegeration.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Lightweight projection of a player entry returned by the gateway.
 */
public record PlayerSummary(UUID uuid,
                            String name,
                            String role,
                            boolean online,
                            String server,
                            String skinTexture) {

    public static PlayerSummary from(JsonObject json) {
        if (json == null) {
            return new PlayerSummary(null, "알 수 없음", "UNKNOWN", false, null, null);
        }
        UUID uuid = json.has("uuid") && !json.get("uuid").isJsonNull() ? parseUuid(json.get("uuid").getAsString()) : null;
        String name = json.has("name") && !json.get("name").isJsonNull() ? json.get("name").getAsString() : "알 수 없음";
        String role = json.has("role") && !json.get("role").isJsonNull() ? json.get("role").getAsString() : "UNKNOWN";
        boolean online = json.has("online") && !json.get("online").isJsonNull() && json.get("online").getAsBoolean();
        String server = json.has("server") && !json.get("server").isJsonNull() ? json.get("server").getAsString() : null;
        String skin = json.has("skinTexture") && !json.get("skinTexture").isJsonNull() ? json.get("skinTexture").getAsString() : null;
        return new PlayerSummary(uuid, name, role, online, server, skin);
    }

    public static List<PlayerSummary> from(JsonArray array) {
        if (array == null) {
            return Collections.emptyList();
        }
        List<PlayerSummary> list = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            list.add(from(element.getAsJsonObject()));
        }
        return list;
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public String identifier() {
        return uuid != null ? uuid.toString() : name;
    }
}
