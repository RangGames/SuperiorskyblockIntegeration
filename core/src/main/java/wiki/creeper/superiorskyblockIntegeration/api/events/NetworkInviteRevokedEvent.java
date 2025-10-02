package wiki.creeper.superiorskyblockIntegeration.api.events;

import com.google.gson.JsonObject;
import org.bukkit.event.HandlerList;

import java.util.UUID;

public final class NetworkInviteRevokedEvent extends NetworkIslandEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String inviteId;

    public NetworkInviteRevokedEvent(UUID actorUuid,
                                     UUID islandUuid,
                                     String islandName,
                                     String inviteId,
                                     JsonObject data) {
        super(actorUuid, islandUuid, islandName, data);
        this.inviteId = inviteId;
    }

    public String getInviteId() {
        return inviteId;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
