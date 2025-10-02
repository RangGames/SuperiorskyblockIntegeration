package wiki.creeper.superiorskyblockIntegeration.client.messaging;

import com.google.gson.JsonObject;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.PluginManager;
import redis.clients.jedis.JedisPubSub;

import java.util.UUID;
import java.util.logging.Level;

import wiki.creeper.superiorskyblockIntegeration.api.events.NetworkInviteCreatedEvent;
import wiki.creeper.superiorskyblockIntegeration.api.events.NetworkInviteRevokedEvent;
import wiki.creeper.superiorskyblockIntegeration.api.events.NetworkIslandDisbandedEvent;
import wiki.creeper.superiorskyblockIntegeration.api.events.NetworkIslandUpdatedEvent;
import wiki.creeper.superiorskyblockIntegeration.api.events.NetworkMemberAddedEvent;
import wiki.creeper.superiorskyblockIntegeration.api.events.NetworkMemberRemovedEvent;
import wiki.creeper.superiorskyblockIntegeration.client.cache.ClientCache;
import wiki.creeper.superiorskyblockIntegeration.redis.MessageSecurity;
import wiki.creeper.superiorskyblockIntegeration.redis.RedisChannels;
import wiki.creeper.superiorskyblockIntegeration.redis.RedisMessage;

public final class ClientRedisListener extends JedisPubSub {

    private final JavaPlugin plugin;
    private final MessageSecurity security;
    private final RedisChannels channels;
    private final ClientPendingRequests pendingRequests;
    private final ClientCache cache;

    public ClientRedisListener(JavaPlugin plugin,
                               MessageSecurity security,
                               RedisChannels channels,
                               ClientPendingRequests pendingRequests,
                               ClientCache cache) {
        this.plugin = plugin;
        this.security = security;
        this.channels = channels;
        this.pendingRequests = pendingRequests;
        this.cache = cache;
    }

    @Override
    public void onPMessage(String pattern, String channel, String message) {
        try {
            RedisMessage payload = RedisMessage.parse(message);
            if (!security.verify(payload)) {
                plugin.getLogger().warning("Dropped message with invalid signature from " + channel);
                return;
            }
            if (channels.isResponseChannel(channel)) {
                pendingRequests.complete(payload.id(), payload);
            } else if (channels.isEventChannel(channel)) {
                String eventType = channel.substring(channel.lastIndexOf('.') + 1);
                cache.invalidateByEvent(eventType, payload);
                emitEvent(eventType, payload);
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Client listener failed to handle message", ex);
        }
    }

    public void gracefulShutdown() {
        try {
            this.punsubscribe();
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Error while unsubscribing client listener", ex);
        }
    }

    private void emitEvent(String eventType, RedisMessage payload) {
        String serialized = payload.toJson();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            RedisMessage copy = RedisMessage.parse(serialized);
            dispatchEvent(eventType, copy);
        });
    }

    private void dispatchEvent(String eventType, RedisMessage payload) {
        PluginManager pm = plugin.getServer().getPluginManager();
        JsonObject data = payload.data().deepCopy();
        UUID actorUuid = parseUuid(payload.actor());
        UUID islandUuid = parseUuid(asString(data, "islandId"));
        String islandName = asString(data, "islandName");

        switch (eventType) {
            case "invite.created" -> pm.callEvent(new NetworkInviteCreatedEvent(
                    actorUuid,
                    islandUuid,
                    islandName,
                    parseUuid(asString(data, "targetUuid")),
                    asString(data, "targetName"),
                    data));
            case "invite.revoked" -> pm.callEvent(new NetworkInviteRevokedEvent(
                    actorUuid,
                    islandUuid,
                    islandName,
                    asString(data, "inviteId"),
                    data));
            case "member.added" -> pm.callEvent(new NetworkMemberAddedEvent(
                    actorUuid,
                    islandUuid,
                    islandName,
                    parseUuid(asString(data, "memberUuid")),
                    asString(data, "memberName"),
                    data));
            case "member.removed" -> pm.callEvent(new NetworkMemberRemovedEvent(
                    actorUuid,
                    islandUuid,
                    islandName,
                    parseUuid(asString(data, "memberUuid")),
                    asString(data, "memberName"),
                    asString(data, "cause"),
                    data));
            case "island.updated" -> pm.callEvent(new NetworkIslandUpdatedEvent(
                    actorUuid,
                    islandUuid,
                    islandName,
                    asString(data, "upgradeName"),
                    data));
            case "island.disbanded" -> pm.callEvent(new NetworkIslandDisbandedEvent(
                    actorUuid,
                    islandUuid,
                    islandName,
                    data));
            default -> {
                // ignore unsupported event
            }
        }
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String asString(JsonObject data, String field) {
        return data.has(field) && !data.get(field).isJsonNull() ? data.get(field).getAsString() : null;
    }
}
