package wiki.creeper.superiorskyblockIntegeration.client.services;

import com.google.gson.JsonObject;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import wiki.creeper.superiorskyblockIntegeration.api.PlayerMetadataService;
import wiki.creeper.superiorskyblockIntegeration.redis.RedisChannels;
import wiki.creeper.superiorskyblockIntegeration.redis.RedisManager;

/**
 * Handles head texture data requests across the network bus.
 */
public final class ClientHeadDataService {

    private final RedisManager redisManager;
    private final RedisChannels channels;
    private final PlayerMetadataService metadataService;
    private final Map<UUID, Player> pending = new ConcurrentHashMap<>();

    public ClientHeadDataService(RedisManager redisManager,
                                 RedisChannels channels,
                                 PlayerMetadataService metadataService) {
        this.redisManager = redisManager;
        this.channels = channels;
        this.metadataService = metadataService;
    }

    public PlayerMetadataService getMetadataService() {
        return metadataService;
    }

    public void requestHeadData(Player player) {
        UUID uuid = player.getUniqueId();
        if (metadataService == null) {
            pending.put(uuid, player);
            publishRequest(player);
            return;
        }
        metadataService.get(uuid, "skin.texture").thenAccept(optional -> {
            if (optional.isPresent()) {
                // cached value exists; no network request required
                return;
            }
            pending.put(uuid, player);
            publishRequest(player);
        }).exceptionally(ex -> {
            pending.put(uuid, player);
            publishRequest(player);
            return null;
        });
    }

    private void publishRequest(Player player) {
        JsonObject payload = new JsonObject();
        payload.addProperty("uuid", player.getUniqueId().toString());
        payload.addProperty("name", player.getName());
        payload.addProperty("server", player.getServer().getName());
        redisManager.publish(channels.busChannel("headdata.request"), payload.toString());
    }

    public void handleHeadDataResponse(JsonObject payload, HeadDataConsumer consumer) {
        if (!payload.has("uuid")) {
            return;
        }
        UUID uuid = UUID.fromString(payload.get("uuid").getAsString());
        String texture = payload.has("texture") ? payload.get("texture").getAsString() : null;
        Player player = pending.remove(uuid);
        consumer.accept(player, texture);
        if (metadataService != null && texture != null) {
            metadataService.put(uuid, "skin.texture", texture, Duration.ofHours(24));
        }
    }

    @FunctionalInterface
    public interface HeadDataConsumer {
        void accept(Player player, String textureValue);
    }
}
