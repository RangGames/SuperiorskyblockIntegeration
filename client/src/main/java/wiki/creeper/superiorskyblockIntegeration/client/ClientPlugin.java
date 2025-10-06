package wiki.creeper.superiorskyblockIntegeration.client;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

import wiki.creeper.superiorskyblockIntegeration.client.lang.Messages;
import wiki.creeper.superiorskyblockIntegeration.common.ComponentLifecycle;
import wiki.creeper.superiorskyblockIntegeration.config.PluginConfig;
import wiki.creeper.superiorskyblockIntegeration.redis.RedisManager;

public final class ClientPlugin extends JavaPlugin {

    private PluginConfig configuration;
    private RedisManager redisManager;
    private ComponentLifecycle application;
    private Messages messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            configuration = PluginConfig.from(getConfig());
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Unable to load configuration", ex);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        messages = new Messages(this);

        redisManager = new RedisManager(this, configuration.redis());
        try {
            redisManager.start();
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Failed to initialize Redis", ex);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (configuration.client().velocity().enabled()) {
            getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        }

        application = new ClientApplication(this, configuration, redisManager, messages);
        application.start();
        getLogger().info("SuperiorSkyblock client component started");
    }

    @Override
    public void onDisable() {
        if (application != null) {
            try {
                application.stop();
            } catch (Exception ex) {
                getLogger().log(Level.WARNING, "Error while stopping client application", ex);
            }
        }
        if (redisManager != null) {
            redisManager.stop();
        }
        if (configuration != null && configuration.client().velocity().enabled()) {
            getServer().getMessenger().unregisterOutgoingPluginChannel(this, "BungeeCord");
        }
    }
}
