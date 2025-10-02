package wiki.creeper.superiorskyblockIntegeration.api.events;

import com.google.gson.JsonObject;
import org.bukkit.event.HandlerList;

import java.util.UUID;

public final class NetworkMemberAddedEvent extends NetworkIslandEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID memberUuid;
    private final String memberName;

    public NetworkMemberAddedEvent(UUID actorUuid,
                                   UUID islandUuid,
                                   String islandName,
                                   UUID memberUuid,
                                   String memberName,
                                   JsonObject data) {
        super(actorUuid, islandUuid, islandName, data);
        this.memberUuid = memberUuid;
        this.memberName = memberName;
    }

    public UUID getMemberUuid() {
        return memberUuid;
    }

    public String getMemberName() {
        return memberName;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
