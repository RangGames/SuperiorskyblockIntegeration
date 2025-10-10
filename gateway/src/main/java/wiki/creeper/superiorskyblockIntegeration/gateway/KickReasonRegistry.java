package wiki.creeper.superiorskyblockIntegeration.gateway;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks recent kick requests so IslandKickEvent handlers can access the context (reason, target).
 */
final class KickReasonRegistry {

    private static final long ENTRY_TTL_MILLIS = 5 * 60 * 1000L;
    private final Map<KickKey, KickContext> registry = new ConcurrentHashMap<>();

    void register(UUID actorUuid, UUID targetUuid, String reason) {
        if (targetUuid == null) {
            return;
        }
        prune();
        registry.put(new KickKey(actorUuid, targetUuid),
                new KickContext(reason, System.currentTimeMillis()));
    }

    Optional<KickContext> consume(UUID actorUuid, UUID targetUuid) {
        if (targetUuid == null) {
            return Optional.empty();
        }
        KickKey key = new KickKey(actorUuid, targetUuid);
        KickContext context = registry.remove(key);
        if (context != null) {
            return Optional.of(context);
        }
        prune();
        return Optional.empty();
    }

    void invalidate(UUID actorUuid, UUID targetUuid) {
        if (targetUuid == null) {
            return;
        }
        registry.remove(new KickKey(actorUuid, targetUuid));
    }

    private void prune() {
        long cutoff = System.currentTimeMillis() - ENTRY_TTL_MILLIS;
        registry.entrySet().removeIf(entry -> entry.getValue().timestamp() < cutoff);
    }

    record KickContext(String reason, long timestamp) {
    }

    private record KickKey(UUID actor, UUID target) {
    }
}

