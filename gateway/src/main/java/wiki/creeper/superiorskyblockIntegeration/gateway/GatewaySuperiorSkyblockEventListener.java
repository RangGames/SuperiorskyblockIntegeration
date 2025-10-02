package wiki.creeper.superiorskyblockIntegeration.gateway;

import com.bgsoftware.superiorskyblock.api.events.IslandDisbandEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandInviteEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandJoinEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandLeaveEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandUpgradeEvent;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.google.gson.JsonObject;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.UUID;

import wiki.creeper.superiorskyblockIntegeration.gateway.GatewayResponse;
import wiki.creeper.superiorskyblockIntegeration.gateway.bridge.SuperiorSkyblockBridge;
import wiki.creeper.superiorskyblockIntegeration.gateway.cache.PlayerIslandCache;

final class GatewaySuperiorSkyblockEventListener implements Listener {

    private final JavaPlugin plugin;
    private final SuperiorSkyblockBridge bridge;
    private final GatewayEventPublisher events;
    private final PlayerIslandCache islandCache;

    GatewaySuperiorSkyblockEventListener(JavaPlugin plugin,
                                         SuperiorSkyblockBridge bridge,
                                         GatewayEventPublisher events,
                                         PlayerIslandCache islandCache) {
        this.plugin = plugin;
        this.bridge = bridge;
        this.events = events;
        this.islandCache = islandCache;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInvite(IslandInviteEvent event) {
        Island island = event.getIsland();
        if (island == null) {
            return;
        }
        SuperiorPlayer inviter = event.getPlayer();
        SuperiorPlayer target = event.getTarget();
        JsonObject snapshot = islandSnapshot(island, false);
        if (target != null) {
            snapshot.addProperty("targetUuid", target.getUniqueId().toString());
            snapshot.addProperty("targetName", target.getName());
        }
        if (inviter != null) {
            snapshot.addProperty("inviterUuid", inviter.getUniqueId().toString());
            snapshot.addProperty("inviterName", inviter.getName());
        }
        events.publishInviteCreated(inviter != null ? inviter.getUniqueId() : null, snapshot);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(IslandJoinEvent event) {
        Island island = event.getIsland();
        SuperiorPlayer member = event.getPlayer();
        if (island == null || member == null) {
            return;
        }
        JsonObject snapshot = membersSnapshot(island);
        snapshot.addProperty("memberUuid", member.getUniqueId().toString());
        snapshot.addProperty("memberName", member.getName());
        events.publishMemberAdded(member.getUniqueId(), snapshot);
        islandCache.setMembership(member.getUniqueId(), island.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLeave(IslandLeaveEvent event) {
        Island island = event.getIsland();
        SuperiorPlayer member = event.getPlayer();
        if (island == null || member == null) {
            return;
        }
        JsonObject snapshot = membersSnapshot(island);
        snapshot.addProperty("memberUuid", member.getUniqueId().toString());
        snapshot.addProperty("memberName", member.getName());
        snapshot.addProperty("cause", event.getCause().name());
        events.publishMemberRemoved(member.getUniqueId(), snapshot);
        islandCache.removePlayer(member.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDisband(IslandDisbandEvent event) {
        Island island = event.getIsland();
        if (island == null) {
            return;
        }
        SuperiorPlayer actor = event.getPlayer();
        JsonObject snapshot = islandSnapshot(island, false);
        snapshot.addProperty("disbanded", true);
        events.publishIslandDisbanded(actor != null ? actor.getUniqueId() : null, snapshot);
        islandCache.removeIsland(island.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUpgrade(IslandUpgradeEvent event) {
        Island island = event.getIsland();
        if (island == null) {
            return;
        }
        SuperiorPlayer actor = event.getPlayer();
        JsonObject snapshot = islandSnapshot(island, true);
        snapshot.addProperty("upgradeName", event.getUpgradeName());
        events.publishIslandUpdated(actor != null ? actor.getUniqueId() : null, snapshot);
    }

    void shutdown() {
        HandlerList.unregisterAll(this);
    }

    private JsonObject islandSnapshot(Island island, boolean includeMembers) {
        UUID islandUuid = island.getUniqueId();
        JsonObject payload = new JsonObject();
        payload.addProperty("islandId", islandUuid.toString());
        try {
            GatewayResponse response = bridge.getIslandInfo(null, Optional.of(islandUuid.toString()), new JsonObject());
            if (response.ok()) {
                JsonObject data = response.data().deepCopy();
                if (!includeMembers) {
                    data.remove("members");
                }
                return data;
            }
        } catch (Exception ex) {
            plugin.getLogger().fine("Failed to fetch island snapshot: " + ex.getMessage());
        }
        payload.addProperty("islandName", island.getName());
        return payload;
    }

    private JsonObject membersSnapshot(Island island) {
        UUID islandUuid = island.getUniqueId();
        try {
            GatewayResponse response = bridge.listMembers(null, Optional.of(islandUuid.toString()), new JsonObject());
            if (response.ok()) {
                return response.data().deepCopy();
            }
        } catch (Exception ex) {
            plugin.getLogger().fine("Failed to fetch members snapshot: " + ex.getMessage());
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("islandId", islandUuid.toString());
        payload.addProperty("islandName", island.getName());
        return payload;
    }
}
