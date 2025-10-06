package wiki.creeper.superiorskyblockIntegeration.gateway.data;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.google.gson.JsonObject;

import java.util.UUID;
import java.util.logging.Logger;

import wiki.creeper.superiorskyblockIntegeration.redis.RedisChannels;
import wiki.creeper.superiorskyblockIntegeration.redis.RedisManager;
import wiki.creeper.superiorskyblockIntegeration.api.PlayerMetadataService;

/**
 * Responds to head-data bus requests and keeps SuperiorSkyblock textures up-to-date.
 */
public final class GatewayHeadDataService {

    private final RedisManager redisManager;
    private final RedisChannels channels;
    private final Logger logger;
    private final PlayerMetadataService metadataService;

    public GatewayHeadDataService(RedisManager redisManager,
                                  RedisChannels channels,
                                  Logger logger,
                                  PlayerMetadataService metadataService) {
        this.redisManager = redisManager;
        this.channels = channels;
        this.logger = logger;
        this.metadataService = metadataService;
    }

    public void handleRequest(JsonObject payload) {
        if (!payload.has("uuid")) {
            return;
        }
        String rawUuid = payload.get("uuid").getAsString();
        String playerName = payload.has("name") ? payload.get("name").getAsString() : "unknown";
        String texture = null;
        try {
            UUID uuid = UUID.fromString(rawUuid);
            SuperiorPlayer superiorPlayer = SuperiorSkyblockAPI.getPlayer(uuid);
            if (superiorPlayer != null) {
                texture = superiorPlayer.getTextureValue();
                if (texture != null && !texture.isBlank()) {
                    superiorPlayer.setTextureValue(texture);
                }
            }
        } catch (IllegalArgumentException ex) {
            logger.warning("Invalid UUID received in head-data request: " + rawUuid);
        }

        JsonObject response = new JsonObject();
        response.addProperty("uuid", rawUuid);
        response.addProperty("name", playerName);
        if (texture != null) {
            response.addProperty("texture", texture);
            metadataService.put(UUID.fromString(rawUuid), "skin.texture", texture, java.time.Duration.ofHours(24))
                    .exceptionally(ex -> {
                        logger.log(java.util.logging.Level.WARNING, "Failed to cache head texture", ex);
                        return null;
                    });
        }
        redisManager.publish(channels.busChannel("headdata.response"), response.toString());
    }
}
