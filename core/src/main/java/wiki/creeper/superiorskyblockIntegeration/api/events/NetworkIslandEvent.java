package wiki.creeper.superiorskyblockIntegeration.api.events;

import com.google.gson.JsonObject;
import org.bukkit.event.Event;

import java.util.UUID;

/**
 * Base class for custom network-wide SuperiorSkyblock events raised by the client component.
 */
public abstract class NetworkIslandEvent extends Event {

    private final UUID actorUuid;
    private final UUID islandUuid;
    private final String islandName;
    private final JsonObject data;

    protected NetworkIslandEvent(UUID actorUuid, UUID islandUuid, String islandName, JsonObject data) {
        super(false);
        this.actorUuid = actorUuid;
        this.islandUuid = islandUuid;
        this.islandName = islandName;
        this.data = data != null ? data.deepCopy() : new JsonObject();
    }

    public UUID getActorUuid() {
        return actorUuid;
    }

    public UUID getIslandUuid() {
        return islandUuid;
    }

    public String getIslandName() {
        return islandName;
    }

    public JsonObject getData() {
        return data.deepCopy();
    }
}
