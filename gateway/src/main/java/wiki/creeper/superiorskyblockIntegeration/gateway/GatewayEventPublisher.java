package wiki.creeper.superiorskyblockIntegeration.gateway;

import com.google.gson.JsonObject;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.logging.Level;

import wiki.creeper.superiorskyblockIntegeration.redis.MessageSecurity;
import wiki.creeper.superiorskyblockIntegeration.redis.RedisChannels;
import wiki.creeper.superiorskyblockIntegeration.redis.RedisManager;
import wiki.creeper.superiorskyblockIntegeration.redis.RedisMessage;

final class GatewayEventPublisher {

    private final JavaPlugin plugin;
    private final RedisManager redisManager;
    private final RedisChannels channels;
    private final MessageSecurity security;

    GatewayEventPublisher(JavaPlugin plugin,
                          RedisManager redisManager,
                          RedisChannels channels,
                          MessageSecurity security) {
        this.plugin = plugin;
        this.redisManager = redisManager;
        this.channels = channels;
        this.security = security;
    }

    void publishInviteCreated(UUID actor, JsonObject data) {
        publish("invite.created", actor, data);
    }

    void publishInviteRevoked(UUID actor, JsonObject data) {
        publish("invite.revoked", actor, data);
    }

    void publishMemberAdded(UUID actor, JsonObject data) {
        publish("member.added", actor, data);
    }

    void publishMemberRemoved(UUID actor, JsonObject data) {
        publish("member.removed", actor, data);
    }

    void publishIslandUpdated(UUID actor, JsonObject data) {
        publish("island.updated", actor, data);
    }

    void publishIslandDisbanded(UUID actor, JsonObject data) {
        publish("island.disbanded", actor, data);
    }

    private void publish(String eventType, UUID actor, JsonObject payload) {
        try {
            RedisMessage message = RedisMessage.request(eventType);
            if (actor != null) {
                message.setActor(actor.toString());
            }
            message.mergeData(payload);
            security.sign(message);
            String channel = channels.eventChannel(eventType);
            redisManager.publish(channel, message.toJson());
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to publish event " + eventType, ex);
        }
    }
}
