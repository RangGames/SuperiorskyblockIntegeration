package wiki.creeper.superiorskyblockIntegeration.api.events;

import com.google.gson.JsonObject;
import org.bukkit.event.HandlerList;

import java.util.UUID;

public final class NetworkInviteCreatedEvent extends NetworkIslandEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID targetUuid;
    private final String targetName;

    public NetworkInviteCreatedEvent(UUID actorUuid,
                                     UUID islandUuid,
                                     String islandName,
                                     UUID targetUuid,
                                     String targetName,
                                     JsonObject data) {
        super(actorUuid, islandUuid, islandName, data);
        this.targetUuid = targetUuid;
        this.targetName = targetName;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public String getTargetName() {
        return targetName;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
