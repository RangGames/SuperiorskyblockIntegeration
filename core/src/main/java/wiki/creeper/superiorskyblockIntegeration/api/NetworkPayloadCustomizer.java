package wiki.creeper.superiorskyblockIntegeration.api;

import wiki.creeper.superiorskyblockIntegeration.redis.RedisMessage;

@FunctionalInterface
public interface NetworkPayloadCustomizer {
    void apply(RedisMessage message);
}
