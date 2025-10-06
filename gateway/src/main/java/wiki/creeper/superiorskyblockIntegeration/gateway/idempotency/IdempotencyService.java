package wiki.creeper.superiorskyblockIntegeration.gateway.idempotency;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import wiki.creeper.superiorskyblockIntegeration.gateway.GatewayResponse;

/**
 * In-memory idempotency cache to deduplicate gateway write operations.
 */
public final class IdempotencyService {

    private final Duration ttl;
    private final Logger logger;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    public IdempotencyService(Duration ttl, Logger logger) {
        this.ttl = ttl != null ? ttl : Duration.ofMinutes(5);
        this.logger = logger;
    }

    public Optional<GatewayResponse> fetch(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        Entry entry = cache.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (isExpired(entry)) {
            cache.remove(key, entry);
            return Optional.empty();
        }
        return Optional.of(entry.response);
    }

    public void store(String key, GatewayResponse response) {
        if (key == null || key.isBlank() || response == null) {
            return;
        }
        long expiresAt = computeExpiry();
        cache.put(key, new Entry(response, expiresAt));
        sweepIfNeeded();
    }

    private long computeExpiry() {
        long ttlMillis = Math.max(0L, ttl.toMillis());
        long now = System.currentTimeMillis();
        long expiresAt = now + ttlMillis;
        if (expiresAt <= 0L) {
            return Long.MAX_VALUE;
        }
        return expiresAt;
    }

    private boolean isExpired(Entry entry) {
        return entry.expiresAt != Long.MAX_VALUE && entry.expiresAt <= System.currentTimeMillis();
    }

    private void sweepIfNeeded() {
        if (cache.size() < 512) {
            return;
        }
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(e -> e.getValue().expiresAt != Long.MAX_VALUE && e.getValue().expiresAt <= now);
        if (logger != null && cache.size() > 4096) {
            logger.warning("Idempotency cache contains more than 4096 entries; consider reducing TTL");
        }
    }

    private record Entry(GatewayResponse response, long expiresAt) {
    }
}
