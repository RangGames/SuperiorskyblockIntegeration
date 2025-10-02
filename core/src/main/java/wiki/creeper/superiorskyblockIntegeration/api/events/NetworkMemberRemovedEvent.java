package wiki.creeper.superiorskyblockIntegeration.api.events;

import com.google.gson.JsonObject;
import org.bukkit.event.HandlerList;

import java.util.UUID;

public final class NetworkMemberRemovedEvent extends NetworkIslandEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID memberUuid;
    private final String memberName;
    private final String cause;

    public NetworkMemberRemovedEvent(UUID actorUuid,
                                     UUID islandUuid,
                                     String islandName,
                                     UUID memberUuid,
                                     String memberName,
                                     String cause,
                                     JsonObject data) {
        super(actorUuid, islandUuid, islandName, data);
        this.memberUuid = memberUuid;
        this.memberName = memberName;
        this.cause = cause;
    }

    public UUID getMemberUuid() {
        return memberUuid;
    }

    public String getMemberName() {
        return memberName;
    }

    public String getCause() {
        return cause;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
