package wiki.creeper.superiorskyblockIntegeration.gateway.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import wiki.creeper.superiorskyblockIntegeration.gateway.services.GatewayVelocityService;

public final class SpawnCommand implements CommandExecutor {

    private final GatewayVelocityService velocityService;

    public SpawnCommand(GatewayVelocityService velocityService) {
        this.velocityService = velocityService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "해당 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }
        velocityService.connectToLobby(player);
        return true;
    }
}
