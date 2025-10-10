package wiki.creeper.superiorskyblockIntegeration.client.services;

import com.google.gson.JsonObject;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;

import wiki.creeper.superiorskyblockIntegeration.api.PlayerMetadataService;
import wiki.creeper.superiorskyblockIntegeration.redis.RedisChannels;
import wiki.creeper.superiorskyblockIntegeration.redis.RedisManager;

/**
 * Handles head texture data requests across the network bus.
 */
public final class ClientHeadDataService {

    private final JavaPlugin plugin;
    private final RedisManager redisManager;
    private final RedisChannels channels;
    private final PlayerMetadataService metadataService;
    private final Map<UUID, List<HeadDataConsumer>> pending = new ConcurrentHashMap<>();

    public ClientHeadDataService(JavaPlugin plugin,
                                 RedisManager redisManager,
                                 RedisChannels channels,
                                 PlayerMetadataService metadataService) {
        this.plugin = plugin;
        this.redisManager = redisManager;
        this.channels = channels;
        this.metadataService = metadataService;
    }

    public PlayerMetadataService getMetadataService() {
        return metadataService;
    }

    public void requestHeadData(Player player) {
        if (player == null) {
            return;
        }
        requestHeadData(player.getUniqueId(), player.getName(), texture -> { });
    }

    public void requestHeadData(UUID uuid, String name, Consumer<String> callback) {
        if (uuid == null) {
            return;
        }
        if (metadataService != null) {
            metadataService.get(uuid, "skin.texture").thenAccept(optional -> {
                if (optional.isPresent()) {
                    if (callback != null) {
                        callback.accept(optional.get());
                    }
                    return;
                }
                enqueueRequest(uuid, name, callback);
            }).exceptionally(ex -> {
                enqueueRequest(uuid, name, callback);
                return null;
            });
        } else {
            enqueueRequest(uuid, name, callback);
        }
    }

    private void enqueueRequest(UUID uuid, String name, Consumer<String> callback) {
        pending.compute(uuid, (key, consumers) -> {
            if (consumers == null) {
                consumers = new CopyOnWriteArrayList<>();
                publishRequest(uuid, name);
            }
            if (callback != null) {
                consumers.add((player, texture) -> callback.accept(texture));
            }
            return consumers;
        });
    }

    private void publishRequest(UUID uuid, String name) {
        JsonObject payload = new JsonObject();
        payload.addProperty("uuid", uuid.toString());
        if (name != null && !name.isBlank()) {
            payload.addProperty("name", name);
        }
        payload.addProperty("server", plugin.getServer().getName());
        redisManager.publish(channels.busChannel("headdata.request"), payload.toString());
    }

    public void handleHeadDataResponse(JsonObject payload, HeadDataConsumer consumer) {
        if (!payload.has("uuid")) {
            return;
        }
        UUID uuid = UUID.fromString(payload.get("uuid").getAsString());
        String texture = payload.has("texture") ? payload.get("texture").getAsString() : null;
        if (metadataService != null && texture != null) {
            metadataService.put(uuid, "skin.texture", texture, Duration.ofHours(24));
        }
        List<HeadDataConsumer> callbacks = pending.remove(uuid);
        Player player = plugin.getServer().getPlayer(uuid);
        if (callbacks != null) {
            for (HeadDataConsumer callback : callbacks) {
                try {
                    callback.accept(player, texture);
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.WARNING, "Head data callback failed for " + uuid, ex);
                }
            }
        }
        if (consumer != null) {
            consumer.accept(player, texture);
        }
    }

    @FunctionalInterface
    public interface HeadDataConsumer {
        void accept(Player player, String textureValue);
    }
}
