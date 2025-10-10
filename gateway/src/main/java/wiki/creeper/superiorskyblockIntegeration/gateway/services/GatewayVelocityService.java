package wiki.creeper.superiorskyblockIntegeration.gateway.services;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import wiki.creeper.superiorskyblockIntegeration.config.PluginConfig;

/**
 * Handles Velocity/BungeeCord transfers for the gateway plugin.
 */
public final class GatewayVelocityService {

    private final JavaPlugin plugin;
    private final PluginConfig.VelocitySettings settings;

    public GatewayVelocityService(JavaPlugin plugin, PluginConfig.VelocitySettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    public boolean enabled() {
        return settings != null && settings.enabled();
    }

    public void connectToLobby(Player player) {
        if (player == null) {
            return;
        }
        if (!enabled()) {
            teleportToLocalSpawn(player);
            return;
        }
        String lobby = settings.lobbyServer();
        if (lobby == null || lobby.isBlank()) {
            teleportToLocalSpawn(player);
            return;
        }
        player.sendMessage(ChatColor.YELLOW + "[Skyblock] 로비로 이동합니다...");
        connect(player, lobby);
    }

    public void connect(Player player, String server) {
        if (player == null) {
            return;
        }
        if (!enabled() || server == null || server.isBlank()) {
            teleportToLocalSpawn(player);
            return;
        }
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(server);
        player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
    }

    private void teleportToLocalSpawn(Player player) {
        if (player == null) {
            return;
        }
        Location spawn = player.getWorld().getSpawnLocation();
        player.teleport(spawn);
    }
}
