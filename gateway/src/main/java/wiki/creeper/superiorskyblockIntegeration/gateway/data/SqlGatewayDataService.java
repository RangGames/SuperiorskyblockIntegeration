package wiki.creeper.superiorskyblockIntegeration.gateway.data;

import com.google.gson.JsonObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import wiki.creeper.superiorskyblockIntegeration.common.model.PlayerProfile;
import wiki.creeper.superiorskyblockIntegeration.common.quest.IslandQuestData;
import wiki.creeper.superiorskyblockIntegeration.redis.RedisCodec;

/**
 * SQL-backed implementation of {@link GatewayDataService} using the shared gateway database.
 */
public final class SqlGatewayDataService implements GatewayDataService {

    private static final String TABLE_DATA = "ssb_data_entries";
    private static final String TABLE_PROFILES = "ssb_player_profiles";
    private static final String TABLE_QUESTS = "ssb_island_quests";

    private final GatewayDatabase database;
    private final Logger logger;

    public SqlGatewayDataService(GatewayDatabase database, Logger logger) {
        this.database = Objects.requireNonNull(database, "database");
        this.logger = Objects.requireNonNull(logger, "logger");
        initializeSchema();
        cleanupExpiredData();
    }

    @Override
    public void setData(String namespace, String key, String value, Duration ttl) {
        if (namespace == null || namespace.isBlank() || key == null || key.isBlank()) {
            return;
        }
        if (value == null) {
            deleteData(namespace, key);
            return;
        }

        Long expiresAt = computeExpiry(ttl);
        String sql = "INSERT INTO " + TABLE_DATA + " (namespace, data_key, value, expires_at) " +
                "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE value = VALUES(value), expires_at = VALUES(expires_at), updated_at = CURRENT_TIMESTAMP";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, namespace);
            statement.setString(2, key);
            statement.setString(3, value);
            if (expiresAt != null) {
                statement.setLong(4, expiresAt);
            } else {
                statement.setNull(4, Types.BIGINT);
            }
            statement.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to store data entry {0}:{1}", new Object[]{namespace, key});
            logger.log(Level.FINE, "SQL error", ex);
        }
    }

    @Override
    public Optional<String> getData(String namespace, String key) {
        if (namespace == null || namespace.isBlank() || key == null || key.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT value, expires_at FROM " + TABLE_DATA + " WHERE namespace = ? AND data_key = ?";
        long now = System.currentTimeMillis();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, namespace);
            statement.setString(2, key);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                long expiresAt = result.getLong("expires_at");
                boolean hasExpiry = !result.wasNull();
                if (hasExpiry && expiresAt > 0 && expiresAt <= now) {
                    deleteData(namespace, key);
                    return Optional.empty();
                }
                return Optional.ofNullable(result.getString("value"));
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to read data entry {0}:{1}", new Object[]{namespace, key});
            logger.log(Level.FINE, "SQL error", ex);
            return Optional.empty();
        }
    }

    @Override
    public void deleteData(String namespace, String key) {
        if (namespace == null || namespace.isBlank() || key == null || key.isBlank()) {
            return;
        }
        String sql = "DELETE FROM " + TABLE_DATA + " WHERE namespace = ? AND data_key = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, namespace);
            statement.setString(2, key);
            statement.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to delete data entry {0}:{1}", new Object[]{namespace, key});
            logger.log(Level.FINE, "SQL error", ex);
        }
    }

    @Override
    public void setPlayerProfile(PlayerProfile profile) {
        if (profile == null) {
            return;
        }
        JsonObject json = profile.toJson();
        String sql = "INSERT INTO " + TABLE_PROFILES + " (uuid, name, payload) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE name = VALUES(name), payload = VALUES(payload), updated_at = CURRENT_TIMESTAMP";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, profile.uuid());
            statement.setString(2, profile.name());
            statement.setString(3, json.toString());
            statement.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to upsert player profile for {0}", profile.uuid());
            logger.log(Level.FINE, "SQL error", ex);
        }
    }

    @Override
    public Optional<PlayerProfile> findProfileByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT payload FROM " + TABLE_PROFILES + " WHERE LOWER(name) = LOWER(?)";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                String payload = result.getString("payload");
                if (payload == null || payload.isBlank()) {
                    return Optional.empty();
                }
                JsonObject json = RedisCodec.gson().fromJson(payload, JsonObject.class);
                return Optional.of(PlayerProfile.fromJson(json));
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to lookup player profile by name {0}", name);
            logger.log(Level.FINE, "SQL error", ex);
            return Optional.empty();
        }
    }

    @Override
    public Optional<PlayerProfile> findProfileByUuid(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT payload FROM " + TABLE_PROFILES + " WHERE uuid = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                String payload = result.getString("payload");
                if (payload == null || payload.isBlank()) {
                    return Optional.empty();
                }
                JsonObject json = RedisCodec.gson().fromJson(payload, JsonObject.class);
                return Optional.of(PlayerProfile.fromJson(json));
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to lookup player profile by uuid {0}", uuid);
            logger.log(Level.FINE, "SQL error", ex);
            return Optional.empty();
        }
    }

    @Override
    public JsonObject toJson(PlayerProfile profile) {
        return profile.toJson();
    }

    @Override
    public Optional<IslandQuestData> loadIslandQuests(String islandUuid) {
        if (islandUuid == null || islandUuid.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT payload FROM " + TABLE_QUESTS + " WHERE island_uuid = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, islandUuid);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                String payload = result.getString("payload");
                if (payload == null || payload.isBlank()) {
                    return Optional.empty();
                }
                JsonObject json = RedisCodec.gson().fromJson(payload, JsonObject.class);
                return Optional.of(IslandQuestData.fromJson(json));
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to load island quest data for {0}", islandUuid);
            logger.log(Level.FINE, "SQL error", ex);
            return Optional.empty();
        }
    }

    @Override
    public void saveIslandQuests(IslandQuestData quests) {
        if (quests == null) {
            return;
        }
        String sql = "INSERT INTO " + TABLE_QUESTS + " (island_uuid, payload) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE payload = VALUES(payload), updated_at = CURRENT_TIMESTAMP";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, quests.islandUuid());
            statement.setString(2, quests.toJson().toString());
            statement.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to save island quest data for {0}", quests.islandUuid());
            logger.log(Level.FINE, "SQL error", ex);
        }
    }

    @Override
    public void deleteIslandQuests(String islandUuid) {
        if (islandUuid == null || islandUuid.isBlank()) {
            return;
        }
        String sql = "DELETE FROM " + TABLE_QUESTS + " WHERE island_uuid = ?";
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, islandUuid);
            statement.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to delete island quest data for {0}", islandUuid);
            logger.log(Level.FINE, "SQL error", ex);
        }
    }

    private void initializeSchema() {
        try (Connection connection = database.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS ssb_data_entries (
                        namespace VARCHAR(64) NOT NULL,
                        data_key VARCHAR(128) NOT NULL,
                        value TEXT NOT NULL,
                        expires_at BIGINT NULL,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        PRIMARY KEY (namespace, data_key),
                        INDEX idx_ssb_data_expiry (expires_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS ssb_player_profiles (
                        uuid VARCHAR(36) NOT NULL,
                        name VARCHAR(64) NOT NULL,
                        payload LONGTEXT NOT NULL,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        PRIMARY KEY (uuid),
                        UNIQUE KEY uq_ssb_player_profiles_name (name)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS ssb_island_quests (
                        island_uuid VARCHAR(36) NOT NULL,
                        payload LONGTEXT NOT NULL,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        PRIMARY KEY (island_uuid)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Failed to prepare gateway data schema", ex);
        }
    }

    private void cleanupExpiredData() {
        String sql = "DELETE FROM " + TABLE_DATA + " WHERE expires_at IS NOT NULL AND expires_at <= ?";
        long now = System.currentTimeMillis();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, now);
            statement.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.FINE, "Failed to clean up expired data entries", ex);
        }
    }

    private Long computeExpiry(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return null;
        }
        long now = System.currentTimeMillis();
        long delta = ttl.toMillis();
        long expiresAt = now + delta;
        if (expiresAt <= 0L) {
            return null;
        }
        return expiresAt;
    }
}
