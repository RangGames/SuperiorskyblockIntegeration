package wiki.creeper.superiorskyblockIntegeration.gateway.bridge;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblock;
import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.enums.BankAction;
import com.bgsoftware.superiorskyblock.api.enums.BorderColor;
import com.bgsoftware.superiorskyblock.api.enums.Rating;
import com.bgsoftware.superiorskyblock.api.events.IslandInviteEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandJoinEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandKickEvent;
import com.bgsoftware.superiorskyblock.api.handlers.GridManager;
import com.bgsoftware.superiorskyblock.api.handlers.PlayersManager;
import com.bgsoftware.superiorskyblock.api.handlers.RolesManager;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.island.IslandPrivilege;
import com.bgsoftware.superiorskyblock.api.island.PlayerRole;
import com.bgsoftware.superiorskyblock.api.island.container.IslandsContainer;
import com.bgsoftware.superiorskyblock.api.island.bank.BankTransaction;
import com.bgsoftware.superiorskyblock.api.island.bank.IslandBank;
import com.bgsoftware.superiorskyblock.api.island.warps.IslandWarp;
import com.bgsoftware.superiorskyblock.api.key.Key;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import wiki.creeper.superiorskyblockIntegeration.common.errors.ErrorCode;
import wiki.creeper.superiorskyblockIntegeration.gateway.GatewayResponse;
import wiki.creeper.superiorskyblockIntegeration.gateway.errors.GatewayException;
import wiki.creeper.superiorskyblockIntegeration.api.NetworkSkyblockAPI;
import wiki.creeper.superiorskyblockIntegeration.api.PlayerMetadataService;

final class ApiSuperiorSkyblockBridge implements SuperiorSkyblockBridge {

    private final SuperiorSkyblock superiorSkyblock;
    private final PlayersManager playersManager;
    private final GridManager gridManager;
    private final RolesManager rolesManager;

    private static final String[] ROLE_PERMISSION_PRIVILEGES = {
            "MANAGE_ROLE_SETTINGS",
            "MANAGE_ROLES",
            "MANAGE_PERMISSIONS",
            "ROLE_SETTINGS"
    };

    private static final Map<String, String> PRIVILEGE_ALIASES = Map.ofEntries(
            Map.entry("MANAGE_ROLE_SETTINGS", "ROLE_SETTINGS"),
            Map.entry("MANAGE_ROLE_SETTINGS_MENU", "ROLE_SETTINGS"),
            Map.entry("ROLE_SETTINGS_MENU", "ROLE_SETTINGS")
    );

    private static final int HOME_WARP_LIMIT = 2;

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
    public GatewayResponse listCoopPlayers(UUID actorUuid) {
        SuperiorPlayer actor = requirePlayer(actorUuid, "actor");
        Island island = requireIsland(actor);
        List<SuperiorPlayer> coops = Optional.ofNullable(island.getCoopPlayers()).orElse(Collections.emptyList());
        JsonObject data = new JsonObject();
        data.addProperty("islandId", island.getUniqueId().toString());
        data.addProperty("limit", island.getCoopLimit());
        data.addProperty("size", coops.size());
        data.add("players", membersToJsonArray(coops));
        return GatewayResponse.ok(data);
    }

    @Override
    public GatewayResponse addCoopPlayer(UUID actorUuid, String targetIdentifier) {
        SuperiorPlayer actor = requirePlayer(actorUuid, "actor");
        Island island = requireIsland(actor);
        SuperiorPlayer target = resolvePlayer(targetIdentifier);
        if (target == null) {
            throw new GatewayException(ErrorCode.NOT_FOUND, "Target player not found");
        }
        if (actorUuid.equals(target.getUniqueId())) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "Cannot coop yourself");
        }
        if (target.asPlayer() == null) {
            throw new GatewayException(ErrorCode.CONFLICT, "Target must be online to become a coop member");
        }
        if (island.isMember(target)) {
            throw new GatewayException(ErrorCode.CONFLICT, "Target is already a member of the island");
        }
        if (island.isCoop(target)) {
            throw new GatewayException(ErrorCode.CONFLICT, "Target is already a coop member");
        }
        List<SuperiorPlayer> coops = Optional.ofNullable(island.getCoopPlayers()).orElse(Collections.emptyList());
        if (coops.size() >= island.getCoopLimit()) {
            throw new GatewayException(ErrorCode.CONFLICT, "Coop limit reached");
        }
        IslandPrivilege privilege = IslandPrivilege.getByName("COOP_MEMBER");
        if (privilege != null && !actor.hasPermission(privilege)) {
            throw new GatewayException(ErrorCode.FORBIDDEN, "Actor lacks coop privilege");
        }
        island.addCoop(target);

        broadcastIslandMessage(island.getUniqueId(), List.of(
                "&6[Skyblock] &f" + actor.getName() + "님이 " + target.getName() + "님을 알바로 고용했습니다."));
        target.runIfOnline(p -> p.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&6[Skyblock] &f" + actor.getName() + "님의 팜 알바로 고용되었습니다. /팜 알바 명령어로 확인하세요.")));

        JsonObject data = new JsonObject();
        data.add("player", playerSummary(target));
        return GatewayResponse.ok(data);
    }

    @Override
    public GatewayResponse removeCoopPlayer(UUID actorUuid, String targetIdentifier) {
        SuperiorPlayer actor = requirePlayer(actorUuid, "actor");
        Island island = requireIsland(actor);
        SuperiorPlayer target = resolvePlayer(targetIdentifier);
        if (target == null) {
            throw new GatewayException(ErrorCode.NOT_FOUND, "Target player not found");
        }
        if (!island.isCoop(target)) {
            throw new GatewayException(ErrorCode.NOT_FOUND, "Target is not a coop member");
        }
        IslandPrivilege privilege = IslandPrivilege.getByName("UNCOOP_MEMBER");
        if (privilege != null && !actor.hasPermission(privilege)) {
            throw new GatewayException(ErrorCode.FORBIDDEN, "Actor lacks uncoop privilege");
        }
        island.removeCoop(target);

        broadcastIslandMessage(island.getUniqueId(), List.of(
                "&6[Skyblock] &f" + actor.getName() + "님이 " + target.getName() + "님을 알바에서 해고했습니다."));
        target.runIfOnline(p -> p.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&6[Skyblock] &f" + actor.getName() + "님의 팜 알바에서 해고되었습니다.")));

        JsonObject data = new JsonObject();
        data.add("player", playerSummary(target));
        return GatewayResponse.ok(data);
    }

    @Override
    public GatewayResponse listBannedPlayers(UUID actorUuid) {
        SuperiorPlayer actor = requirePlayer(actorUuid, "actor");
        Island island = requireIsland(actor);
        List<SuperiorPlayer> banned = Optional.ofNullable(island.getBannedPlayers()).orElse(Collections.emptyList());
        JsonObject data = new JsonObject();
        data.addProperty("islandId", island.getUniqueId().toString());
        data.addProperty("size", banned.size());
        data.add("players", membersToJsonArray(banned));
        return GatewayResponse.ok(data);
    }

    @Override
    public GatewayResponse addBannedPlayer(UUID actorUuid, String targetIdentifier) {
        SuperiorPlayer actor = requirePlayer(actorUuid, "actor");
        Island island = requireIsland(actor);
        SuperiorPlayer target = resolvePlayer(targetIdentifier);
        if (target == null) {
            throw new GatewayException(ErrorCode.NOT_FOUND, "Target player not found");
        }
        if (actorUuid.equals(target.getUniqueId())) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "Cannot ban yourself");
        }
        if (island.isMember(target) || island.isCoop(target)) {
            throw new GatewayException(ErrorCode.CONFLICT, "Cannot ban an island member or coop");
        }
        IslandPrivilege privilege = IslandPrivilege.getByName("BAN_MEMBER");
        if (privilege != null && !actor.hasPermission(privilege)) {
            throw new GatewayException(ErrorCode.FORBIDDEN, "Actor lacks ban privilege");
        }
        if (island.isBanned(target)) {
            throw new GatewayException(ErrorCode.CONFLICT, "Target is already banned");
        }
        island.banMember(target, actor);

        broadcastIslandMessage(island.getUniqueId(), List.of(
                "&6[Skyblock] &f" + actor.getName() + "님이 " + target.getName() + "님을 팜에서 차단했습니다."));

        JsonObject data = new JsonObject();
        data.add("player", playerSummary(target));
        return GatewayResponse.ok(data);
    }

    @Override
    public GatewayResponse removeBannedPlayer(UUID actorUuid, String targetIdentifier) {
        SuperiorPlayer actor = requirePlayer(actorUuid, "actor");
        Island island = requireIsland(actor);
        SuperiorPlayer target = resolvePlayer(targetIdentifier);
        if (target == null) {
            throw new GatewayException(ErrorCode.NOT_FOUND, "Target player not found");
        }
        if (!island.isBanned(target)) {
            throw new GatewayException(ErrorCode.NOT_FOUND, "Target is not banned");
        }
        IslandPrivilege privilege = IslandPrivilege.getByName("UNBAN_MEMBER");
        if (privilege != null && !actor.hasPermission(privilege)) {
            throw new GatewayException(ErrorCode.FORBIDDEN, "Actor lacks unban privilege");
        }
        island.unbanMember(target);

        broadcastIslandMessage(island.getUniqueId(), List.of(
                "&6[Skyblock] &f" + actor.getName() + "님이 " + target.getName() + "님을 팜 차단 목록에서 해제했습니다."));

        JsonObject data = new JsonObject();
        data.add("player", playerSummary(target));
        return GatewayResponse.ok(data);
    }

    @Override
    public GatewayResponse kickMember(UUID actorUuid, UUID targetUuid, String reason, JsonObject payload) {
        SuperiorPlayer actor = requirePlayer(actorUuid, "actor");
        SuperiorPlayer target = requirePlayer(targetUuid, "target");
        if (actorUuid.equals(targetUuid)) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "Cannot kick yourself");
        }

        Island island = requireIsland(actor);
        Island targetIsland = target.getIsland();
        if (targetIsland == null || !targetIsland.getUniqueId().equals(island.getUniqueId())) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "Target does not belong to the same island");
        }

        SuperiorPlayer owner = island.getOwner();
        if (owner != null && owner.getUniqueId().equals(targetUuid)) {
            throw new GatewayException(ErrorCode.FORBIDDEN, "Cannot kick the island owner");
        }

        IslandPrivilege privilege = IslandPrivilege.getByName("KICK_MEMBER");
        if (privilege != null && !actor.hasPermission(privilege)) {
            throw new GatewayException(ErrorCode.FORBIDDEN, "Actor lacks kick privilege");
        }

        PlayerRole actorRole = actor.getPlayerRole();
        PlayerRole targetRole = target.getPlayerRole();
        if (actorRole != null && targetRole != null && targetRole.isHigherThan(actorRole)) {
            throw new GatewayException(ErrorCode.FORBIDDEN, "Cannot kick a higher-ranked member");
        }

        IslandKickEvent kickEvent = new IslandKickEvent(actor, target, island);
        Bukkit.getPluginManager().callEvent(kickEvent);
        if (kickEvent.isCancelled()) {
            throw new GatewayException(ErrorCode.CONFLICT, "Island kick cancelled by another plugin");
        }

        island.kickMember(target);

        JsonObject data = new JsonObject();
        data.addProperty("islandId", island.getUniqueId().toString());
        if (island.getName() != null) {
            data.addProperty("islandName", island.getName());
        }
        data.addProperty("actorUuid", actorUuid.toString());
        data.addProperty("actorName", actor.getName());
        data.addProperty("targetUuid", targetUuid.toString());
        data.addProperty("targetName", target.getName());
        if (reason != null && !reason.isBlank()) {
            data.addProperty("reason", reason);
        }
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
    public GatewayResponse bankState(UUID actorUuid) {
        SuperiorPlayer actor = requirePlayer(actorUuid, "actor");
        Island island = requireIsland(actor);
        IslandBank bank = requireBank(island);

        JsonObject data = new JsonObject();
        data.addProperty("islandId", island.getUniqueId().toString());
        data.addProperty("islandName", island.getName());
        data.addProperty("balance", sanitize(bank.getBalance()).toPlainString());
        BigDecimal limit = island.getBankLimit();
        if (limit != null) {
            data.addProperty("limit", sanitize(limit).toPlainString());
        }
        return GatewayResponse.ok(data);
    }

    @Override
    public GatewayResponse bankDeposit(UUID actorUuid, BigDecimal amount) {
        SuperiorPlayer actor = requirePlayer(actorUuid, "actor");
        Island island = requireIsland(actor);
        IslandBank bank = requireBank(island);

        BigDecimal positive = sanitize(amount);
        if (positive.signum() <= 0) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "amount must be positive");
        }
        if (!bank.canDepositMoney(positive)) {
            throw new GatewayException(ErrorCode.CONFLICT, "Deposit exceeds bank limit");
        }
        var transaction = bank.depositMoney(actor, positive);
        if (transaction == null || transaction.getAction() != BankAction.DEPOSIT_COMPLETED) {
            String reason = transaction != null ? transaction.getFailureReason() : null;
            throw new GatewayException(ErrorCode.CONFLICT,
                    reason != null && !reason.isBlank() ? reason : "Deposit failed");
        }

        JsonObject data = new JsonObject();
        data.addProperty("islandId", island.getUniqueId().toString());
        data.addProperty("islandName", island.getName());
        data.addProperty("balance", sanitize(bank.getBalance()).toPlainString());
        data.addProperty("amount", positive.toPlainString());
        data.addProperty("action", transaction.getAction().name());
        return GatewayResponse.ok(data);
    }

    @Override
    public GatewayResponse bankWithdraw(UUID actorUuid, BigDecimal amount) {
        SuperiorPlayer actor = requirePlayer(actorUuid, "actor");
        Island island = requireIsland(actor);
        IslandBank bank = requireBank(island);

        BigDecimal positive = sanitize(amount);
        if (positive.signum() <= 0) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "amount must be positive");
        }
        var transaction = bank.withdrawMoney(actor, positive, Collections.singletonList("Network withdraw"));
        if (transaction == null || transaction.getAction() != BankAction.WITHDRAW_COMPLETED) {
            String reason = transaction != null ? transaction.getFailureReason() : null;
            throw new GatewayException(ErrorCode.CONFLICT,
                    reason != null && !reason.isBlank() ? reason : "Withdraw failed");
        }

        JsonObject data = new JsonObject();
        data.addProperty("islandId", island.getUniqueId().toString());
        data.addProperty("islandName", island.getName());
        data.addProperty("balance", sanitize(bank.getBalance()).toPlainString());
        data.addProperty("amount", positive.toPlainString());
        data.addProperty("action", transaction.getAction().name());
        return GatewayResponse.ok(data);
    }

    @Override
    public GatewayResponse bankHistory(UUID actorUuid, int page, int pageSize) {
        SuperiorPlayer actor = requirePlayer(actorUuid, "actor");
        Island island = requireIsland(actor);
        IslandBank bank = requireBank(island);

        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, pageSize);

        List<BankTransaction> transactions = new ArrayList<>(bank.getAllTransactions());
        transactions.sort(Comparator.comparingInt(BankTransaction::getPosition));
        Collections.reverse(transactions);

        int total = transactions.size();
        int fromIndex = Math.min((safePage - 1) * safeSize, total);
        int toIndex = Math.min(fromIndex + safeSize, total);
        List<BankTransaction> slice = transactions.subList(fromIndex, toIndex);

        JsonArray array = new JsonArray();
        for (BankTransaction transaction : slice) {
            JsonObject entry = new JsonObject();
            UUID playerUuid = transaction.getPlayer();
            if (playerUuid != null) {
                entry.addProperty("playerUuid", playerUuid.toString());
                String name = null;
                SuperiorPlayer target = playersManager.getSuperiorPlayer(playerUuid);
                if (target != null) {
                    name = target.getName();
                }
                if (name == null) {
                    name = Optional.ofNullable(Bukkit.getOfflinePlayer(playerUuid).getName()).orElse(null);
                }
                if (name != null && !name.isBlank()) {
                    entry.addProperty("playerName", name);
                }
            } else {
                entry.addProperty("playerName", "팜 이자");
            }
            entry.addProperty("action", transaction.getAction().name());
            entry.addProperty("amount", sanitize(transaction.getAmount()).toPlainString());
            entry.addProperty("time", transaction.getTime());
            entry.addProperty("position", transaction.getPosition());
            String failure = transaction.getFailureReason();
            if (failure != null && !failure.isBlank()) {
                entry.addProperty("failureReason", failure);
            }
            array.add(entry);
        }

        JsonObject data = new JsonObject();
        data.addProperty("islandId", island.getUniqueId().toString());
        data.addProperty("islandName", island.getName());
        data.addProperty("page", safePage);
        data.addProperty("pageSize", safeSize);
        data.addProperty("total", total);
        data.add("transactions", array);
        return GatewayResponse.ok(data);
    }

    @Override
    public GatewayResponse hopperState(UUID actorUuid) {
        SuperiorPlayer actor = requirePlayer(actorUuid, "actor");
        Island island = requireIsland(actor);

        Key hopperKey = Key.of(Material.HOPPER);
        BigInteger current = island.getBlockCountAsBigInteger(hopperKey);
        int limit = island.getBlockLimit(hopperKey);

        JsonObject data = new JsonObject();
        data.addProperty("islandId", island.getUniqueId().toString());
        if (island.getName() != null) {
            data.addProperty("islandName", island.getName());
        }
        SuperiorPlayer owner = island.getOwner();
        if (owner != null) {
            data.addProperty("ownerUuid", owner.getUniqueId().toString());
            data.addProperty("ownerName", owner.getName());
        }
        data.addProperty("current", current != null ? current.toString() : "0");
        data.addProperty("limit", limit);
        return GatewayResponse.ok(data);
    }

    @Override
    public GatewayResponse updateIslandRating(UUID actorUuid, int ratingValue) {
        SuperiorPlayer actor = requirePlayer(actorUuid, "actor");
        Player player = actor.asPlayer();
        if (player == null) {
            throw new GatewayException(ErrorCode.TARGET_OFFLINE, "Player must be online to rate islands");
        }
        Island targetIsland = SuperiorSkyblockAPI.getIslandAt(player.getLocation());
        if (targetIsland == null) {
            throw new GatewayException(ErrorCode.ISLAND_NOT_FOUND, "You are not standing on an island");
        }
        Island ownIsland = actor.getIsland();
        if (ownIsland != null && ownIsland.getUniqueId().equals(targetIsland.getUniqueId())) {
            throw new GatewayException(ErrorCode.CONFLICT, "Cannot rate your own island");
        }

        Rating previous = targetIsland.getRating(actor);
        Rating applied;
        if (ratingValue <= 0) {
            applied = Rating.UNKNOWN;
            targetIsland.setRating(actor, Rating.UNKNOWN);
        } else {
            if (ratingValue > 5) {
                throw new GatewayException(ErrorCode.BAD_REQUEST, "rating must be between 0 and 5");
            }
            applied = Rating.valueOf(ratingValue);
            targetIsland.setRating(actor, applied);
        }

        JsonObject data = new JsonObject();
        data.addProperty("islandId", targetIsland.getUniqueId().toString());
        if (targetIsland.getName() != null) {
            data.addProperty("islandName", targetIsland.getName());
        }
        SuperiorPlayer owner = targetIsland.getOwner();
        if (owner != null) {
            data.addProperty("ownerUuid", owner.getUniqueId().toString());
            data.addProperty("ownerName", owner.getName());
        }
        data.addProperty("rating", applied.getValue());
        data.addProperty("previousRating", previous != null ? previous.getValue() : Rating.UNKNOWN.getValue());
        return GatewayResponse.ok(data);
    }

    @Override
    public GatewayResponse listHomeWarps(UUID actorUuid) {
        SuperiorPlayer actor = requirePlayer(actorUuid, "actor");
        Island island = requireIsland(actor);
        JsonObject data = new JsonObject();
        data.addProperty("islandId", island.getUniqueId().toString());
        if (island.getName() != null) {
            data.addProperty("islandName", island.getName());
        }
        data.addProperty("maxWarps", HOME_WARP_LIMIT);
        data.addProperty("warpCount", island.getIslandWarps().size());
        SuperiorPlayer owner = island.getOwner();
        if (owner != null) {
            data.addProperty("ownerUuid", owner.getUniqueId().toString());
            data.addProperty("ownerName", owner.getName());
        }
        data.add("warps", serializeWarps(island, true, true));
        return GatewayResponse.ok(data);
    }

    @Override
    public GatewayResponse createHomeWarp(UUID actorUuid, String name, Location location) {
        SuperiorPlayer actor = requirePlayer(actorUuid, "actor");
        Island island = requireIsland(actor);
        Map<String, IslandWarp> warps = island.getIslandWarps();
        if (warps.size() >= HOME_WARP_LIMIT) {
            throw new GatewayException(ErrorCode.CONFLICT, "Warp limit reached");
        }
        if (warps.containsKey(name)) {
            throw new GatewayException(ErrorCode.CONFLICT, "Warp already exists");
        }
        if (location.getWorld() == null) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "location world is required");
        }
        IslandWarp warp = island.createWarp(name, location, null);
        if (warp == null) {
            throw new GatewayException(ErrorCode.CONFLICT, "Failed to create warp");
        }
        JsonObject data = new JsonObject();
        data.addProperty("name", warp.getName());
        data.add("warp", serializeWarp(warp, true));
        return GatewayResponse.ok(data);
    }

    @Override
    public GatewayResponse deleteHomeWarp(UUID actorUuid, String warpName) {
        SuperiorPlayer actor = requirePlayer(actorUuid, "actor");
        Island island = requireIsland(actor);
        IslandWarp warp = island.getWarp(warpName);
        if (warp == null) {
            throw new GatewayException(ErrorCode.NOT_FOUND, "Warp not found");
        }
        island.deleteWarp(actor, warp.getLocation());
        JsonObject data = new JsonObject();
        data.addProperty("deleted", warpName);
        return GatewayResponse.ok(data);
    }

    @Override
    public GatewayResponse renameHomeWarp(UUID actorUuid, String warpName, String newName) {
        SuperiorPlayer actor = requirePlayer(actorUuid, "actor");
        Island island = requireIsland(actor);
        IslandWarp warp = island.getWarp(warpName);
        if (warp == null) {
            throw new GatewayException(ErrorCode.NOT_FOUND, "Warp not found");
        }
        if (island.getIslandWarps().containsKey(newName)) {
            throw new GatewayException(ErrorCode.CONFLICT, "Warp with that name already exists");
        }
        island.renameWarp(warp, newName);
        JsonObject data = new JsonObject();
        data.addProperty("name", newName);
        return GatewayResponse.ok(data);
    }

    @Override
    public GatewayResponse toggleHomeWarpPrivacy(UUID actorUuid, String warpName) {
        SuperiorPlayer actor = requirePlayer(actorUuid, "actor");
        Island island = requireIsland(actor);
        IslandWarp warp = island.getWarp(warpName);
        if (warp == null) {
            throw new GatewayException(ErrorCode.NOT_FOUND, "Warp not found");
        }
        boolean next = !warp.hasPrivateFlag();
        warp.setPrivateFlag(next);
        JsonObject data = new JsonObject();
        data.addProperty("name", warpName);
        data.addProperty("private", next);
        return GatewayResponse.ok(data);
    }

    @Override
    public GatewayResponse listPlayerWarps(UUID actorUuid, String targetIdentifier) {
        SuperiorPlayer actor = requirePlayer(actorUuid, "actor");
        Island island = targetIdentifier == null || targetIdentifier.isBlank()
                ? requireIsland(actor)
                : resolveIslandByIdentifier(targetIdentifier);
        if (island == null) {
            throw new GatewayException(ErrorCode.ISLAND_NOT_FOUND, "Island not found");
        }
        JsonObject data = new JsonObject();
        data.addProperty("islandId", island.getUniqueId().toString());
        if (island.getName() != null) {
            data.addProperty("islandName", island.getName());
        }
        SuperiorPlayer owner = island.getOwner();
        if (owner != null) {
            data.addProperty("ownerUuid", owner.getUniqueId().toString());
            data.addProperty("ownerName", owner.getName());
        }
        boolean member = isMember(island, actorUuid);
        data.addProperty("isMember", member);
        data.addProperty("isOwner", owner != null && owner.getUniqueId().equals(actorUuid));
        data.addProperty("maxWarps", HOME_WARP_LIMIT);
        data.add("warps", serializeWarps(island, true, true));
        return GatewayResponse.ok(data);
    }

    @Override
    public GatewayResponse listGlobalWarps(UUID actorUuid, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, pageSize);
        List<JsonObject> summaries = new ArrayList<>();
        for (Island island : allIslands()) {
            List<IslandWarp> publicWarps = island.getIslandWarps().values().stream()
                    .filter(warp -> warp != null && !warp.hasPrivateFlag())
                    .collect(Collectors.toList());
            if (publicWarps.isEmpty()) {
                continue;
            }
            JsonObject summary = new JsonObject();
            summary.addProperty("islandId", island.getUniqueId().toString());
            if (island.getName() != null) {
                summary.addProperty("islandName", island.getName());
            }
            SuperiorPlayer owner = island.getOwner();
            if (owner != null) {
                summary.addProperty("ownerUuid", owner.getUniqueId().toString());
                summary.addProperty("ownerName", owner.getName());
            }
            summary.addProperty("totalRating", island.getTotalRating());
            summary.addProperty("members", island.getIslandMembers(true).size());
            summary.addProperty("creation", island.getCreationTime());
            summary.addProperty("warpCount", publicWarps.size());
            JsonArray warps = new JsonArray();
            publicWarps.forEach(warp -> warps.add(serializeWarp(warp, false)));
            summary.add("warps", warps);
            summaries.add(summary);
        }
        summaries.sort(Comparator.comparingDouble(obj -> -obj.get("totalRating").getAsDouble()));
        int total = summaries.size();
        int from = Math.min((safePage - 1) * safeSize, total);
        int to = Math.min(from + safeSize, total);
        JsonArray pageArray = new JsonArray();
        for (int i = from; i < to; i++) {
            pageArray.add(summaries.get(i));
        }
        JsonObject data = new JsonObject();
        data.addProperty("page", safePage);
        data.addProperty("pageSize", safeSize);
        data.addProperty("total", total);
        data.add("islands", pageArray);
        return GatewayResponse.ok(data);
    }

    @Override
    public GatewayResponse visitWarp(UUID actorUuid, String islandIdentifier, String warpName) {
        SuperiorPlayer actor = requirePlayer(actorUuid, "actor");
        Island island = resolveIslandByIdentifier(islandIdentifier);
        if (island == null) {
            throw new GatewayException(ErrorCode.ISLAND_NOT_FOUND, "Island not found");
        }
        IslandWarp warp = island.getWarp(warpName);
        if (warp == null) {
            throw new GatewayException(ErrorCode.NOT_FOUND, "Warp not found");
        }
        boolean member = isMember(island, actorUuid);
        if (warp.hasPrivateFlag() && !member) {
            throw new GatewayException(ErrorCode.FORBIDDEN, "Warp is private");
        }
        island.warpPlayer(actor, warpName);
        JsonObject data = new JsonObject();
        data.addProperty("islandId", island.getUniqueId().toString());
        data.addProperty("warp", warpName);
        return GatewayResponse.ok(data);
    }

    @Override
    public GatewayResponse adminResetPermissions(UUID actorUuid, String playerIdentifier) {
        if (playerIdentifier == null || playerIdentifier.isBlank()) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "target must be provided");
        }
        SuperiorPlayer target = resolvePlayer(playerIdentifier);
        if (target == null) {
            throw new GatewayException(ErrorCode.NOT_FOUND, "Target player not found");
        }
        Island island = requireIsland(target);
        island.resetPermissions();
        JsonObject data = new JsonObject();
        data.addProperty("islandId", island.getUniqueId().toString());
        data.addProperty("targetUuid", target.getUniqueId().toString());
        data.addProperty("targetName", target.getName());
        if (actorUuid != null) {
            data.addProperty("actorUuid", actorUuid.toString());
        }
        return GatewayResponse.ok(data);
    }

    @Override
    public GatewayResponse adminLookupIslandUuid(String playerIdentifier) {
        if (playerIdentifier == null || playerIdentifier.isBlank()) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "target must be provided");
        }
        SuperiorPlayer target = resolvePlayer(playerIdentifier);
        if (target == null) {
            throw new GatewayException(ErrorCode.NOT_FOUND, "Target player not found");
        }
        Island island = target.getIsland();
        if (island == null) {
            throw new GatewayException(ErrorCode.ISLAND_NOT_FOUND, "Target player has no island");
        }
        JsonObject data = new JsonObject();
        data.addProperty("islandId", island.getUniqueId().toString());
        data.addProperty("playerUuid", target.getUniqueId().toString());
        data.addProperty("playerName", target.getName());
        return GatewayResponse.ok(data);
    }

    @Override
    public GatewayResponse adminLookupIslandOwner(String islandIdentifier) {
        if (islandIdentifier == null || islandIdentifier.isBlank()) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "islandId must be provided");
        }
        Island island = resolveIslandByIdentifier(islandIdentifier);
        if (island == null) {
            throw new GatewayException(ErrorCode.ISLAND_NOT_FOUND, "Island not found");
        }
        SuperiorPlayer owner = island.getOwner();
        JsonObject data = new JsonObject();
        data.addProperty("islandId", island.getUniqueId().toString());
        if (owner != null) {
            data.addProperty("ownerUuid", owner.getUniqueId().toString());
            data.addProperty("ownerName", owner.getName());
        }
        return GatewayResponse.ok(data);
    }

    @Override
    public GatewayResponse listRolePermissions(UUID actorUuid) {
        SuperiorPlayer actor = requirePlayer(actorUuid, "actor");
        Island island = requireIsland(actor);
        boolean canManage = canManageRolePermissions(actor, island);

        List<PlayerRole> roles = new ArrayList<>(rolesManager.getRoles());
        roles.removeIf(java.util.Objects::isNull);
        roles.sort(Comparator.comparingInt(PlayerRole::getWeight));

        Set<String> seenPrivileges = new LinkedHashSet<>();
        List<IslandPrivilege> privileges = new ArrayList<>();
        for (IslandPrivilege privilege : IslandPrivilege.values()) {
            IslandPrivilege canonical = resolvePrivilege(privilege.getName());
            if (canonical == null) {
                continue;
            }
            if (seenPrivileges.add(canonical.getName())) {
                privileges.add(canonical);
            }
        }
        privileges.sort(Comparator.comparing(IslandPrivilege::getName, String.CASE_INSENSITIVE_ORDER));

        JsonObject data = new JsonObject();
        data.addProperty("islandId", island.getUniqueId().toString());
        data.addProperty("islandName", island.getName());
        data.addProperty("canManage", canManage);

        JsonArray rolesArray = new JsonArray();
        for (PlayerRole role : roles) {
            JsonObject roleJson = new JsonObject();
            roleJson.addProperty("name", role.getName());
            roleJson.addProperty("displayName", role.getDisplayName() != null ? role.getDisplayName() : role.getName());
            roleJson.addProperty("weight", role.getWeight());
            JsonArray permArray = new JsonArray();
            for (IslandPrivilege privilege : privileges) {
                JsonObject permJson = new JsonObject();
                permJson.addProperty("name", privilege.getName());
                permJson.addProperty("enabled", island.hasPermission(role, privilege));
                permArray.add(permJson);
            }
            roleJson.add("permissions", permArray);
            rolesArray.add(roleJson);
        }
        data.add("roles", rolesArray);
        return GatewayResponse.ok(data);
    }

    @Override
    public GatewayResponse updateRolePermission(UUID actorUuid, String roleName, String privilegeName, boolean enabled) {
        SuperiorPlayer actor = requirePlayer(actorUuid, "actor");
        Island island = requireIsland(actor);
        if (!canManageRolePermissions(actor, island)) {
            throw new GatewayException(ErrorCode.FORBIDDEN, "Insufficient permissions to modify role settings");
        }
        if (roleName == null || roleName.isBlank()) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "role is required");
        }
        if (privilegeName == null || privilegeName.isBlank()) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "privilege is required");
        }

        PlayerRole role = resolveRole(roleName);
        if (role == null) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "Unknown role: " + roleName);
        }
        IslandPrivilege privilege = resolvePrivilege(privilegeName);
        if (privilege == null) {
            throw new GatewayException(ErrorCode.BAD_REQUEST, "Unknown privilege: " + privilegeName);
        }

        island.setPermission(role, privilege, enabled);

        JsonObject data = new JsonObject();
        data.addProperty("role", role.getName());
        data.addProperty("displayName", role.getDisplayName() != null ? role.getDisplayName() : role.getName());
        data.addProperty("privilege", privilege.getName());
        data.addProperty("enabled", island.hasPermission(role, privilege));
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

    private boolean isMember(Island island, UUID playerUuid) {
        if (island == null || playerUuid == null) {
            return false;
        }
        return island.getIslandMembers(true).stream()
                .anyMatch(member -> member != null && playerUuid.equals(member.getUniqueId()));
    }

    private JsonArray serializeWarps(Island island, boolean includePrivate, boolean includeLocation) {
        JsonArray array = new JsonArray();
        if (island == null) {
            return array;
        }
        for (IslandWarp warp : island.getIslandWarps().values()) {
            if (warp == null) {
                continue;
            }
            if (!includePrivate && warp.hasPrivateFlag()) {
                continue;
            }
            array.add(serializeWarp(warp, includeLocation));
        }
        return array;
    }

    private JsonObject serializeWarp(IslandWarp warp, boolean includeLocation) {
        JsonObject json = new JsonObject();
        json.addProperty("name", warp.getName());
        json.addProperty("private", warp.hasPrivateFlag());
        if (includeLocation) {
            Location location = warp.getLocation();
            if (location != null && location.getWorld() != null) {
                JsonObject loc = new JsonObject();
                loc.addProperty("world", location.getWorld().getName());
                loc.addProperty("x", location.getX());
                loc.addProperty("y", location.getY());
                loc.addProperty("z", location.getZ());
                loc.addProperty("yaw", location.getYaw());
                loc.addProperty("pitch", location.getPitch());
                json.add("location", loc);
            }
        }
        return json;
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
        PlayerMetadataService metadataService = NetworkSkyblockAPI.metadataService().orElse(null);
        for (SuperiorPlayer member : members) {
            if (member == null) {
                continue;
            }
            JsonObject json = new JsonObject();
            json.addProperty("uuid", member.getUniqueId().toString());
            json.addProperty("name", member.getName());
            PlayerRole role = member.getPlayerRole();
            json.addProperty("role", role != null ? role.getName() : "UNKNOWN");
            json.addProperty("roleWeight", role != null ? role.getWeight() : Integer.MAX_VALUE);
            boolean online = member.asPlayer() != null;
            String server = null;
            String texture = null;
            if (metadataService != null) {
                Optional<String> storedOnline = metadataValue(metadataService, member.getUniqueId(), "presence.online");
                if (storedOnline.isPresent()) {
                    online = Boolean.parseBoolean(storedOnline.get());
                }
                server = metadataValue(metadataService, member.getUniqueId(), "presence.server").orElse(null);
                texture = metadataValue(metadataService, member.getUniqueId(), "skin.texture").orElse(null);
            }
            json.addProperty("online", online);
            if (server != null && !server.isBlank()) {
                json.addProperty("server", server);
            }
            if (texture != null && !texture.isBlank()) {
                json.addProperty("skinTexture", texture);
            }
            array.add(json);
        }
        return array;
    }

    private JsonObject playerSummary(SuperiorPlayer player) {
        JsonArray array = membersToJsonArray(Collections.singletonList(player));
        if (array.size() > 0 && array.get(0).isJsonObject()) {
            return array.get(0).getAsJsonObject();
        }
        JsonObject json = new JsonObject();
        if (player != null) {
            json.addProperty("uuid", player.getUniqueId().toString());
            json.addProperty("name", player.getName());
        }
        return json;
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

    private IslandPrivilege resolvePrivilege(String privilegeName) {
        if (privilegeName == null || privilegeName.isBlank()) {
            return null;
        }
        IslandPrivilege direct = IslandPrivilege.getByName(privilegeName);
        if (direct != null) {
            return direct;
        }
        String aliasKey = privilegeName.toUpperCase(Locale.ROOT);
        String mapped = PRIVILEGE_ALIASES.get(aliasKey);
        if (mapped != null) {
            IslandPrivilege aliasPrivilege = IslandPrivilege.getByName(mapped);
            if (aliasPrivilege != null) {
                return aliasPrivilege;
            }
        }
        String normalized = privilegeName.replace("_", "").toLowerCase(Locale.ROOT);
        for (IslandPrivilege candidate : IslandPrivilege.values()) {
            String candidateName = candidate.getName();
            if (candidateName != null
                    && candidateName.replace("_", "").toLowerCase(Locale.ROOT).equals(normalized)) {
                return candidate;
            }
        }
        return null;
    }

    private static final long DEFAULT_INVITE_TTL_MILLIS = 10L * 60L * 1000L;

    private boolean canManageRolePermissions(SuperiorPlayer actor, Island island) {
        if (actor == null || island == null) {
            return false;
        }
        SuperiorPlayer owner = island.getOwner();
        if (owner != null && owner.getUniqueId().equals(actor.getUniqueId())) {
            return true;
        }
        for (String key : ROLE_PERMISSION_PRIVILEGES) {
            IslandPrivilege privilege = resolvePrivilege(key);
            if (privilege != null && island.hasPermission(actor, privilege)) {
                return true;
            }
        }
        return false;
    }

    private PlayerRole resolveRole(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return null;
        }
        PlayerRole direct = rolesManager.getPlayerRole(roleName);
        if (direct != null) {
            return direct;
        }
        try {
            int id = Integer.parseInt(roleName);
            PlayerRole byId = rolesManager.getPlayerRoleFromId(id);
            if (byId != null) {
                return byId;
            }
        } catch (NumberFormatException ignored) {
            // ignore and fall back to case-insensitive matching
        }
        List<PlayerRole> roles = rolesManager.getRoles();
        if (roles != null) {
            for (PlayerRole role : roles) {
                if (role != null && role.getName() != null && role.getName().equalsIgnoreCase(roleName)) {
                    return role;
                }
            }
        }
        return null;
    }

    private IslandBank requireBank(Island island) {
        IslandBank bank = island.getIslandBank();
        if (bank == null) {
            throw new GatewayException(ErrorCode.INTERNAL, "Island bank not available");
        }
        return bank;
    }

    private BigDecimal sanitize(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private Optional<String> metadataValue(PlayerMetadataService service, UUID uuid, String key) {
        try {
            return service.get(uuid, key).get(200, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (ExecutionException | TimeoutException ex) {
            return Optional.empty();
        }
    }
}
