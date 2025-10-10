package wiki.creeper.superiorskyblockIntegeration.gateway;

import com.google.gson.JsonObject;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.logging.Level;

import wiki.creeper.superiorskyblockIntegeration.client.lang.Messages;
import wiki.creeper.superiorskyblockIntegeration.redis.MessageSecurity;
import wiki.creeper.superiorskyblockIntegeration.redis.RedisChannels;
import wiki.creeper.superiorskyblockIntegeration.redis.RedisManager;
import wiki.creeper.superiorskyblockIntegeration.redis.RedisMessage;

final class GatewayEventPublisher {

    private final JavaPlugin plugin;
    private final RedisManager redisManager;
    private final RedisChannels channels;
    private final MessageSecurity security;
    private final int compressionThreshold;
    private final Messages messages;
    private final boolean redisDebug;

    GatewayEventPublisher(JavaPlugin plugin,
                          RedisManager redisManager,
                          RedisChannels channels,
                          MessageSecurity security,
                          int compressionThreshold,
                          Messages messages,
                          boolean redisDebug) {
        this.plugin = plugin;
        this.redisManager = redisManager;
        this.channels = channels;
        this.security = security;
        this.compressionThreshold = compressionThreshold;
        this.messages = messages;
        this.redisDebug = redisDebug;
    }

    void publishInviteCreated(UUID actor, JsonObject data) {
        notifyTarget(data);
        debug("invite.created", data);
        publish("invite.created", actor, data);
    }

    void publishInviteRevoked(UUID actor, JsonObject data) {
        debug("invite.revoked", data);
        publish("invite.revoked", actor, data);
    }

    void publishMemberAdded(UUID actor, JsonObject data) {
        debug("member.added", data);
        publish("member.added", actor, data);
    }

    void publishMemberRemoved(UUID actor, JsonObject data) {
        debug("member.removed", data);
        publish("member.removed", actor, data);
    }

    void publishMemberKicked(UUID actor, JsonObject data) {
        debug("member.kicked", data);
        publish("member.kicked", actor, data);
    }

    void publishIslandUpdated(UUID actor, JsonObject data) {
        debug("island.updated", data);
        publish("island.updated", actor, data);
    }

    void publishIslandDisbanded(UUID actor, JsonObject data) {
        debug("island.disbanded", data);
        publish("island.disbanded", actor, data);
    }

    void publishInviteResult(String action,
                             UUID actorUuid,
                             UUID targetUuid,
                             JsonObject data) {
        if (action == null || action.isBlank() || actorUuid == null || targetUuid == null) {
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("action", action);
        payload.addProperty("actor", actorUuid.toString());
        payload.addProperty("target", targetUuid.toString());
        if (data != null) {
            if (data.has("memberName")) {
                payload.addProperty("actorName", data.get("memberName").getAsString());
            }
            if (data.has("inviterName")) {
                payload.addProperty("targetName", data.get("inviterName").getAsString());
            }
            if (data.has("islandId")) {
                payload.addProperty("islandId", data.get("islandId").getAsString());
            }
            if (data.has("islandName")) {
                payload.addProperty("islandName", data.get("islandName").getAsString());
            }
        }
        redisManager.publish(channels.busChannel("islandinviteresult"), payload.toString());
    }

    void publishInviteRefresh(UUID playerUuid) {
        if (playerUuid == null) {
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("playerUuid", playerUuid.toString());
        redisManager.publish(channels.busChannel("invite.refresh"), payload.toString());
    }

    private void publish(String eventType, UUID actor, JsonObject payload) {
        try {
            RedisMessage message = RedisMessage.request(eventType);
            if (actor != null) {
                message.setActor(actor.toString());
            }
            message.mergeData(payload);
            message.compressDataIfNeeded(compressionThreshold);
            security.sign(message);
            String channel = channels.eventChannel(eventType);
            redisManager.publish(channel, message.toJson());
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to publish event " + eventType, ex);
        }
    }

    private void notifyTarget(JsonObject data) {
        if (messages == null || data == null || !data.has("targetUuid") || data.get("targetUuid").isJsonNull()) {
            return;
        }
        try {
            UUID targetUuid = UUID.fromString(data.get("targetUuid").getAsString());
            var player = plugin.getServer().getPlayer(targetUuid);
            if (player == null) {
                return;
            }
            String inviterName = data.has("inviterName") && !data.get("inviterName").isJsonNull()
                    ? data.get("inviterName").getAsString()
                    : "누군가";
            player.sendMessage(messages.format("invite.created-target", inviterName, messages.commandLabel()));
        } catch (IllegalArgumentException ignored) {
            // malformed uuid, ignore
        }
    }

    private void debug(String eventType, JsonObject payload) {
        if (!redisDebug) {
            return;
        }
        plugin.getLogger().info("[RedisEvt] " + eventType + " -> " + payload);
    }
}
