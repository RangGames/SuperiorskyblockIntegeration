package wiki.creeper.superiorskyblockIntegeration.gateway;

import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.JedisPubSub;

import java.util.concurrent.ExecutorService;
import java.util.logging.Level;

import wiki.creeper.superiorskyblockIntegeration.redis.MessageSecurity;
import wiki.creeper.superiorskyblockIntegeration.redis.RedisMessage;

final class GatewaySubscriber extends JedisPubSub {

    private final JavaPlugin plugin;
    private final MessageSecurity security;
    private final GatewayRequestRouter router;
    private final ExecutorService workers;

    GatewaySubscriber(JavaPlugin plugin, MessageSecurity security, GatewayRequestRouter router, ExecutorService workers) {
        this.plugin = plugin;
        this.security = security;
        this.router = router;
        this.workers = workers;
    }

    @Override
    public void onPMessage(String pattern, String channel, String message) {
        workers.submit(() -> {
            try {
                RedisMessage payload = RedisMessage.parse(message);
                if (!security.verify(payload)) {
                    plugin.getLogger().warning("Rejected request with invalid signature: " + payload.id());
                    return;
                }
                router.handle(channel, payload);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to process gateway request", ex);
            }
        });
    }

    void gracefulShutdown() {
        try {
            this.punsubscribe();
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Error while unsubscribing gateway listener", ex);
        }
    }
}
