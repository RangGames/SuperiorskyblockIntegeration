package wiki.creeper.superiorskyblockIntegeration.client.messaging;

import com.google.gson.JsonObject;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

import wiki.creeper.superiorskyblockIntegeration.api.events.NetworkInviteCreatedEvent;
import wiki.creeper.superiorskyblockIntegeration.api.events.NetworkInviteRevokedEvent;
import wiki.creeper.superiorskyblockIntegeration.api.events.NetworkIslandDisbandedEvent;
import wiki.creeper.superiorskyblockIntegeration.api.events.NetworkIslandUpdatedEvent;
import wiki.creeper.superiorskyblockIntegeration.api.events.NetworkMemberAddedEvent;
import wiki.creeper.superiorskyblockIntegeration.api.events.NetworkMemberKickedEvent;
import wiki.creeper.superiorskyblockIntegeration.api.events.NetworkMemberRemovedEvent;
import wiki.creeper.superiorskyblockIntegeration.client.cache.ClientCache;
import wiki.creeper.superiorskyblockIntegeration.client.menu.IslandMenuManager;
import wiki.creeper.superiorskyblockIntegeration.client.services.ClientHeadDataService;
import wiki.creeper.superiorskyblockIntegeration.client.lang.Messages;
import wiki.creeper.superiorskyblockIntegeration.redis.MessageSecurity;
import wiki.creeper.superiorskyblockIntegeration.redis.RedisChannels;
import wiki.creeper.superiorskyblockIntegeration.redis.RedisMessage;

public final class ClientRedisListener extends RedisPubSubAdapter<String, String> {

    private final JavaPlugin plugin;
    private final MessageSecurity security;
    private final RedisChannels channels;
    private final ClientPendingRequests pendingRequests;
    private final ClientCache cache;
    private final ClientHeadDataService headDataService;
    private final IslandMenuManager menus;
    private final Messages messages;
    private StatefulRedisPubSubConnection<String, String> connection;

    public ClientRedisListener(JavaPlugin plugin,
                               MessageSecurity security,
                               RedisChannels channels,
                               ClientPendingRequests pendingRequests,
                               ClientCache cache,
                               ClientHeadDataService headDataService,
                               IslandMenuManager menus,
                               Messages messages) {
        this.plugin = plugin;
        this.security = security;
        this.channels = channels;
        this.pendingRequests = pendingRequests;
        this.cache = cache;
        this.headDataService = headDataService;
        this.menus = menus;
        this.messages = messages;
    }

    public void register(StatefulRedisPubSubConnection<String, String> connection) {
        this.connection = connection;
        connection.addListener(this);
        connection.async().psubscribe(channels.responsePattern(), channels.eventPattern(), channels.busPattern());
    }

    @Override
    public void message(String pattern, String channel, String message) {
        try {
            if (channels.isBusChannel(channel)) {
                handleBusChannel(channel, message);
                return;
            }
            if (messages != null && messages.redisDebugEnabled()) {
                plugin.getLogger().info("[RedisEvt] recv " + channel + " => " + message);
            }
            RedisMessage payload = RedisMessage.parse(message);
            if (!security.verify(payload)) {
                plugin.getLogger().warning("Dropped message with invalid signature from " + channel);
                return;
            }
            payload.decompressDataIfNeeded();
            if (channels.isResponseChannel(channel)) {
                pendingRequests.complete(payload.id(), payload);
            } else if (channels.isEventChannel(channel)) {
                String eventType = extractEventType(channel);
                cache.invalidateByEvent(eventType, payload);
                emitEvent(eventType, payload);
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Client listener failed to handle message", ex);
        }
    }

    public void gracefulShutdown() {
        if (connection == null) {
            return;
        }
        try {
            CompletableFuture<?> future = connection.async()
                    .punsubscribe(channels.responsePattern(), channels.eventPattern(), channels.busPattern())
                    .toCompletableFuture();
            future.join();
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Error while unsubscribing client listener", ex);
        } finally {
            try {
                connection.removeListener(this);
                connection.close();
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Error while closing client pub/sub connection", ex);
            }
        }
    }

    @Override
    public void message(String channel, String message) {
        // not used
    }

    @Override
    public void subscribed(String channel, long count) {
        // no-op
    }

    @Override
    public void psubscribed(String pattern, long count) {
        // no-op
    }

    @Override
    public void unsubscribed(String channel, long count) {
        // no-op
    }

    @Override
    public void punsubscribed(String pattern, long count) {
        // no-op
    }

    private String extractEventType(String channel) {
        String prefix = channels.eventChannel("");
        if (channel.startsWith(prefix)) {
            return channel.substring(prefix.length());
        }
        int lastDot = channel.lastIndexOf('.');
        return lastDot >= 0 ? channel.substring(lastDot + 1) : channel;
    }

    private void handleBusChannel(String channel, String rawMessage) {
        String topic = channels.busTopic(channel);
        if ("headdata.response".equals(topic)) {
            JsonObject payload = wiki.creeper.superiorskyblockIntegeration.redis.RedisCodec.gson()
                    .fromJson(rawMessage, JsonObject.class);
            headDataService.handleHeadDataResponse(payload, (player, texture) -> {
                // no immediate action; metadata service handles persistence
            });
        } else if ("islandinviteresult".equals(topic)) {
            JsonObject payload = wiki.creeper.superiorskyblockIntegeration.redis.RedisCodec.gson()
                    .fromJson(rawMessage, JsonObject.class);
            displayInviteResult(payload);
        } else if ("invite.refresh".equals(topic)) {
            JsonObject payload = wiki.creeper.superiorskyblockIntegeration.redis.RedisCodec.gson()
                    .fromJson(rawMessage, JsonObject.class);
            refreshPendingInvites(payload);
        } else if ("farm.openTop".equals(topic)) {
            JsonObject payload = wiki.creeper.superiorskyblockIntegeration.redis.RedisCodec.gson()
                    .fromJson(rawMessage, JsonObject.class);
            openFarmTop(payload);
        } else if ("farm.openMembers".equals(topic)) {
            JsonObject payload = wiki.creeper.superiorskyblockIntegeration.redis.RedisCodec.gson()
                    .fromJson(rawMessage, JsonObject.class);
            openFarmMembers(payload);
        }
    }

    private void displayInviteResult(JsonObject payload) {
        String action = payload.has("action") ? payload.get("action").getAsString() : "";
        UUID actorUuid = parseUuid(payload.has("actor") ? payload.get("actor").getAsString() : null);
        UUID targetUuid = parseUuid(payload.has("target") ? payload.get("target").getAsString() : null);

        if (targetUuid == null) {
            return;
        }

        org.bukkit.entity.Player targetPlayer = Bukkit.getPlayer(targetUuid);
        if (targetPlayer == null) {
            return;
        }

        String actorName = payload.has("actorName")
                ? payload.get("actorName").getAsString()
                : actorUuid != null ? Bukkit.getOfflinePlayer(actorUuid).getName() : null;
        if (actorName == null || actorName.isBlank()) {
            actorName = "누군가";
        }

        switch (action) {
            case "accept" -> targetPlayer.sendMessage(messages.format("invite.accepted-notify", actorName, messages.commandLabel()));
            case "reject" -> targetPlayer.sendMessage(messages.format("invite.rejected-notify", actorName, messages.commandLabel()));
            default -> {
            }
        }
    }

    private void refreshPendingInvites(JsonObject payload) {
        if (menus == null) {
            return;
        }
        UUID targetUuid = parseUuid(asString(payload, "playerUuid"));
        if (targetUuid == null) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            org.bukkit.entity.Player player = plugin.getServer().getPlayer(targetUuid);
            if (player == null) {
                return;
            }
            menus.refreshPendingInvites(player);
        });
    }

    private void openFarmTop(JsonObject payload) {
        if (menus == null) {
            return;
        }
        java.util.UUID playerUuid = parseUuid(asString(payload, "playerUuid"));
        if (playerUuid == null) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            org.bukkit.entity.Player player = plugin.getServer().getPlayer(playerUuid);
            if (player == null) {
                return;
            }
            menus.openFarmRanking(player);
        });
    }

    private void openFarmMembers(JsonObject payload) {
        if (menus == null) {
            return;
        }
        java.util.UUID playerUuid = parseUuid(asString(payload, "playerUuid"));
        String islandId = asString(payload, "islandId");
        if (playerUuid == null || islandId == null || islandId.isBlank()) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            org.bukkit.entity.Player player = plugin.getServer().getPlayer(playerUuid);
            if (player == null) {
                return;
            }
            menus.openFarmMemberRanking(player, islandId);
        });
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
            case "invite.created" -> {
                pm.callEvent(new NetworkInviteCreatedEvent(
                        actorUuid,
                        islandUuid,
                        islandName,
                        parseUuid(asString(data, "targetUuid")),
                        asString(data, "targetName"),
                        data));
                notifyInviteCreated(data);
            }
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
            case "member.kicked" -> pm.callEvent(new NetworkMemberKickedEvent(
                    actorUuid,
                    islandUuid,
                    islandName,
                    parseUuid(asString(data, "memberUuid")),
                    asString(data, "memberName"),
                    asString(data, "reason"),
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

    private void notifyInviteCreated(JsonObject data) {
        if (messages == null) {
            return;
        }
        UUID targetUuid = parseUuid(asString(data, "targetUuid"));
        if (targetUuid == null) {
            return;
        }
        org.bukkit.entity.Player targetPlayer = plugin.getServer().getPlayer(targetUuid);
        if (targetPlayer == null) {
            return;
        }
        String inviterName = asString(data, "inviterName");
        if (inviterName == null || inviterName.isBlank()) {
            inviterName = "누군가";
        }
        targetPlayer.sendMessage(messages.format("invite.created-target", inviterName, messages.commandLabel()));
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
