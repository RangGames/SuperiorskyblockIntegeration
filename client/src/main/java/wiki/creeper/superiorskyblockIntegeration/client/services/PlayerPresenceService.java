package wiki.creeper.superiorskyblockIntegeration.client.services;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

import rang.games.allPlayersUtil.RedisPlayerAPI;

/**
 * Leverages the shared RedisPlayerAPI provided by allplayersutil to resolve player presence.
 */
public final class PlayerPresenceService {

    private final JavaPlugin plugin;

    public PlayerPresenceService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<PlayerPresence> lookup(UUID uuid) {
        if (uuid == null) {
            return CompletableFuture.completedFuture(PlayerPresence.offline());
        }

        RedisPlayerAPI api;
        try {
            api = RedisPlayerAPI.getInstance();
        } catch (IllegalStateException ex) {
            plugin.getLogger().log(Level.FINEST, "RedisPlayerAPI not initialised; treating player as offline");
            return CompletableFuture.completedFuture(PlayerPresence.offline());
        }

        return api.getPlayerServerAsync(uuid.toString())
                .thenApply(server -> {
                    if (server == null || server.isBlank()) {
                        return PlayerPresence.offline();
                    }
                    return new PlayerPresence(true, server);
                })
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING,
                            "Failed to resolve network presence for " + uuid, ex);
                    return PlayerPresence.offline();
                });
    }

    public record PlayerPresence(boolean online, String server) {
        static PlayerPresence offline() {
            return new PlayerPresence(false, null);
        }
    }
}
