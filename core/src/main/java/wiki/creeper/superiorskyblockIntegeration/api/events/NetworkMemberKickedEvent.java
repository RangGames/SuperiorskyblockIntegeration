package wiki.creeper.superiorskyblockIntegeration.api.events;

import com.google.gson.JsonObject;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Fired when a member is forcibly removed (kicked) from a network island.
 */
public final class NetworkMemberKickedEvent extends NetworkIslandEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID memberUuid;
    private final String memberName;
    private final String reason;

    public NetworkMemberKickedEvent(UUID actorUuid,
                                    UUID islandUuid,
                                    String islandName,
                                    UUID memberUuid,
                                    String memberName,
                                    String reason,
                                    JsonObject data) {
        super(actorUuid, islandUuid, islandName, data);
        this.memberUuid = memberUuid;
        this.memberName = memberName;
        this.reason = reason;
    }

    public UUID getMemberUuid() {
        return memberUuid;
    }

    public String getMemberName() {
        return memberName;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
