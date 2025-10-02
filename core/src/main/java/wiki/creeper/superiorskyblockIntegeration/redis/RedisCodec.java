package wiki.creeper.superiorskyblockIntegeration.redis;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Shared Gson instance tuned for messaging payloads.
 */
public final class RedisCodec {

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();

    private RedisCodec() {
        // utility
    }

    public static Gson gson() {
        return GSON;
    }
}
