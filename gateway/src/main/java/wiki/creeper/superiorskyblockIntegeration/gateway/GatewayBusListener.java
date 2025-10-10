package wiki.creeper.superiorskyblockIntegeration.gateway;

import com.google.gson.JsonObject;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.papermc.lib.PaperLib;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import wiki.creeper.superiorskyblockIntegeration.gateway.data.GatewayHeadDataService;
import wiki.creeper.superiorskyblockIntegeration.redis.RedisChannels;
import wiki.creeper.superiorskyblockIntegeration.redis.RedisCodec;
import wiki.creeper.superiorskyblockIntegeration.redis.RedisManager;

final class GatewayBusListener extends RedisPubSubAdapter<String, String> {

    private final JavaPlugin plugin;
    private final RedisChannels channels;
    private final GatewayHeadDataService headDataService;
    private final Logger logger;
    private final RedisManager redisManager;

    GatewayBusListener(JavaPlugin plugin,
                       Logger logger,
                       RedisManager redisManager,
                       RedisChannels channels,
                       GatewayHeadDataService headDataService) {
        this.plugin = plugin;
        this.logger = logger;
        this.redisManager = redisManager;
        this.channels = channels;
        this.headDataService = headDataService;
    }

    void register(StatefulRedisPubSubConnection<String, String> connection) {
        connection.addListener(this);
        connection.async().psubscribe(channels.busPattern());
    }

    public void message(String channel, String message) {
        // not used
    }

    @Override
    public void message(String pattern, String channel, String message) {
        try {
            String topic = channels.busTopic(channel);
            if ("headdata.request".equals(topic)) {
                JsonObject payload = RedisCodec.gson().fromJson(message, JsonObject.class);
                headDataService.handleRequest(payload);
            } else if ("farm.command".equals(topic)) {
                handleFarmCommand(message);
            } else if ("farm.setloclobby".equals(topic)) {
                handleSetLocLobby(message);
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Gateway bus listener failed to process message", ex);
        }
    }

    @Override
    public void subscribed(String channel, long count) {
    }

    @Override
    public void psubscribed(String pattern, long count) {
    }

    @Override
    public void unsubscribed(String channel, long count) {
    }

    @Override
    public void punsubscribed(String pattern, long count) {
    }

    private void handleFarmCommand(String rawMessage) {
        JsonObject payload = parseJson(rawMessage);
        if (payload == null) {
            handleLegacyFarmCommand(rawMessage);
            return;
        }
        String action = payload.has("action") ? payload.get("action").getAsString() : null;
        String playerRaw = payload.has("playerUuid") ? payload.get("playerUuid").getAsString() : null;
        if (action == null || action.isBlank() || playerRaw == null || playerRaw.isBlank()) {
            return;
        }
        JsonObject forward = new JsonObject();
        forward.addProperty("playerUuid", playerRaw);
        switch (action.toLowerCase()) {
            case "showtop" -> publish("farm.openTop", forward);
            case "showmembers" -> {
                String islandId = payload.has("islandId") ? payload.get("islandId").getAsString() : null;
                if (islandId == null || islandId.isBlank()) {
                    return;
                }
                forward.addProperty("islandId", islandId);
                publish("farm.openMembers", forward);
            }
            default -> logger.fine("Unknown farm command action: " + action);
        }
    }

    private void handleLegacyFarmCommand(String rawMessage) {
        if (rawMessage == null || !rawMessage.startsWith("farmcommand/")) {
            return;
        }
        String[] parts = rawMessage.split("/");
        if (parts.length < 4) {
            return;
        }
        String playerRaw = parts[1];
        String action = parts[2];
        if (playerRaw == null || action == null) {
            return;
        }
        JsonObject forward = new JsonObject();
        forward.addProperty("playerUuid", playerRaw);
        switch (action.toLowerCase()) {
            case "showtop" -> publish("farm.openTop", forward);
            case "showmember", "showmembers" -> {
                if (parts.length >= 4) {
                    forward.addProperty("islandId", parts[3]);
                    publish("farm.openMembers", forward);
                }
            }
            default -> {
                // ignore
            }
        }
    }

    private void handleSetLocLobby(String rawMessage) {
        JsonObject payload = parseJson(rawMessage);
        if (payload == null) {
            return;
        }
        String playerRaw = payload.has("playerUuid") ? payload.get("playerUuid").getAsString() : null;
        if (playerRaw == null || playerRaw.isBlank()) {
            return;
        }
        UUID targetUuid;
        try {
            targetUuid = UUID.fromString(playerRaw);
        } catch (IllegalArgumentException ex) {
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer superiorPlayer =
                    com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI.getPlayer(targetUuid);
            if (superiorPlayer == null) {
                return;
            }
            com.bgsoftware.superiorskyblock.api.island.Island island = superiorPlayer.getIsland();
            if (island == null) {
                return;
            }
            Location center = island.getCenter(World.Environment.NORMAL);
            if (center == null && superiorPlayer.isOnline()) {
                Player online = superiorPlayer.asPlayer();
                if (online != null) {
                    center = island.getCenter(online.getWorld().getEnvironment());
                }
            }
            if (center == null) {
                return;
            }
            if (superiorPlayer.isOnline()) {
                Player bukkitPlayer = superiorPlayer.asPlayer();
                if (bukkitPlayer != null) {
                    final Player playerRef = bukkitPlayer;
                    final Location destination = center;
                    PaperLib.teleportAsync(playerRef, destination)
                            .exceptionally(ex -> {
                                logger.log(Level.WARNING, "Failed to teleport player asynchronously", ex);
                                plugin.getServer().getScheduler().runTask(plugin, () -> playerRef.teleport(destination));
                                return null;
                            });
                }
            }
        });
    }

    private void publish(String topic, JsonObject payload) {
        if (payload == null) {
            return;
        }
        redisManager.publish(channels.busChannel(topic), payload.toString());
    }

    private JsonObject parseJson(String raw) {
        try {
            return RedisCodec.gson().fromJson(raw, JsonObject.class);
        } catch (Exception ex) {
            logger.log(Level.FINE, "Invalid JSON payload on bus: " + raw, ex);
            return null;
        }
    }
}
