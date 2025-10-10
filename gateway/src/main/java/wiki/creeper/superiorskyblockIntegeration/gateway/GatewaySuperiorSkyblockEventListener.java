package wiki.creeper.superiorskyblockIntegeration.gateway;

import com.bgsoftware.superiorskyblock.api.events.IslandDisbandEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandEnterEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandInviteEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandJoinEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandKickEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandLeaveEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandQuitEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandUpgradeEvent;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.island.IslandFlag;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.google.gson.JsonObject;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;

import java.text.DecimalFormat;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import wiki.creeper.superiorskyblockIntegeration.api.PlayerMetadataService;
import wiki.creeper.superiorskyblockIntegeration.gateway.GatewayResponse;
import wiki.creeper.superiorskyblockIntegeration.gateway.bridge.SuperiorSkyblockBridge;
import wiki.creeper.superiorskyblockIntegeration.gateway.cache.PlayerIslandCache;
import wiki.creeper.superiorskyblockIntegeration.gateway.services.GatewayVelocityService;

final class GatewaySuperiorSkyblockEventListener implements Listener {

    private final JavaPlugin plugin;
    private final SuperiorSkyblockBridge bridge;
    private final GatewayEventPublisher events;
    private final PlayerIslandCache islandCache;
    private final KickReasonRegistry kickReasons;
    private final PlayerMetadataService metadataService;
    private final GatewayVelocityService velocityService;

    GatewaySuperiorSkyblockEventListener(JavaPlugin plugin,
                                         SuperiorSkyblockBridge bridge,
                                         GatewayEventPublisher events,
                                         PlayerIslandCache islandCache,
                                         KickReasonRegistry kickReasons,
                                         PlayerMetadataService metadataService,
                                         GatewayVelocityService velocityService) {
        this.plugin = plugin;
        this.bridge = bridge;
        this.events = events;
        this.islandCache = islandCache;
        this.kickReasons = kickReasons;
        this.metadataService = metadataService;
        this.velocityService = velocityService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInvite(IslandInviteEvent event) {
        Island island = event.getIsland();
        if (island == null) {
            return;
        }
        SuperiorPlayer inviter = event.getPlayer();
        SuperiorPlayer target = event.getTarget();
        if (inviter != null && target != null) {
            boolean inviterIsMember = island.isMember(inviter);
            boolean targetIsMember = island.isMember(target);
            if (!inviterIsMember && targetIsMember) {
                SuperiorPlayer actualTarget = inviter;
                inviter = target;
                target = actualTarget;
            }
        }
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
    public void onEnter(IslandEnterEvent event) {
        SuperiorPlayer superiorPlayer = event.getPlayer();
        Island island = event.getIsland();
        if (superiorPlayer == null || island == null) {
            return;
        }
        Player player = superiorPlayer.asPlayer();
        if (player == null) {
            return;
        }
        if (player.getGameMode() == GameMode.SPECTATOR || player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            return;
        }
        sendEntryActionBar(player, island);
        notifyIslandMembers(island, player, "&a&l| &f" + player.getName() + "님이 당신의 팜에 입장하였습니다.");
        if (islandHasPvpEnabled(island)) {
            player.sendMessage(color("&a&l| &f해당 팜은 &cPVP&f가 활성화 되어있습니다."));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLeave(IslandLeaveEvent event) {
        Island island = event.getIsland();
        SuperiorPlayer member = event.getPlayer();
        if (island == null || member == null) {
            return;
        }
        Player player = member.asPlayer();
        if (player != null && player.getGameMode() != GameMode.SPECTATOR && !player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            sendActionBar(player, "&a&l| &f" + islandDisplayName(island) + "에서 퇴장하였습니다.");
            notifyIslandMembers(island, player, "&a&l| &f" + player.getName() + "님이 당신의 팜에서 퇴장하였습니다.");
        }
        JsonObject snapshot = membersSnapshot(island);
        snapshot.addProperty("memberUuid", member.getUniqueId().toString());
        snapshot.addProperty("memberName", member.getName());
        snapshot.addProperty("cause", event.getCause().name());
        events.publishMemberRemoved(member.getUniqueId(), snapshot);
        islandCache.removePlayer(member.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(IslandQuitEvent event) {
        SuperiorPlayer superiorPlayer = event.getPlayer();
        if (superiorPlayer == null) {
            return;
        }
        islandCache.removePlayer(superiorPlayer.getUniqueId());
        Player player = superiorPlayer.asPlayer();
        if (player != null && velocityService != null) {
            velocityService.connectToLobby(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(IslandKickEvent event) {
        Island island = event.getIsland();
        SuperiorPlayer actor = event.getPlayer();
        SuperiorPlayer target = event.getTarget();
        if (island == null || target == null) {
            return;
        }
        UUID actorUuid = actor != null ? actor.getUniqueId() : null;
        UUID targetUuid = target.getUniqueId();
        JsonObject snapshot = islandSnapshot(island, false);
        snapshot.addProperty("memberUuid", targetUuid.toString());
        snapshot.addProperty("memberName", target.getName());
        if (actor != null) {
            snapshot.addProperty("actorUuid", actorUuid.toString());
            snapshot.addProperty("actorName", actor.getName());
        }
        kickReasons.consume(actorUuid, targetUuid).ifPresent(context -> {
            if (context.reason() != null && !context.reason().isBlank()) {
                snapshot.addProperty("reason", context.reason());
            }
        });
        if (!snapshot.has("reason")) {
            snapshot.addProperty("reason", "");
        }
        events.publishMemberKicked(actorUuid, snapshot);
        clearKickMetadata(actorUuid);
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

    private void clearKickMetadata(UUID actorUuid) {
        if (actorUuid == null || metadataService == null) {
            return;
        }
        metadataService.delete(actorUuid, "island.kick.target").exceptionally(ex -> null);
        metadataService.delete(actorUuid, "island.kick.reason").exceptionally(ex -> null);
    }

    private void sendEntryActionBar(Player player, Island island) {
        String name = islandDisplayName(island);
        double rating = island.getTotalRating();
        double rounded = Math.round(rating * 10.0D) / 10.0D;
        DecimalFormat format = new DecimalFormat("0.0");
        String star = starRating(rating);
        String message = "&a&l| &f" + name + "에 입장하였습니다. &f(팜 평가: " + star + " &7&o(" + format.format(rounded) + "&7&o점)&f)";
        sendActionBar(player, message);
    }

    private void notifyIslandMembers(Island island, Player subject, String rawMessage) {
        SuperiorPlayer owner = island.getOwner();
        if (owner == null || owner.getUniqueId().equals(subject.getUniqueId())) {
            return;
        }
        String message = color(rawMessage);
        Collection<SuperiorPlayer> members = island.getIslandMembers(true);
        for (SuperiorPlayer member : members) {
            if (member == null || member.getUniqueId().equals(subject.getUniqueId())) {
                continue;
            }
            member.runIfOnline(p -> sendActionBar(p, message));
        }
    }

    private boolean islandHasPvpEnabled(Island island) {
        IslandFlag flag = IslandFlag.getByName("PVP");
        return flag != null && island.hasSettingsEnabled(flag);
    }

    private String islandDisplayName(Island island) {
        SuperiorPlayer owner = island.getOwner();
        String ownerName = owner != null ? owner.getName() : "";
        String name = island.getName();
        if (name == null || name.isBlank()) {
            if (ownerName == null || ownerName.isBlank()) {
                return "알 수 없음";
            }
            return ownerName + "님의 팜";
        }
        return name;
    }

    private String starRating(double rating) {
        int rounded = (int) Math.round(rating);
        if (rounded <= 0) {
            return color("&f✧✧✧✧✧");
        }
        return switch (Math.min(rounded, 5)) {
            case 5 -> color("&6&l✦✦✦✦✦");
            case 4 -> color("&e&l✦✦✦✦&f✧");
            case 3 -> color("&a&l✦✦✦&f✧✧");
            case 2 -> color("&f&l✦✦&f✧✧✧");
            case 1 -> color("&7&l✦&f✧✧✧✧");
            default -> color("&f✧✧✧✧✧");
        };
    }

    private void sendActionBar(Player player, String message) {
        if (player == null || message == null || message.isBlank()) {
            return;
        }
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(color(message)));
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
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
