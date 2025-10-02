package wiki.creeper.superiorskyblockIntegeration.client.messaging;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

import wiki.creeper.superiorskyblockIntegeration.redis.RedisMessage;

/**
 * Tracks in-flight requests awaiting Redis responses.
 */
public final class ClientPendingRequests {

    private final JavaPlugin plugin;
    private final Map<String, CompletableFuture<RedisMessage>> inflight = new ConcurrentHashMap<>();

    public ClientPendingRequests(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<RedisMessage> register(String requestId, long timeoutMs) {
        CompletableFuture<RedisMessage> future = new CompletableFuture<>();
        inflight.put(requestId, future);
        long ticks = Math.max(1L, timeoutMs / 50L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            CompletableFuture<RedisMessage> pending = inflight.remove(requestId);
            if (pending != null) {
                pending.completeExceptionally(new TimeoutException("Timed out waiting for response " + requestId));
            }
        }, ticks);
        return future;
    }

    public void complete(String requestId, RedisMessage response) {
        CompletableFuture<RedisMessage> future = inflight.remove(requestId);
        if (future != null) {
            future.complete(response);
        }
    }

    public void failAll(Throwable cause) {
        inflight.forEach((id, future) -> future.completeExceptionally(cause));
        inflight.clear();
    }
}
