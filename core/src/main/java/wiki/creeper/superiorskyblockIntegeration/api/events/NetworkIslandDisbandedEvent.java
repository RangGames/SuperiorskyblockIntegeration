package wiki.creeper.superiorskyblockIntegeration.api.events;

import com.google.gson.JsonObject;
import org.bukkit.event.HandlerList;

import java.util.UUID;

public final class NetworkIslandDisbandedEvent extends NetworkIslandEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public NetworkIslandDisbandedEvent(UUID actorUuid,
                                       UUID islandUuid,
                                       String islandName,
                                       JsonObject data) {
        super(actorUuid, islandUuid, islandName, data);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
