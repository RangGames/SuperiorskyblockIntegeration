package wiki.creeper.superiorskyblockIntegeration.api;

import com.google.gson.JsonObject;

import wiki.creeper.superiorskyblockIntegeration.redis.RedisCodec;
import wiki.creeper.superiorskyblockIntegeration.redis.RedisMessage;

/**
 * Represents the result of a network operation executed against the gateway.
 */
public final class NetworkOperationResult {

    private final boolean success;
    private final JsonObject data;
    private final String errorCode;
    private final String errorMessage;
    private final boolean retryable;

    private NetworkOperationResult(boolean success,
                                   JsonObject data,
                                   String errorCode,
                                   String errorMessage,
                                   boolean retryable) {
        this.success = success;
        this.data = data != null ? data.deepCopy() : new JsonObject();
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.retryable = retryable;
    }

    public static NetworkOperationResult fromMessage(RedisMessage message) {
        JsonObject dataCopy = message.data().deepCopy();
        if (message.ok()) {
            return success(dataCopy);
        }
        JsonObject error = message.error();
        String code = error.has("code") ? error.get("code").getAsString() : "UNKNOWN";
        String messageText = error.has("message") ? error.get("message").getAsString() : "";
        boolean retryable = error.has("retryable") && error.get("retryable").getAsBoolean();
        return new NetworkOperationResult(false, dataCopy, code, messageText, retryable);
    }

    public static NetworkOperationResult success(JsonObject data) {
        return new NetworkOperationResult(true, data != null ? data.deepCopy() : new JsonObject(), null, null, false);
    }

    public static NetworkOperationResult failure(String code, String message, boolean retryable) {
        return new NetworkOperationResult(false, new JsonObject(), code, message, retryable);
    }

    public boolean success() {
        return success;
    }

    public boolean failed() {
        return !success;
    }

    public JsonObject data() {
        return data.deepCopy();
    }

    public String errorCode() {
        return errorCode;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public boolean retryable() {
        return retryable;
    }

    @Override
    public String toString() {
        JsonObject root = new JsonObject();
        root.addProperty("success", success);
        root.add("data", data.deepCopy());
        if (errorCode != null) {
            JsonObject err = new JsonObject();
            err.addProperty("code", errorCode);
            err.addProperty("message", errorMessage);
            err.addProperty("retryable", retryable);
            root.add("error", err);
        }
        return RedisCodec.gson().toJson(root);
    }
}
