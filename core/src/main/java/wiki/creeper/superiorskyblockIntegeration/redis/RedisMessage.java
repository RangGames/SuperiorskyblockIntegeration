package wiki.creeper.superiorskyblockIntegeration.redis;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.UUID;

/**
 * Canonical representation of SSB messaging payloads.
 */
public final class RedisMessage {

    public static final int CURRENT_VERSION = 1;

    private final JsonObject root;

    private RedisMessage(JsonObject root) {
        this.root = root;
    }

    public static RedisMessage parse(String json) {
        return new RedisMessage(JsonParser.parseString(json).getAsJsonObject());
    }

    public static RedisMessage request(String op) {
        JsonObject root = new JsonObject();
        root.addProperty("ver", String.valueOf(CURRENT_VERSION));
        root.addProperty("id", UUID.randomUUID().toString());
        root.addProperty("ts", System.currentTimeMillis());
        root.addProperty("op", op);
        return new RedisMessage(root);
    }

    public static RedisMessage responseFor(RedisMessage request) {
        JsonObject root = new JsonObject();
        String version = null;
        if (request.root().has("ver")) {
            version = request.root().get("ver").getAsString();
        }
        root.addProperty("ver", version != null ? version : String.valueOf(CURRENT_VERSION));
        root.addProperty("id", request.id());
        root.addProperty("ts", System.currentTimeMillis());
        if (request.op() != null) {
            root.addProperty("op", request.op());
        }
        if (request.actor() != null) {
            root.addProperty("actor", request.actor());
        }
        return new RedisMessage(root);
    }

    public JsonObject root() {
        return root;
    }

    public String id() {
        JsonElement element = root.get("id");
        return element != null ? element.getAsString() : null;
    }

    public String op() {
        JsonElement element = root.get("op");
        return element != null ? element.getAsString() : null;
    }

    public JsonObject data() {
        JsonElement element = root.get("data");
        if (element == null || element.isJsonNull()) {
            JsonObject data = new JsonObject();
            root.add("data", data);
            return data;
        }
        return element.getAsJsonObject();
    }

    public JsonObject error() {
        JsonElement element = root.get("error");
        if (element == null || element.isJsonNull()) {
            JsonObject err = new JsonObject();
            err.addProperty("code", "");
            err.addProperty("message", "");
            err.addProperty("retryable", false);
            root.add("error", err);
            return err;
        }
        return element.getAsJsonObject();
    }

    public boolean ok() {
        JsonElement element = root.get("ok");
        return element != null && element.getAsBoolean();
    }

    public void setOk(boolean ok) {
        root.addProperty("ok", ok);
    }

    public void setActor(String actorUuid) {
        root.addProperty("actor", actorUuid);
    }

    public String actor() {
        JsonElement element = root.get("actor");
        return element != null ? element.getAsString() : null;
    }

    public void setTarget(String targetUuid) {
        JsonObject data = data();
        data.addProperty("target", targetUuid);
    }

    public void mergeData(JsonObject payload) {
        if (payload == null) {
            return;
        }
        payload.entrySet().forEach(entry -> data().add(entry.getKey(), entry.getValue()));
    }

    public void setData(JsonObject payload) {
        root.add("data", payload == null ? new JsonObject() : payload);
    }

    public void setError(String code, String message, boolean retryable) {
        JsonObject err = new JsonObject();
        err.addProperty("code", code);
        err.addProperty("message", message);
        err.addProperty("retryable", retryable);
        root.add("error", err);
    }

    public void attachSignature(String signature) {
        root.addProperty("sig", signature);
    }

    public String signature() {
        JsonElement element = root.get("sig");
        return element != null ? element.getAsString() : null;
    }

    public String toJson() {
        return RedisCodec.gson().toJson(root.deepCopy());
    }

    public String canonicalPayload() {
        JsonObject copy = root.deepCopy();
        copy.remove("sig");
        return RedisCodec.gson().toJson(copy);
    }

    public void ensureVersion() {
        root.addProperty("ver", String.valueOf(CURRENT_VERSION));
    }
}
