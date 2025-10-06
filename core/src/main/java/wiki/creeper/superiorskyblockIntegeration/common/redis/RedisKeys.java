package wiki.creeper.superiorskyblockIntegeration.common.redis;

import java.util.Locale;

/**
 * Centralises Redis key formats to ensure consistency between client and gateway components.
 */
public final class RedisKeys {

    private RedisKeys() {
    }

    private static final String ROOT = "ssb2";

    public static String playerName(String name) {
        return ROOT + ":player:name:" + normalise(name);
    }

    public static String playerUuid(String uuid) {
        return ROOT + ":player:uuid:" + uuid.toLowerCase(Locale.ROOT);
    }

    public static String metadata(String uuid, String key) {
        return ROOT + ":metadata:" + uuid.toLowerCase(Locale.ROOT) + ':' + normalise(key);
    }

    public static String data(String namespace, String key) {
        return ROOT + ":data:" + normalise(namespace) + ':' + normalise(key);
    }

    private static String normalise(String input) {
        return input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
    }
}
