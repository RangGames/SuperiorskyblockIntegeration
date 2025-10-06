package wiki.creeper.superiorskyblockIntegeration.gateway.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

import wiki.creeper.superiorskyblockIntegeration.config.PluginConfig;

/**
 * Manages the shared HikariCP connection pool for the gateway.
 */
public final class GatewayDatabase implements AutoCloseable {

    private final HikariDataSource dataSource;

    public GatewayDatabase(JavaPlugin plugin, PluginConfig.DatabaseSettings settings) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(settings, "settings");

        if (!"mysql".equalsIgnoreCase(settings.type())) {
            throw new IllegalArgumentException("Unsupported database type: " + settings.type());
        }

        HikariConfig config = new HikariConfig();
        config.setPoolName("SSB2-Gateway-Pool");
        config.setJdbcUrl(buildJdbcUrl(settings));
        config.setUsername(settings.username());
        config.setPassword(settings.password());
        config.setMaximumPoolSize(Math.max(1, settings.hikari().maximumPoolSize()));
        config.setMinimumIdle(Math.max(0, Math.min(settings.hikari().minimumIdle(), settings.hikari().maximumPoolSize())));
        config.setConnectionTimeout(Math.max(1000L, settings.hikari().connectionTimeoutMs()));
        config.setIdleTimeout(Math.max(0L, settings.hikari().idleTimeoutMs()));
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setInitializationFailTimeout(-1L);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");

        this.dataSource = new HikariDataSource(config);
    }

    private String buildJdbcUrl(PluginConfig.DatabaseSettings settings) {
        return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC",
                settings.host(),
                settings.port(),
                settings.name());
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void close() {
        if (!dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
