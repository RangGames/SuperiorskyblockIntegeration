package wiki.creeper.superiorskyblockIntegeration.gateway;

import com.google.gson.JsonObject;

import wiki.creeper.superiorskyblockIntegeration.redis.RedisCodec;

public record GatewayResponse(boolean ok, JsonObject data, GatewayError error) {

    public static GatewayResponse ok(JsonObject data) {
        return new GatewayResponse(true, data != null ? data : new JsonObject(), null);
    }

    public static GatewayResponse error(String code, String message, boolean retryable) {
        return new GatewayResponse(false, new JsonObject(), new GatewayError(code, message, retryable));
    }

    @Override
    public JsonObject data() {
        return data;
    }

    @Override
    public GatewayError error() {
        return error;
    }

    public String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("ok", ok);
        root.add("data", data != null ? data : new JsonObject());
        if (error != null) {
            root.add("error", error.toJson());
        }
        return RedisCodec.gson().toJson(root);
    }

    public static GatewayResponse fromJson(String json) {
        JsonObject root = RedisCodec.gson().fromJson(json, JsonObject.class);
        boolean ok = root.has("ok") && root.get("ok").getAsBoolean();
        JsonObject data = root.has("data") && root.get("data").isJsonObject()
                ? root.getAsJsonObject("data")
                : new JsonObject();
        GatewayError error = null;
        if (root.has("error") && root.get("error").isJsonObject()) {
            error = GatewayError.fromJson(root.getAsJsonObject("error"));
        }
        return new GatewayResponse(ok, data, error);
    }
}

record GatewayError(String code, String message, boolean retryable) {

    JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("code", code);
        root.addProperty("message", message);
        root.addProperty("retryable", retryable);
        return root;
    }

    static GatewayError fromJson(JsonObject root) {
        String code = root.has("code") ? root.get("code").getAsString() : "UNKNOWN";
        String message = root.has("message") ? root.get("message").getAsString() : "";
        boolean retryable = root.has("retryable") && root.get("retryable").getAsBoolean();
        return new GatewayError(code, message, retryable);
    }
}
