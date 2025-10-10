package wiki.creeper.superiorskyblockIntegeration.client.model;

import com.google.gson.JsonObject;

import java.math.BigDecimal;

/**
 * Immutable representation of the island bank state returned by the gateway.
 */
public final class BankSnapshot {

    private final String islandId;
    private final String islandName;
    private final BigDecimal balance;
    private final BigDecimal limit;
    private final boolean locked;
    private final boolean canManageLock;

    private BankSnapshot(String islandId,
                         String islandName,
                         BigDecimal balance,
                         BigDecimal limit,
                         boolean locked,
                         boolean canManageLock) {
        this.islandId = islandId;
        this.islandName = islandName;
        this.balance = balance;
        this.limit = limit;
        this.locked = locked;
        this.canManageLock = canManageLock;
    }

    public static BankSnapshot from(JsonObject json) {
        if (json == null) {
            return new BankSnapshot(null, null, BigDecimal.ZERO, null, false, false);
        }
        String islandId = value(json, "islandId");
        String islandName = value(json, "islandName");
        BigDecimal balance = decimal(json, "balance", BigDecimal.ZERO);
        BigDecimal limit = json.has("limit") && !json.get("limit").isJsonNull()
                ? decimal(json, "limit", null)
                : null;
        boolean locked = json.has("locked") && !json.get("locked").isJsonNull() && getBoolean(json, "locked");
        boolean canManageLock = json.has("canLock") && !json.get("canLock").isJsonNull() && getBoolean(json, "canLock");
        return new BankSnapshot(islandId, islandName, balance, limit, locked, canManageLock);
    }

    private static String value(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : null;
    }

    private static BigDecimal decimal(JsonObject json, String key, BigDecimal fallback) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return new BigDecimal(json.get(key).getAsString());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public String islandId() {
        return islandId;
    }

    public String islandName() {
        return islandName;
    }

    public BigDecimal balance() {
        return balance;
    }

    public BigDecimal limit() {
        return limit;
    }

    public boolean hasLimit() {
        return limit != null;
    }

    public boolean locked() {
        return locked;
    }

    public boolean canManageLock() {
        return canManageLock;
    }

    private static boolean getBoolean(JsonObject json, String key) {
        try {
            return json.get(key).getAsBoolean();
        } catch (Exception ex) {
            return false;
        }
    }
}
