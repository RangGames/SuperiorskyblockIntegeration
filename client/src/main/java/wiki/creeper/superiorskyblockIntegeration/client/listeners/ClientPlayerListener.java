package wiki.creeper.superiorskyblockIntegeration.client.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.UUID;

import wiki.creeper.superiorskyblockIntegeration.api.PlayerMetadataService;
import wiki.creeper.superiorskyblockIntegeration.api.NetworkPlayerIslands;
import wiki.creeper.superiorskyblockIntegeration.client.ClientNetworkService;
import wiki.creeper.superiorskyblockIntegeration.client.services.ClientHeadDataService;

/**
 * Captures join events to keep player profile data in sync across the network.
 */
public final class ClientPlayerListener implements Listener {

    private final JavaPlugin plugin;
    private final ClientNetworkService networkService;
    private final ClientHeadDataService headDataService;

    public ClientPlayerListener(JavaPlugin plugin,
                                ClientNetworkService networkService,
                                ClientHeadDataService headDataService) {
        this.plugin = plugin;
        this.networkService = networkService;
        this.headDataService = headDataService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        networkService.registerPlayerProfile(event.getPlayer());
        headDataService.requestHeadData(event.getPlayer());
        UUID uuid = event.getPlayer().getUniqueId();
        PlayerMetadataService metadata = headDataService.getMetadataService();
        if (metadata != null) {
            metadata.put(uuid, "player.uuid", uuid.toString(), null);
            metadata.put(uuid, "player.name", event.getPlayer().getName(), null);
        }
        NetworkPlayerIslands.forPlayer(uuid).thenAccept(opt -> {
            if (opt.isPresent()) {
                if (metadata != null) {
                    metadata.put(uuid, "island.uuid", opt.get().toString(), null);
                }
            } else {
                if (metadata != null) {
                    metadata.get(uuid, "farm.autojoin.prompted").thenAccept(flag -> {
                        if (flag.isPresent()) {
                            return;
                        }
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            if (event.getPlayer().isOnline()) {
                                event.getPlayer().performCommand("팜");
                                metadata.put(uuid, "farm.autojoin.prompted", "true", null);
                            }
                        }, 20L);
                    });
                }
            }
        }).exceptionally(ex -> {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Failed to resolve island for " + uuid, ex);
            return null;
        });
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.setDeathMessage("");
    }
}
