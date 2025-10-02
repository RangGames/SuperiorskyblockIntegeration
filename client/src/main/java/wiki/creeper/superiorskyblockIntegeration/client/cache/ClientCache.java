package wiki.creeper.superiorskyblockIntegeration.client.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.JsonObject;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import wiki.creeper.superiorskyblockIntegeration.config.PluginConfig;
import wiki.creeper.superiorskyblockIntegeration.redis.RedisMessage;

/**
 * Lightweight cache for read operations, invalidated by Redis events.
 */
public final class ClientCache {

    private final Cache<String, JsonObject> cache;

    public ClientCache(PluginConfig.CacheSettings settings) {
        if (!settings.enabled()) {
            this.cache = null;
            return;
        }
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(settings.ttlSeconds(), TimeUnit.SECONDS)
                .maximumSize(settings.maximumSize())
                .build();
    }

    public boolean enabled() {
        return cache != null;
    }

    public Optional<JsonObject> get(String key) {
        if (cache == null) {
            return Optional.empty();
        }
        JsonObject value = cache.getIfPresent(key);
        return Optional.ofNullable(value);
    }

    public void put(String key, JsonObject value) {
        if (cache != null && value != null) {
            cache.put(key, value);
        }
    }

    public void invalidate(String key) {
        if (cache != null) {
            cache.invalidate(key);
        }
    }

    public void invalidateByEvent(String eventType, RedisMessage message) {
        if (cache == null) {
            return;
        }
        if (eventType.startsWith("member") || eventType.startsWith("island")) {
            String islandId = message.data().has("islandId")
                    ? message.data().get("islandId").getAsString()
                    : null;
            if (islandId != null) {
                cache.invalidate(ClientCacheKeys.members(islandId));
                cache.invalidate(ClientCacheKeys.island(islandId));
            } else {
                cache.invalidateAll();
            }
        } else if (eventType.startsWith("invite")) {
            // invites affect cached lists indirectly; safest to invalidate all invite related keys
            cache.invalidateAll();
        }
    }
}
