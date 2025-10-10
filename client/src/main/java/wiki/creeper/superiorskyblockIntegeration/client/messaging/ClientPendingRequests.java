package wiki.creeper.superiorskyblockIntegeration.client.messaging;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

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

        if (timeoutMs > 0L) {
            CompletableFuture.delayedExecutor(timeoutMs, TimeUnit.MILLISECONDS).execute(() -> {
                if (inflight.remove(requestId, future) && !future.isDone()) {
                    future.completeExceptionally(new TimeoutException("Timed out waiting for response " + requestId));
                    if (plugin.getLogger().isLoggable(Level.FINE)) {
                        plugin.getLogger().fine("Timed out waiting for Redis response " + requestId);
                    }
                }
            });
        }

        future.whenComplete((response, throwable) -> inflight.remove(requestId, future));
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
