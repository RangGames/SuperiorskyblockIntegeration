package wiki.creeper.superiorskyblockIntegeration.gateway.bridge;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblock;
import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.enums.BorderColor;
import com.bgsoftware.superiorskyblock.api.events.IslandInviteEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandJoinEvent;
import com.bgsoftware.superiorskyblock.api.handlers.GridManager;
import com.bgsoftware.superiorskyblock.api.handlers.PlayersManager;
import com.bgsoftware.superiorskyblock.api.handlers.RolesManager;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.island.IslandPrivilege;
import com.bgsoftware.superiorskyblock.api.island.PlayerRole;
import com.bgsoftware.superiorskyblock.api.island.container.IslandsContainer;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import wiki.creeper.superiorskyblockIntegeration.common.errors.ErrorCode;
import wiki.creeper.superiorskyblockIntegeration.gateway.GatewayResponse;
import wiki.creeper.superiorskyblockIntegeration.gateway.errors.GatewayException;

final class ApiSuperiorSkyblockBridge implements SuperiorSkyblockBridge {

    private final SuperiorSkyblock superiorSkyblock;
    private final PlayersManager playersManager;
    private final GridManager gridManager;
    private final RolesManager rolesManager;

    ApiSuperiorSkyblockBridge() {
        SuperiorSkyblock instance = SuperiorSkyblockAPI.getSuperiorSkyblock();
        if (instance == null) {
            throw new IllegalStateException("SuperiorSkyblock API is not initialised yet");
        }
        this.superiorSkyblock = instance;
        this.playersManager = superiorSkyblock.getPlayers();
        this.gridManager = superiorSkyblock.getGrid();
        this.rolesManager = superiorSkyblock.getRoles();
    }

    @Override
    public boolean isAvailable() {
        return superiorSkyblock != null;
    }

    @Override
    public GatewayResponse createInvite(UUID actorUuid, String targetIdentifier, JsonObject payload) {
        SuperiorPlayer actor = requirePlayer(actorUuid, "actor");
        Island island = requireIsland(actor);

        SuperiorPlayer target = resolvePlayer(targetIdentifier);
        if (target == null) {
            throw new GatewayException(ErrorCode.TARGET_OFFLINE, "Target player not found");
        }

        if (actor.equals(target)) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "Cannot invite yourself");
        }

        IslandPrivilege invitePrivilege = IslandPrivilege.getByName("INVITE_MEMBER");
        if (invitePrivilege != null && !actor.hasPermission(invitePrivilege)) {
            throw new GatewayException(ErrorCode.FORBIDDEN, "Actor lacks invite privilege");
        }

        if (island.isMember(target)) {
            throw new GatewayException(ErrorCode.ALREADY_MEMBER, "Target already a member");
        }
        if (island.isInvited(target)) {
            throw new GatewayException(ErrorCode.INVITE_ALREADY_EXISTS, "Invite already pending");
        }
        if (target.getIsland() != null) {
            throw new GatewayException(ErrorCode.ALREADY_MEMBER, "Target already belongs to an island");
        }
        if (island.isBanned(target)) {
            throw new GatewayException(ErrorCode.FORBIDDEN, "Target is banned from this island");
        }

        int teamLimit = island.getTeamLimit();
        List<SuperiorPlayer> members = members(island);
        if (teamLimit > 0 && members.size() >= teamLimit) {
            throw new GatewayException(ErrorCode.MEMBER_LIMIT_REACHED, "Island member limit reached");
        }

        IslandInviteEvent inviteEvent = new IslandInviteEvent(actor, target, island);
        Bukkit.getPluginManager().callEvent(inviteEvent);
        if (inviteEvent.isCancelled()) {
            throw new GatewayException(ErrorCode.CONFLICT, "Island invite was cancelled by another plugin");
        }

        island.inviteMember(target);

        JsonObject data = new JsonObject();
        UUID islandUuid = island.getUniqueId();
        data.addProperty("inviteId", islandUuid.toString());
        data.addProperty("islandId", islandUuid.toString());
        data.addProperty("islandName", island.getName());
        data.addProperty("inviterUuid", actor.getUniqueId().toString());
        data.addProperty("inviterName", actor.getName());
        data.addProperty("targetUuid", target.getUniqueId().toString());
        data.addProperty("targetName", target.getName());
        data.addProperty("expiresAt", System.currentTimeMillis() + DEFAULT_INVITE_TTL_MILLIS);
        data.addProperty("membersCount", members.size());
        data.addProperty("membersLimit", teamLimit);
        return GatewayResponse.ok(data);
    }

    @Override
    public GatewayResponse acceptInvite(UUID actorUuid, String inviteId, JsonObject payload) {
        SuperiorPlayer actor = requirePlayer(actorUuid, "actor");
        Island island = resolveInviteIsland(actor, inviteId);
        if (island == null || !island.isInvited(actor)) {
            throw new GatewayException(ErrorCode.INVITE_NOT_FOUND, "Invite not present");
        }

        Island currentIsland = actor.getIsland();
        if (currentIsland != null) {
            if (currentIsland.getUniqueId().equals(island.getUniqueId())) {
                throw new GatewayException(ErrorCode.ALREADY_MEMBER, "Already a member of this island");
            }
            throw new GatewayException(ErrorCode.CONFLICT, "Leave current island before accepting invite");
        }

        int teamLimit = island.getTeamLimit();
        List<SuperiorPlayer> members = members(island);
        if (teamLimit > 0 && members.size() >= teamLimit) {
            throw new GatewayException(ErrorCode.MEMBER_LIMIT_REACHED, "Island member limit reached");
        }

        PlayerRole defaultRole = rolesManager.getDefaultRole();
        if (defaultRole == null) {
            defaultRole = actor.getPlayerRole();
        }

        IslandJoinEvent joinEvent = new IslandJoinEvent(actor, island, IslandJoinEvent.Cause.INVITE);
        Bukkit.getPluginManager().callEvent(joinEvent);
        if (joinEvent.isCancelled()) {
            throw new GatewayException(ErrorCode.CONFLICT, "Island join was cancelled by another plugin");
        }

        island.addMember(actor, defaultRole);
        island.revokeInvite(actor);

        List<SuperiorPlayer> updatedMembers = members(island);
        JsonObject data = new JsonObject();
        UUID islandUuid = island.getUniqueId();
        data.addProperty("islandId", islandUuid.toString());
        data.addProperty("islandName", island.getName());
        data.addProperty("role", defaultRole != null ? defaultRole.getName() : "UNKNOWN");
        data.addProperty("memberUuid", actorUuid.toString());
        data.addProperty("memberName", actor.getName());
        SuperiorPlayer inviter = island.getOwner();
        if (inviter != null) {
            data.addProperty("inviterUuid", inviter.getUniqueId().toString());
            data.addProperty("inviterName", inviter.getName());
        }
        data.addProperty("membersCount", updatedMembers.size());
        data.addProperty("membersLimit", teamLimit);
        data.add("members", membersToJsonArray(updatedMembers));
        return GatewayResponse.ok(data);
    }

    @Override
    public GatewayResponse denyInvite(UUID actorUuid, String inviteId, JsonObject payload) {
        SuperiorPlayer actor = requirePlayer(actorUuid, "actor");
        Island island = resolveInviteIsland(actor, inviteId);
        if (island == null || !island.isInvited(actor)) {
            throw new GatewayException(ErrorCode.INVITE_NOT_FOUND, "Invite not present");
        }
        island.revokeInvite(actor);
        JsonObject data = new JsonObject();
        UUID islandUuid = island.getUniqueId();
        data.addProperty("inviteId", islandUuid.toString());
        data.addProperty("islandId", islandUuid.toString());
        data.addProperty("islandName", island.getName());
        data.addProperty("memberUuid", actorUuid.toString());
        data.addProperty("memberName", actor.getName());
        SuperiorPlayer owner = island.getOwner();
        if (owner != null) {
            data.addProperty("inviterUuid", owner.getUniqueId().toString());
            data.addProperty("inviterName", owner.getName());
        }
        data.addProperty("ok", true);
        return GatewayResponse.ok(data);
    }

    @Override
    public GatewayResponse listPendingInvites(UUID actorUuid, JsonObject payload) {
        SuperiorPlayer actor = requirePlayer(actorUuid, "actor");
        List<Island> invites = actor.getInvites();
        JsonObject data = new JsonObject();
        JsonArray array = new JsonArray();
        for (Island invitedIsland : invites) {
            JsonObject json = new JsonObject();
            UUID islandUuid = invitedIsland.getUniqueId();
            json.addProperty("islandId", islandUuid.toString());
            String name = invitedIsland.getName();
            if (name != null) {
                json.addProperty("islandName", name);
            }
            SuperiorPlayer owner = invitedIsland.getOwner();
            if (owner != null) {
                json.addProperty("ownerUuid", owner.getUniqueId().toString());
                json.addProperty("ownerName", owner.getName());
            }
            json.addProperty("membersCount", invitedIsland.getIslandMembers(true).size());
            json.addProperty("membersLimit", invitedIsland.getTeamLimit());
            json.addProperty("level", invitedIsland.getIslandLevel() != null
                    ? invitedIsland.getIslandLevel().doubleValue()
                    : 0.0D);
            array.add(json);
        }
        data.add("invites", array);
        data.addProperty("count", invites.size());
        return GatewayResponse.ok(data);
    }

    @Override
    public GatewayResponse getIslandInfo(UUID requester, Optional<String> ownerIdentifier, JsonObject payload) {
        Island island = ownerIdentifier
                .map(this::resolveIslandByIdentifier)
                .orElseGet(() -> requester != null ? islandOf(requester) : null);
        if (island == null) {
            throw new GatewayException(ErrorCode.ISLAND_NOT_FOUND, "Island not found");
        }
        JsonObject data = islandSummary(island, true);
        return GatewayResponse.ok(data);
    }

    @Override
    public GatewayResponse listMembers(UUID requester, Optional<String> islandIdentifier, JsonObject payload) {
        Island island = islandIdentifier
                .map(this::resolveIslandByIdentifier)
                .orElseGet(() -> requester != null ? islandOf(requester) : null);
        if (island == null) {
            throw new GatewayException(ErrorCode.ISLAND_NOT_FOUND, "Island not found");
        }
        List<SuperiorPlayer> members = members(island);
        JsonObject data = new JsonObject();
        data.addProperty("islandId", island.getUniqueId().toString());
        data.add("members", membersToJsonArray(members));
        data.addProperty("membersCount", members.size());
        data.addProperty("membersLimit", island.getTeamLimit());
        return GatewayResponse.ok(data);
    }

    @Override
    public Optional<UUID> islandIdForPlayer(UUID playerUuid) {
        SuperiorPlayer player = playersManager.getSuperiorPlayer(playerUuid);
        if (player == null) {
            return Optional.empty();
        }
        Island island = player.getIsland();
        return island != null ? Optional.of(island.getUniqueId()) : Optional.empty();
    }

    @Override
    public Map<UUID, UUID> snapshotPlayerIslands() {
        Map<UUID, UUID> snapshot = new HashMap<>();
        for (Island island : allIslands()) {
            UUID islandUuid = island.getUniqueId();
            for (SuperiorPlayer member : island.getIslandMembers(true)) {
                if (member != null) {
                    snapshot.put(member.getUniqueId(), islandUuid);
                }
            }
        }
        return snapshot;
    }

    @Override
    public boolean canManageIslandQuests(UUID playerUuid) {
        SuperiorPlayer player = playersManager.getSuperiorPlayer(playerUuid);
        if (player == null) {
            return false;
        }
        Island island = player.getIsland();
        if (island == null) {
            return false;
        }
        SuperiorPlayer owner = island.getOwner();
        if (owner != null && owner.getUniqueId().equals(playerUuid)) {
            return true;
        }
        PlayerRole ownerRole = owner != null ? owner.getPlayerRole() : null;
        PlayerRole role = player.getPlayerRole();
        if (role == null || ownerRole == null) {
            return false;
        }
        PlayerRole deputy = ownerRole.getPreviousRole();
        return deputy != null && role.equals(deputy);
    }

    @Override
    public int memberCount(UUID islandUuid) {
        if (islandUuid == null) {
            return 0;
        }
        Island island = gridManager.getIslandByUUID(islandUuid);
        if (island == null) {
            return 0;
        }
        return members(island).size();
    }

    @Override
    public IslandDetails describeIsland(UUID islandUuid) {
        if (islandUuid == null) {
            return null;
        }
        Island island = gridManager.getIslandByUUID(islandUuid);
        if (island == null) {
            return null;
        }
        SuperiorPlayer owner = island.getOwner();
        UUID ownerUuid = owner != null ? owner.getUniqueId() : null;
        String ownerName = owner != null ? owner.getName() : null;
        return new IslandDetails(island.getUniqueId(), island.getName(), ownerUuid, ownerName);
    }

    @Override
    public Optional<String> lookupPlayerName(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return Optional.empty();
        }
        try {
            UUID parsed = UUID.fromString(uuid);
            SuperiorPlayer player = playersManager.getSuperiorPlayer(parsed);
            if (player != null && player.getName() != null) {
                return Optional.of(player.getName());
            }
            return Optional.ofNullable(Bukkit.getOfflinePlayer(parsed).getName());
        } catch (IllegalArgumentException ex) {
            SuperiorPlayer player = playersManager.getSuperiorPlayer(uuid);
            if (player != null && player.getName() != null) {
                return Optional.of(player.getName());
            }
            return Optional.ofNullable(Bukkit.getOfflinePlayer(uuid).getName());
        }
    }

    @Override
    public void broadcastIslandMessage(UUID islandUuid, java.util.List<String> messages) {
        if (islandUuid == null || messages == null || messages.isEmpty()) {
            return;
        }
        Island island = gridManager.getIslandByUUID(islandUuid);
        if (island == null) {
            return;
        }
        java.util.List<String> resolved = new java.util.ArrayList<>(messages.size());
        for (String message : messages) {
            if (message == null) {
                continue;
            }
            resolved.add(ChatColor.translateAlternateColorCodes('&', message));
        }
        if (resolved.isEmpty()) {
            return;
        }
        for (SuperiorPlayer member : island.getIslandMembers(true)) {
            if (member == null) {
                continue;
            }
            member.runIfOnline(player -> {
                for (String line : resolved) {
                    player.sendMessage(line);
                }
            });
        }
    }

    @Override
    public GatewayResponse disbandIsland(UUID actorUuid) {
        SuperiorPlayer actor = requirePlayer(actorUuid, "actor");
        Island island = requireIsland(actor);
        SuperiorPlayer owner = island.getOwner();
        if (owner == null || !owner.getUniqueId().equals(actorUuid)) {
            throw new GatewayException(ErrorCode.FORBIDDEN, "Only the island owner can disband the island");
        }
        UUID islandId = island.getUniqueId();
        island.disbandIsland();
        JsonObject data = new JsonObject();
        data.addProperty("islandId", islandId.toString());
        return GatewayResponse.ok(data);
    }

    @Override
    public GatewayResponse toggleWorldBorder(UUID playerUuid) {
        SuperiorPlayer player = requirePlayer(playerUuid, "player");
        player.toggleWorldBorder();
        refreshWorldBorder(player);
        return GatewayResponse.ok(borderState(player));
    }

    @Override
    public GatewayResponse setBorderColor(UUID playerUuid, String color) {
        SuperiorPlayer player = requirePlayer(playerUuid, "player");
        BorderColor borderColor = parseBorderColor(color);
        player.setBorderColor(borderColor);
        refreshWorldBorder(player);
        return GatewayResponse.ok(borderState(player));
    }

    @Override
    public GatewayResponse borderState(UUID playerUuid) {
        SuperiorPlayer player = requirePlayer(playerUuid, "player");
        return GatewayResponse.ok(borderState(player));
    }

    private JsonObject borderState(SuperiorPlayer player) {
        JsonObject data = new JsonObject();
        data.addProperty("enabled", player.hasWorldBorderEnabled());
        BorderColor color = player.getBorderColor();
        data.addProperty("color", color != null ? color.name() : BorderColor.GREEN.name());
        return data;
    }

    private void refreshWorldBorder(SuperiorPlayer player) {
        Island island = player.getIsland();
        if (island != null) {
            player.updateWorldBorder(island);
        }
    }

    private BorderColor parseBorderColor(String color) {
        if (color == null || color.isBlank()) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "color is required");
        }
        try {
            return BorderColor.valueOf(color.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "Unknown border color: " + color, false, ex);
        }
    }

    private SuperiorPlayer requirePlayer(UUID uuid, String label) {
        if (uuid == null) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, label + " uuid missing");
        }
        SuperiorPlayer player = playersManager.getSuperiorPlayer(uuid);
        if (player == null) {
            throw new GatewayException(ErrorCode.NOT_FOUND, "Player not tracked: " + uuid);
        }
        return player;
    }

    private SuperiorPlayer resolvePlayer(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return null;
        }
        Optional<UUID> parsed = parseUuid(identifier);
        if (parsed.isPresent()) {
            SuperiorPlayer byUuid = playersManager.getSuperiorPlayer(parsed.get());
            if (byUuid != null) {
                return byUuid;
            }
        }
        return playersManager.getSuperiorPlayer(identifier);
    }

    private Island requireIsland(SuperiorPlayer player) {
        Island island = player.getIsland();
        if (island == null) {
            throw new GatewayException(ErrorCode.ISLAND_NOT_FOUND, "Player has no island");
        }
        return island;
    }

    private Island islandOf(UUID playerUuid) {
        SuperiorPlayer player = playersManager.getSuperiorPlayer(playerUuid);
        return player != null ? player.getIsland() : null;
    }

    private Island resolveIslandByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return null;
        }
        Optional<UUID> parsed = parseUuid(identifier);
        if (parsed.isPresent()) {
            Island island = superiorSkyblock.getGrid().getIslandByUUID(parsed.get());
            if (island != null) {
                return island;
            }
        }
        SuperiorPlayer owner = playersManager.getSuperiorPlayer(identifier);
        return owner != null ? owner.getIsland() : null;
    }

    private Island resolveInviteIsland(SuperiorPlayer player, String inviteId) {
        if (inviteId != null && !inviteId.isBlank()) {
            Optional<UUID> parsed = parseUuid(inviteId);
            if (parsed.isPresent()) {
                Island island = superiorSkyblock.getGrid().getIslandByUUID(parsed.get());
                if (island != null && island.isInvited(player)) {
                    return island;
                }
            }
        }
        return allIslands().stream()
                .filter(island -> island.isInvited(player))
                .findFirst()
                .orElse(null);
    }

    private Collection<Island> allIslands() {
        IslandsContainer container = gridManager.getIslandsContainer();
        return container.getIslandsUnsorted();
    }

    private List<SuperiorPlayer> members(Island island) {
        return island.getIslandMembers(true);
    }

    private JsonObject islandSummary(Island island, boolean includeMembers) {
        JsonObject data = new JsonObject();
        UUID islandUuid = island.getUniqueId();
        data.addProperty("islandId", islandUuid.toString());
        String name = island.getName();
        if (name != null) {
            data.addProperty("islandName", name);
        }
        SuperiorPlayer owner = island.getOwner();
        if (owner != null) {
            data.addProperty("ownerUuid", owner.getUniqueId().toString());
            data.addProperty("ownerName", owner.getName());
        }
        BigDecimal level = island.getIslandLevel();
        data.addProperty("level", level != null ? level.doubleValue() : 0.0D);
        List<SuperiorPlayer> members = members(island);
        data.addProperty("membersCount", members.size());
        data.addProperty("membersLimit", island.getTeamLimit());
        if (includeMembers) {
            data.add("members", membersToJsonArray(members));
        }
        return data;
    }

    private JsonArray membersToJsonArray(List<SuperiorPlayer> members) {
        JsonArray array = new JsonArray();
        for (SuperiorPlayer member : members) {
            if (member == null) {
                continue;
            }
            JsonObject json = new JsonObject();
            json.addProperty("uuid", member.getUniqueId().toString());
            json.addProperty("name", member.getName());
            PlayerRole role = member.getPlayerRole();
            json.addProperty("role", role != null ? role.getName() : "UNKNOWN");
            array.add(json);
        }
        return array;
    }

    private Optional<UUID> parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static final long DEFAULT_INVITE_TTL_MILLIS = 10L * 60L * 1000L;
}
