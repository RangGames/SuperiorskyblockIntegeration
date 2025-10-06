package wiki.creeper.superiorskyblockIntegeration.redis;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Canonical representation of SSB messaging payloads.
 */
public final class RedisMessage {

    public static final int CURRENT_VERSION = 1;

    private static final String KEY_COMPRESSED = "__compressed";
    private static final String KEY_ENCODING = "__encoding";
    private static final String KEY_PAYLOAD = "__payload";
    private static final String ENCODING_GZIP_BASE64 = "gzip+base64";

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

    public void compressDataIfNeeded(int threshold) {
        if (threshold <= 0) {
            return;
        }
        JsonElement element = root.get("data");
        if (element == null || !element.isJsonObject()) {
            return;
        }
        JsonObject data = element.getAsJsonObject();
        if (data.has(KEY_COMPRESSED)) {
            return;
        }
        String json = RedisCodec.gson().toJson(data);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < threshold) {
            return;
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(bytes);
            gzip.finish();
            String encoded = Base64.getEncoder().encodeToString(baos.toByteArray());
            JsonObject wrapper = new JsonObject();
            wrapper.addProperty(KEY_COMPRESSED, true);
            wrapper.addProperty(KEY_ENCODING, ENCODING_GZIP_BASE64);
            wrapper.addProperty(KEY_PAYLOAD, encoded);
            root.add("data", wrapper);
        } catch (IOException ignored) {
            root.add("data", data);
        }
    }

    public void decompressDataIfNeeded() {
        JsonElement element = root.get("data");
        if (element == null || !element.isJsonObject()) {
            return;
        }
        JsonObject data = element.getAsJsonObject();
        if (!isCompressionWrapper(data)) {
            return;
        }
        String payload = data.has(KEY_PAYLOAD) ? data.get(KEY_PAYLOAD).getAsString() : null;
        if (payload == null || payload.isEmpty()) {
            root.add("data", new JsonObject());
            return;
        }
        byte[] compressed;
        try {
            compressed = Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException ex) {
            root.add("data", new JsonObject());
            return;
        }
        try (ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
             GZIPInputStream gzip = new GZIPInputStream(bais)) {
            byte[] uncompressed = gzip.readAllBytes();
            JsonObject original = JsonParser.parseString(new String(uncompressed, StandardCharsets.UTF_8)).getAsJsonObject();
            root.add("data", original);
        } catch (IOException | RuntimeException ex) {
            root.add("data", new JsonObject());
        }
    }

    private boolean isCompressionWrapper(JsonObject data) {
        if (!data.has(KEY_COMPRESSED) || !data.get(KEY_COMPRESSED).getAsBoolean()) {
            return false;
        }
        String encoding = data.has(KEY_ENCODING) ? data.get(KEY_ENCODING).getAsString() : "";
        return ENCODING_GZIP_BASE64.equalsIgnoreCase(encoding);
    }
}
