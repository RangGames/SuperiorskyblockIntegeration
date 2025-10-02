package wiki.creeper.superiorskyblockIntegeration.api.events;

import com.google.gson.JsonObject;
import org.bukkit.event.HandlerList;

import java.util.UUID;

public final class NetworkIslandUpdatedEvent extends NetworkIslandEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String upgradeName;

    public NetworkIslandUpdatedEvent(UUID actorUuid,
                                     UUID islandUuid,
                                     String islandName,
                                     String upgradeName,
                                     JsonObject data) {
        super(actorUuid, islandUuid, islandName, data);
        this.upgradeName = upgradeName;
    }

    public String getUpgradeName() {
        return upgradeName;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
