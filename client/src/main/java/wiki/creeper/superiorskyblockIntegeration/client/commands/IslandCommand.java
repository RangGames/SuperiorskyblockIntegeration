package wiki.creeper.superiorskyblockIntegeration.client.commands;

import com.google.gson.JsonObject;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import wiki.creeper.superiorskyblockIntegeration.client.cache.ClientCache;
import wiki.creeper.superiorskyblockIntegeration.client.cache.ClientCacheKeys;
import wiki.creeper.superiorskyblockIntegeration.client.messaging.ClientRequestDispatcher;
import wiki.creeper.superiorskyblockIntegeration.common.Operations;
import wiki.creeper.superiorskyblockIntegeration.redis.RedisMessage;

/**
 * Handles /is command forwarding to the gateway.
 */
public final class IslandCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final ClientRequestDispatcher dispatcher;
    private final ClientCache cache;

    public IslandCommand(JavaPlugin plugin, ClientRequestDispatcher dispatcher, ClientCache cache) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.cache = cache;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            showHelp(player, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "invite" -> handleInvite(player, args);
            case "accept" -> handleAccept(player, args);
            case "deny" -> handleDeny(player, args);
            case "members" -> handleMembers(player, args);
            case "info" -> handleInfo(player, args);
            default -> showHelp(player, label);
        }
        return true;
    }

    private void handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /is invite <player>");
            return;
        }
        String target = args[1];
        dispatch(player, Operations.INVITE_CREATE, message -> {
            JsonObject data = new JsonObject();
            data.addProperty("target", target);
            message.mergeData(data);
        }, response -> {
            if (response.ok()) {
                player.sendMessage(ChatColor.GREEN + "[Skyblock] 초대를 전송했습니다.");
            } else {
                player.sendMessage(ChatColor.RED + "[Skyblock] 초대 실패: " + describeError(response));
            }
        });
    }

    private void handleAccept(Player player, String[] args) {
        dispatch(player, Operations.INVITE_ACCEPT, message -> {
            if (args.length > 1) {
                JsonObject data = new JsonObject();
                data.addProperty("inviteId", args[1]);
                message.mergeData(data);
            }
        }, response -> {
            if (response.ok()) {
                player.sendMessage(ChatColor.GREEN + "[Skyblock] 초대를 수락했습니다.");
            } else {
                player.sendMessage(ChatColor.RED + "[Skyblock] 수락 실패: " + describeError(response));
            }
        });
    }

    private void handleDeny(Player player, String[] args) {
        dispatch(player, Operations.INVITE_DENY, message -> {
            if (args.length > 1) {
                JsonObject data = new JsonObject();
                data.addProperty("inviteId", args[1]);
                message.mergeData(data);
            }
        }, response -> {
            if (response.ok()) {
                player.sendMessage(ChatColor.YELLOW + "[Skyblock] 초대를 거절했습니다.");
            } else {
                player.sendMessage(ChatColor.RED + "[Skyblock] 거절 실패: " + describeError(response));
            }
        });
    }

    private void handleMembers(Player player, String[] args) {
        String cacheKey = ClientCacheKeys.members(player.getUniqueId().toString());
        if (cache.enabled()) {
            cache.get(cacheKey).ifPresent(data -> {
                if (data.has("members")) {
                    player.sendMessage(ChatColor.AQUA + "[Skyblock] (캐시) 멤버 수: " + data.getAsJsonArray("members").size());
                }
            });
        }
        dispatch(player, Operations.MEMBERS_LIST, null, response -> {
            if (response.ok()) {
                cache.put(cacheKey, response.data());
                int count = response.data().has("members") ? response.data().getAsJsonArray("members").size() : 0;
                player.sendMessage(ChatColor.AQUA + "[Skyblock] 멤버 수: " + count);
            } else {
                player.sendMessage(ChatColor.RED + "[Skyblock] 멤버 목록 조회 실패: " + describeError(response));
            }
        });
    }

    private void handleInfo(Player player, String[] args) {
        String ownerKey = args.length > 1 ? args[1] : player.getUniqueId().toString();
        String cacheKey = ClientCacheKeys.island(ownerKey);
        if (cache.enabled()) {
            cache.get(cacheKey).ifPresent(data -> {
                if (data.has("name")) {
                    player.sendMessage(ChatColor.AQUA + "[Skyblock] (캐시) 섬 이름: " + data.get("name").getAsString());
                }
            });
        }
        dispatch(player, Operations.ISLAND_GET, message -> {
            if (args.length > 1) {
                JsonObject data = new JsonObject();
                data.addProperty("owner", args[1]);
                message.mergeData(data);
            }
        }, response -> {
            if (response.ok()) {
                cache.put(cacheKey, response.data());
                player.sendMessage(ChatColor.AQUA + "[Skyblock] 섬 정보: " + response.data().toString());
            } else {
                player.sendMessage(ChatColor.RED + "[Skyblock] 섬 정보 조회 실패: " + describeError(response));
            }
        });
    }

    private void showHelp(Player player, String label) {
        player.sendMessage(ChatColor.GREEN + "Skyblock 명령어:");
        player.sendMessage(ChatColor.YELLOW + "/" + label + " invite <player>");
        player.sendMessage(ChatColor.YELLOW + "/" + label + " accept [inviteId]");
        player.sendMessage(ChatColor.YELLOW + "/" + label + " deny [inviteId]");
        player.sendMessage(ChatColor.YELLOW + "/" + label + " members");
        player.sendMessage(ChatColor.YELLOW + "/" + label + " info [player]");
    }

    private void dispatch(Player player,
                          Operations operation,
                          Consumer<RedisMessage> payloadCustomizer,
                          Consumer<RedisMessage> responseConsumer) {
        player.sendMessage(ChatColor.GOLD + "[Skyblock] 처리 중입니다...");
        CompletableFuture<RedisMessage> future = dispatcher.send(operation, player, payloadCustomizer);
        future.whenComplete((response, throwable) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (throwable != null) {
                player.sendMessage(ChatColor.RED + "[Skyblock] 처리 실패: " + throwable.getMessage());
                return;
            }
            responseConsumer.accept(response);
        }));
    }

    private String describeError(RedisMessage response) {
        JsonObject error = response.error();
        String code = error.has("code") ? error.get("code").getAsString() : "UNKNOWN";
        String message = error.has("message") ? error.get("message").getAsString() : "";
        return code + (message.isEmpty() ? "" : " (" + message + ")");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("invite", "accept", "deny", "members", "info");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("invite")) {
            return Collections.singletonList("<player>");
        }
        return Collections.emptyList();
    }
}
