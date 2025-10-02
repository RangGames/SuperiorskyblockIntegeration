package wiki.creeper.superiorskyblockIntegeration.gateway.idempotency;

import redis.clients.jedis.Jedis;

import java.time.Duration;
import java.util.Optional;
import java.util.logging.Level;

import wiki.creeper.superiorskyblockIntegeration.gateway.GatewayResponse;
import wiki.creeper.superiorskyblockIntegeration.redis.RedisManager;

/**
 * Persists idempotent operation results in Redis to support at-least-once delivery.
 */
public final class IdempotencyService {

    private final RedisManager redisManager;
    private final Duration ttl;
    private final java.util.logging.Logger logger;

    public IdempotencyService(RedisManager redisManager, Duration ttl, java.util.logging.Logger logger) {
        this.redisManager = redisManager;
        this.ttl = ttl;
        this.logger = logger;
    }

    public Optional<GatewayResponse> fetch(String key) {
        try (Jedis jedis = redisManager.pool().getResource()) {
            String json = jedis.get(key);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(GatewayResponse.fromJson(json));
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Failed to fetch idempotent result for " + key, ex);
            return Optional.empty();
        }
    }

    public void store(String key, GatewayResponse response) {
        try (Jedis jedis = redisManager.pool().getResource()) {
            jedis.setex(key, (int) ttl.toSeconds(), response.toJson());
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Failed to store idempotent result for " + key, ex);
        }
    }
}
