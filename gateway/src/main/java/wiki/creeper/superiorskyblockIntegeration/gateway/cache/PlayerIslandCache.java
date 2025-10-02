package wiki.creeper.superiorskyblockIntegeration.gateway.cache;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import wiki.creeper.superiorskyblockIntegeration.api.PlayerIslandService;
import wiki.creeper.superiorskyblockIntegeration.gateway.bridge.SuperiorSkyblockBridge;

/**
 * Maintains a fast in-memory mapping between player UUIDs and their island UUIDs.
 */
public final class PlayerIslandCache implements PlayerIslandService {

    private final SuperiorSkyblockBridge bridge;
    private final ConcurrentHashMap<UUID, UUID> playerToIsland = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Set<UUID>> islandToPlayers = new ConcurrentHashMap<>();

    public PlayerIslandCache(SuperiorSkyblockBridge bridge) {
        this.bridge = bridge;
    }

    public void loadSnapshot(Map<UUID, UUID> snapshot) {
        playerToIsland.clear();
        islandToPlayers.clear();
        snapshot.forEach(this::setMembershipInternal);
    }

    public void setMembership(UUID playerUuid, UUID islandUuid) {
        if (playerUuid == null || islandUuid == null) {
            return;
        }
        setMembershipInternal(playerUuid, islandUuid);
    }

    public void removePlayer(UUID playerUuid) {
        if (playerUuid == null) {
            return;
        }
        UUID previous = playerToIsland.remove(playerUuid);
        if (previous != null) {
            islandToPlayers.computeIfPresent(previous, (id, members) -> {
                members.remove(playerUuid);
                return members.isEmpty() ? null : members;
            });
        }
    }

    public void removeIsland(UUID islandUuid) {
        if (islandUuid == null) {
            return;
        }
        Set<UUID> members = islandToPlayers.remove(islandUuid);
        if (members != null) {
            for (UUID member : members) {
                playerToIsland.remove(member, islandUuid);
            }
        }
    }

    @Override
    public Optional<UUID> islandId(UUID playerUuid) {
        return Optional.ofNullable(playerToIsland.get(playerUuid));
    }

    @Override
    public Collection<UUID> members(UUID islandUuid) {
        Set<UUID> members = islandToPlayers.get(islandUuid);
        if (members == null || members.isEmpty()) {
            return Collections.emptyList();
        }
        return List.copyOf(members);
    }

    @Override
    public Map<UUID, UUID> snapshot() {
        return Map.copyOf(playerToIsland);
    }

    @Override
    public void refresh(UUID playerUuid) {
        if (playerUuid == null) {
            return;
        }
        bridge.islandIdForPlayer(playerUuid)
                .ifPresentOrElse(island -> setMembership(playerUuid, island), () -> removePlayer(playerUuid));
    }

    private void setMembershipInternal(UUID playerUuid, UUID islandUuid) {
        UUID previous = playerToIsland.put(playerUuid, islandUuid);
        if (previous != null && !previous.equals(islandUuid)) {
            islandToPlayers.computeIfPresent(previous, (id, members) -> {
                members.remove(playerUuid);
                return members.isEmpty() ? null : members;
            });
        }
        islandToPlayers.computeIfAbsent(islandUuid, id -> ConcurrentHashMap.newKeySet()).add(playerUuid);
    }
}
