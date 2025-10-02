package wiki.creeper.superiorskyblockIntegeration.client;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

import wiki.creeper.superiorskyblockIntegeration.common.ComponentLifecycle;
import wiki.creeper.superiorskyblockIntegeration.config.PluginConfig;
import wiki.creeper.superiorskyblockIntegeration.redis.RedisManager;

public final class ClientPlugin extends JavaPlugin {

    private PluginConfig configuration;
    private RedisManager redisManager;
    private ComponentLifecycle application;

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

        redisManager = new RedisManager(this, configuration.redis());
        try {
            redisManager.start();
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Failed to initialize Redis", ex);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        application = new ClientApplication(this, configuration, redisManager);
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
    }
}
