package wiki.creeper.superiorskyblockIntegeration.gateway.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import wiki.creeper.superiorskyblockIntegeration.common.quest.QuestType;
import wiki.creeper.superiorskyblockIntegeration.gateway.bridge.SuperiorSkyblockBridge;

/**
 * Persists farm ranking and contribution data in the configured SQL database.
 */
public final class GatewayRankingService {

    private static final String TABLE_RANKING = "ssb_farm_ranking";
    private static final String TABLE_CONTRIBUTIONS = "ssb_farm_contributions";
    private static final String TABLE_HISTORY_PERIODS = "ssb_farm_history_periods";
    private static final String TABLE_HISTORY_ENTRIES = "ssb_farm_history_entries";

    private final JavaPlugin plugin;
    private final GatewayDatabase database;
    private final SuperiorSkyblockBridge bridge;
    private final Logger logger;
    private final AtomicBoolean schemaReady = new AtomicBoolean(false);
    private BukkitTask schemaRetryTask;

    public GatewayRankingService(JavaPlugin plugin,
                                 GatewayDatabase database,
                                 SuperiorSkyblockBridge bridge,
                                 Logger logger) {
        this.plugin = plugin;
        this.database = database;
        this.bridge = bridge;
        this.logger = logger;
        if (!initializeSchema()) {
            scheduleSchemaRetry();
        }
    }

    private boolean initializeSchema() {
        try (Connection connection = database.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS ssb_farm_ranking (
                        island_id VARCHAR(36) PRIMARY KEY,
                        island_name VARCHAR(64) NOT NULL DEFAULT '',
                        owner_uuid VARCHAR(36) NULL,
                        owner_name VARCHAR(64) NOT NULL DEFAULT '',
                        total_points BIGINT NOT NULL DEFAULT 0,
                        daily_points BIGINT NOT NULL DEFAULT 0,
                        weekly_points BIGINT NOT NULL DEFAULT 0,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS ssb_farm_contributions (
                        island_id VARCHAR(36) NOT NULL,
                        player_uuid VARCHAR(36) NOT NULL,
                        player_name VARCHAR(64) NOT NULL DEFAULT '',
                        contribution BIGINT NOT NULL DEFAULT 0,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        PRIMARY KEY (island_id, player_uuid),
                        CONSTRAINT fk_farm_contributions_island FOREIGN KEY (island_id)
                            REFERENCES ssb_farm_ranking(island_id) ON DELETE CASCADE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS ssb_farm_history_periods (
                        period_id VARCHAR(64) PRIMARY KEY,
                        display_name VARCHAR(128) NOT NULL DEFAULT '',
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS ssb_farm_history_entries (
                        period_id VARCHAR(64) NOT NULL,
                        position INT NOT NULL,
                        island_id VARCHAR(36) NOT NULL,
                        island_name VARCHAR(64) NOT NULL DEFAULT '',
                        owner_uuid VARCHAR(36) NULL,
                        owner_name VARCHAR(64) NOT NULL DEFAULT '',
                        points BIGINT NOT NULL DEFAULT 0,
                        daily_points BIGINT NOT NULL DEFAULT 0,
                        weekly_points BIGINT NOT NULL DEFAULT 0,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (period_id, position),
                        CONSTRAINT fk_farm_history_period FOREIGN KEY (period_id)
                            REFERENCES ssb_farm_history_periods(period_id) ON DELETE CASCADE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
            if (schemaReady.compareAndSet(false, true)) {
                logger.info("Farm ranking database schema ready");
            }
            cancelSchemaRetry();
            return true;
        } catch (SQLException ex) {
            schemaReady.set(false);
            logger.log(Level.WARNING, "Farm ranking schema not ready; will retry", ex);
            return false;
        }
    }

    private void scheduleSchemaRetry() {
        cancelSchemaRetry();
        BukkitScheduler scheduler = plugin.getServer().getScheduler();
        long retryTicks = 20L * 30; // retry every 30 seconds
        schemaRetryTask = scheduler.runTaskTimerAsynchronously(plugin, () -> initializeSchema(), retryTicks, retryTicks);
    }

    private void cancelSchemaRetry() {
        if (schemaRetryTask != null) {
            schemaRetryTask.cancel();
            schemaRetryTask = null;
        }
    }

    private void ensureSchemaReady() {
        if (!schemaReady.get()) {
            initializeSchema();
        }
    }

    public void recordProgress(UUID islandUuid, UUID contributorUuid, int amount, QuestType type) {
        if (islandUuid == null || amount <= 0) {
            return;
        }
        long totalIncrement = amount;
        long dailyIncrement = type != null && type.isDaily() ? amount : 0L;
        long weeklyIncrement = type != null && type.isWeekly() ? amount : 0L;
        incrementScores(islandUuid, contributorUuid, totalIncrement, dailyIncrement, weeklyIncrement);
    }

    public void awardFarmPoints(UUID islandUuid, int farmPoints, QuestType type) {
        if (islandUuid == null || farmPoints <= 0) {
            return;
        }
        long totalIncrement = farmPoints;
        long dailyIncrement = type != null && type.isDaily() ? farmPoints : 0L;
        long weeklyIncrement = type != null && type.isWeekly() ? farmPoints : 0L;
        incrementScores(islandUuid, null, totalIncrement, dailyIncrement, weeklyIncrement);
    }

    public void incrementScores(UUID islandUuid,
                                UUID contributorUuid,
                                long totalIncrement,
                                long dailyIncrement,
                                long weeklyIncrement) {
        if (islandUuid == null) {
            return;
        }
        if (totalIncrement <= 0 && dailyIncrement <= 0 && weeklyIncrement <= 0) {
            return;
        }

        ensureSchemaReady();

        long effectiveTotal = totalIncrement;
        if (effectiveTotal <= 0) {
            long sum = dailyIncrement + weeklyIncrement;
            if (sum > 0) {
                effectiveTotal = sum;
            }
        }
        if (effectiveTotal <= 0) {
            return;
        }

        SuperiorSkyblockBridge.IslandDetails details = bridge.describeIsland(islandUuid);
        String islandName = details != null && details.name() != null ? details.name() : "";
        String ownerUuid = details != null && details.ownerUuid() != null ? details.ownerUuid().toString() : null;
        String ownerName = details != null && details.ownerName() != null ? details.ownerName() : "";

        String contributorName = "";
        if (contributorUuid != null) {
            contributorName = bridge.lookupPlayerName(contributorUuid.toString()).orElse("");
        }

        try (Connection connection = database.getConnection()) {
            try {
                connection.setAutoCommit(false);

                try (PreparedStatement ranking = connection.prepareStatement(
                        "INSERT INTO " + TABLE_RANKING + " (island_id, island_name, owner_uuid, owner_name, total_points, daily_points, weekly_points) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                                "ON DUPLICATE KEY UPDATE island_name = VALUES(island_name), owner_uuid = VALUES(owner_uuid), owner_name = VALUES(owner_name), " +
                                "total_points = total_points + ?, daily_points = daily_points + ?, weekly_points = weekly_points + ?, updated_at = CURRENT_TIMESTAMP"
                )) {
                    ranking.setString(1, islandUuid.toString());
                    ranking.setString(2, islandName);
                    if (ownerUuid != null) {
                        ranking.setString(3, ownerUuid);
                    } else {
                        ranking.setNull(3, java.sql.Types.VARCHAR);
                    }
                    ranking.setString(4, ownerName);
                    ranking.setLong(5, effectiveTotal);
                    ranking.setLong(6, dailyIncrement);
                    ranking.setLong(7, weeklyIncrement);
                    ranking.setLong(8, effectiveTotal);
                    ranking.setLong(9, dailyIncrement);
                    ranking.setLong(10, weeklyIncrement);
                    ranking.executeUpdate();
                }

                if (contributorUuid != null) {
                    try (PreparedStatement contribution = connection.prepareStatement(
                            "INSERT INTO " + TABLE_CONTRIBUTIONS + " (island_id, player_uuid, player_name, contribution) " +
                                    "VALUES (?, ?, ?, ?) " +
                                    "ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), contribution = contribution + VALUES(contribution), updated_at = CURRENT_TIMESTAMP"
                    )) {
                        contribution.setString(1, islandUuid.toString());
                        contribution.setString(2, contributorUuid.toString());
                        contribution.setString(3, contributorName);
                        contribution.setLong(4, effectiveTotal);
                        contribution.executeUpdate();
                    }
                }

                connection.commit();
            } catch (SQLException ex) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackEx) {
                    logger.log(Level.SEVERE, "Failed to rollback farm ranking transaction", rollbackEx);
                }
                throw ex;
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignore) {
                    // ignored
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Failed to record farm ranking progress", ex);
        }
    }

    public JsonObject topIslands(int limit) {
        ensureSchemaReady();

        JsonArray array = new JsonArray();
        String sql = "SELECT island_id, island_name, owner_uuid, owner_name, total_points, daily_points, weekly_points " +
                "FROM " + TABLE_RANKING + " ORDER BY total_points DESC LIMIT ?";

        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Math.max(1, limit));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("islandId", rs.getString("island_id"));
                    obj.addProperty("islandName", rs.getString("island_name"));
                    String ownerUuid = rs.getString("owner_uuid");
                    if (ownerUuid != null) {
                        obj.addProperty("ownerUuid", ownerUuid);
                    }
                    obj.addProperty("ownerName", rs.getString("owner_name"));
                    obj.addProperty("points", rs.getLong("total_points"));
                    obj.addProperty("dailyPoints", rs.getLong("daily_points"));
                    obj.addProperty("weeklyPoints", rs.getLong("weekly_points"));
                    array.add(obj);
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Failed to load farm ranking data", ex);
        }

        JsonObject root = new JsonObject();
        root.add("islands", array);
        return root;
    }

    public JsonObject islandPoints(UUID islandUuid) {
        ensureSchemaReady();

        JsonObject data = new JsonObject();
        data.addProperty("islandId", islandUuid.toString());

        long totalPoints = 0L;
        long dailyPoints = 0L;
        long weeklyPoints = 0L;
        java.sql.Timestamp updatedAt = null;

        String sql = "SELECT total_points, daily_points, weekly_points, updated_at " +
                "FROM " + TABLE_RANKING + " WHERE island_id = ?";

        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, islandUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    totalPoints = rs.getLong("total_points");
                    dailyPoints = rs.getLong("daily_points");
                    weeklyPoints = rs.getLong("weekly_points");
                    updatedAt = rs.getTimestamp("updated_at");
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to load farm points for island " + islandUuid, ex);
        }

        SuperiorSkyblockBridge.IslandDetails details = bridge.describeIsland(islandUuid);
        if (details != null) {
            data.addProperty("islandName", details.name());
            if (details.ownerUuid() != null) {
                data.addProperty("ownerUuid", details.ownerUuid().toString());
            }
            if (details.ownerName() != null) {
                data.addProperty("ownerName", details.ownerName());
            }
        }

        data.addProperty("totalPoints", totalPoints);
        data.addProperty("dailyPoints", dailyPoints);
        data.addProperty("weeklyPoints", weeklyPoints);
        if (updatedAt != null) {
            data.addProperty("updatedAt", updatedAt.getTime());
        }

        return data;
    }

    public JsonObject islandMembers(UUID islandUuid, int limit) {
        ensureSchemaReady();

        JsonArray array = new JsonArray();
        String sql = "SELECT player_uuid, player_name, contribution FROM " + TABLE_CONTRIBUTIONS +
                " WHERE island_id = ? ORDER BY contribution DESC LIMIT ?";

        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, islandUuid.toString());
            statement.setInt(2, Math.max(1, limit));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("playerUuid", rs.getString("player_uuid"));
                    obj.addProperty("playerName", rs.getString("player_name"));
                    obj.addProperty("points", rs.getLong("contribution"));
                    array.add(obj);
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Failed to load farm member ranking", ex);
        }

        JsonObject root = new JsonObject();
        root.add("members", array);
        return root;
    }

    public void snapshot(String periodId, String displayName, int limit) {
        ensureSchemaReady();

        JsonObject top = topIslands(limit);
        JsonArray islands = top.has("islands") ? top.getAsJsonArray("islands") : new JsonArray();

        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement insertPeriod = connection.prepareStatement(
                    "INSERT INTO " + TABLE_HISTORY_PERIODS + " (period_id, display_name) VALUES (?, ?) " +
                            "ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), created_at = CURRENT_TIMESTAMP")) {
                insertPeriod.setString(1, periodId);
                insertPeriod.setString(2, displayName != null ? displayName : periodId);
                insertPeriod.executeUpdate();
            }

            try (PreparedStatement deleteEntries = connection.prepareStatement(
                    "DELETE FROM " + TABLE_HISTORY_ENTRIES + " WHERE period_id = ?")) {
                deleteEntries.setString(1, periodId);
                deleteEntries.executeUpdate();
            }

            try (PreparedStatement insertEntry = connection.prepareStatement(
                    "INSERT INTO " + TABLE_HISTORY_ENTRIES + " (period_id, position, island_id, island_name, owner_uuid, owner_name, points, daily_points, weekly_points) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                int position = 1;
                for (var element : islands) {
                    JsonObject obj = element.getAsJsonObject();
                    insertEntry.setString(1, periodId);
                    insertEntry.setInt(2, position++);
                    insertEntry.setString(3, obj.get("islandId").getAsString());
                    insertEntry.setString(4, obj.has("islandName") ? obj.get("islandName").getAsString() : "");
                    if (obj.has("ownerUuid") && !obj.get("ownerUuid").isJsonNull()) {
                        insertEntry.setString(5, obj.get("ownerUuid").getAsString());
                    } else {
                        insertEntry.setNull(5, java.sql.Types.VARCHAR);
                    }
                    insertEntry.setString(6, obj.has("ownerName") ? obj.get("ownerName").getAsString() : "");
                    insertEntry.setLong(7, obj.has("points") ? obj.get("points").getAsLong() : 0L);
                    insertEntry.setLong(8, obj.has("dailyPoints") ? obj.get("dailyPoints").getAsLong() : 0L);
                    insertEntry.setLong(9, obj.has("weeklyPoints") ? obj.get("weeklyPoints").getAsLong() : 0L);
                    insertEntry.addBatch();
                }
                insertEntry.executeBatch();
            }

            connection.commit();
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Failed to persist farm ranking snapshot", ex);
        }
    }

    public JsonObject historyList(int page, int pageSize) {
        ensureSchemaReady();

        JsonArray array = new JsonArray();
        long total = 0;
        String listSql = "SELECT period_id, display_name, created_at, (SELECT COUNT(*) FROM " + TABLE_HISTORY_ENTRIES + " e WHERE e.period_id = p.period_id) AS entries " +
                "FROM " + TABLE_HISTORY_PERIODS + " p ORDER BY created_at DESC LIMIT ? OFFSET ?";
        String countSql = "SELECT COUNT(*) FROM " + TABLE_HISTORY_PERIODS;

        try (Connection connection = database.getConnection()) {
            try (PreparedStatement countStmt = connection.prepareStatement(countSql);
                 ResultSet rs = countStmt.executeQuery()) {
                if (rs.next()) {
                    total = rs.getLong(1);
                }
            }

            try (PreparedStatement stmt = connection.prepareStatement(listSql)) {
                stmt.setInt(1, pageSize);
                stmt.setInt(2, Math.max(0, (page - 1) * pageSize));
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        JsonObject obj = new JsonObject();
                        obj.addProperty("periodId", rs.getString("period_id"));
                        obj.addProperty("displayName", rs.getString("display_name"));
                        obj.addProperty("createdAt", rs.getTimestamp("created_at").getTime());
                        obj.addProperty("entries", rs.getInt("entries"));
                        array.add(obj);
                    }
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Failed to load farm history periods", ex);
        }

        JsonObject root = new JsonObject();
        root.add("periods", array);
        root.addProperty("total", total);
        return root;
    }

    public JsonObject historyDetail(String periodId) {
        ensureSchemaReady();

        JsonArray array = new JsonArray();
        String sql = "SELECT period_id, display_name, created_at FROM " + TABLE_HISTORY_PERIODS + " WHERE period_id = ?";
        String entriesSql = "SELECT position AS rank, island_id, island_name, owner_uuid, owner_name, points, daily_points, weekly_points FROM " + TABLE_HISTORY_ENTRIES +
                " WHERE period_id = ? ORDER BY position";

        JsonObject root = new JsonObject();
        try (Connection connection = database.getConnection()) {
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, periodId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        root.addProperty("periodId", rs.getString("period_id"));
                        root.addProperty("displayName", rs.getString("display_name"));
                        root.addProperty("createdAt", rs.getTimestamp("created_at").getTime());
                    }
                }
            }

            try (PreparedStatement stmt = connection.prepareStatement(entriesSql)) {
                stmt.setString(1, periodId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        JsonObject obj = new JsonObject();
                        obj.addProperty("rank", rs.getInt("rank"));
                        obj.addProperty("islandId", rs.getString("island_id"));
                        obj.addProperty("islandName", rs.getString("island_name"));
                        String ownerUuid = rs.getString("owner_uuid");
                        if (ownerUuid != null) {
                            obj.addProperty("ownerUuid", ownerUuid);
                        }
                        obj.addProperty("ownerName", rs.getString("owner_name"));
                        obj.addProperty("points", rs.getLong("points"));
                        obj.addProperty("dailyPoints", rs.getLong("daily_points"));
                        obj.addProperty("weeklyPoints", rs.getLong("weekly_points"));
                        array.add(obj);
                    }
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Failed to load farm history detail", ex);
        }

        root.add("entries", array);
        return root;
    }

    public void shutdown() {
        cancelSchemaRetry();
    }
}
