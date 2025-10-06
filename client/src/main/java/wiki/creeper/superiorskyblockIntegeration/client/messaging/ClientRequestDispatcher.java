package wiki.creeper.superiorskyblockIntegeration.client.messaging;

import com.google.gson.JsonObject;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import wiki.creeper.superiorskyblockIntegeration.common.Operations;
import wiki.creeper.superiorskyblockIntegeration.config.PluginConfig;
import wiki.creeper.superiorskyblockIntegeration.redis.MessageSecurity;
import wiki.creeper.superiorskyblockIntegeration.redis.RedisChannels;
import wiki.creeper.superiorskyblockIntegeration.redis.RedisManager;
import wiki.creeper.superiorskyblockIntegeration.redis.RedisMessage;

/**
 * Handles outgoing requests to the gateway and awaits responses.
 */
public final class ClientRequestDispatcher {

    private final JavaPlugin plugin;
    private final PluginConfig config;
    private final RedisManager redisManager;
    private final RedisChannels channels;
    private final MessageSecurity security;
    private final ClientPendingRequests pendingRequests;

    public ClientRequestDispatcher(JavaPlugin plugin,
                                   PluginConfig config,
                                   RedisManager redisManager,
                                   RedisChannels channels,
                                   MessageSecurity security,
                                   ClientPendingRequests pendingRequests) {
        this.plugin = plugin;
        this.config = config;
        this.redisManager = redisManager;
        this.channels = channels;
        this.security = security;
        this.pendingRequests = pendingRequests;
    }

    public CompletableFuture<RedisMessage> send(Operations operation,
                                                Player actor,
                                                Consumer<RedisMessage> payloadCustomizer) {
        RedisMessage request = RedisMessage.request(operation.op());
        if (actor != null) {
            request.setActor(actor.getUniqueId().toString());
            request.root().addProperty("actorName", actor.getName());
            JsonObject ctx = new JsonObject();
            ctx.addProperty("server", plugin.getServer().getName());
            ctx.addProperty("locale", actor.getLocale());
            request.root().add("ctx", ctx);
        } else {
            request.setActor(UUID.randomUUID().toString());
        }

        if (payloadCustomizer != null) {
            payloadCustomizer.accept(request);
        }

        request.compressDataIfNeeded(config.redis().messageCompressionThreshold());
        security.sign(request);

        CompletableFuture<RedisMessage> future = pendingRequests.register(
                request.id(),
                config.timeouts().responseMaxWaitMs()
        );

        String channel = channels.requestChannel(operation.op());
        redisManager.publish(channel, request.toJson());
        return future;
    }
}
