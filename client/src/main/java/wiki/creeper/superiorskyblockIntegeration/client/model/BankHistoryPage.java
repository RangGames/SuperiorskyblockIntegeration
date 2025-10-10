package wiki.creeper.superiorskyblockIntegeration.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a paged view of island bank transactions delivered by the gateway.
 */
public final class BankHistoryPage {

    private final String islandId;
    private final String islandName;
    private final int page;
    private final int pageSize;
    private final int total;
    private final List<Entry> entries;

    private BankHistoryPage(String islandId,
                            String islandName,
                            int page,
                            int pageSize,
                            int total,
                            List<Entry> entries) {
        this.islandId = islandId;
        this.islandName = islandName;
        this.page = page;
        this.pageSize = pageSize;
        this.total = total;
        this.entries = entries;
    }

    public static BankHistoryPage from(JsonObject json) {
        if (json == null) {
            return new BankHistoryPage(null, null, 1, 10, 0, Collections.emptyList());
        }
        String islandId = getString(json, "islandId");
        String islandName = getString(json, "islandName");
        int page = getInt(json, "page", 1);
        int pageSize = getInt(json, "pageSize", 10);
        int total = getInt(json, "total", 0);
        List<Entry> entries = parseEntries(json);
        return new BankHistoryPage(islandId, islandName, Math.max(page, 1), Math.max(pageSize, 1), Math.max(total, 0), entries);
    }

    private static List<Entry> parseEntries(JsonObject json) {
        if (!json.has("transactions") || !json.get("transactions").isJsonArray()) {
            return Collections.emptyList();
        }
        JsonArray array = json.getAsJsonArray("transactions");
        List<Entry> entries = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject obj = element.getAsJsonObject();
            String playerUuid = getString(obj, "playerUuid");
            String playerName = getString(obj, "playerName");
            BigDecimal amount = getDecimal(obj, "amount");
            String action = getString(obj, "action");
            long time = getLong(obj, "time");
            String failure = getString(obj, "failureReason");
            int position = getInt(obj, "position", -1);
            entries.add(new Entry(playerUuid, playerName, amount, action, time, failure, position));
        }
        return Collections.unmodifiableList(entries);
    }

    private static String getString(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull()
                ? json.get(key).getAsString()
                : null;
    }

    private static int getInt(JsonObject json, String key, int fallback) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return json.get(key).getAsInt();
        } catch (Exception ignored) {
            try {
                return Integer.parseInt(json.get(key).getAsString());
            } catch (Exception ignoredAgain) {
                return fallback;
            }
        }
    }

    private static long getLong(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            return 0L;
        }
        try {
            return json.get(key).getAsLong();
        } catch (Exception ignored) {
            try {
                return Long.parseLong(json.get(key).getAsString());
            } catch (Exception ignoredAgain) {
                return 0L;
            }
        }
    }

    private static BigDecimal getDecimal(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(json.get(key).getAsString());
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    public String islandId() {
        return islandId;
    }

    public String islandName() {
        return islandName;
    }

    public int page() {
        return page;
    }

    public int pageSize() {
        return pageSize;
    }

    public int total() {
        return total;
    }

    public List<Entry> entries() {
        return entries;
    }

    public int totalPages() {
        if (pageSize <= 0) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil(total / (double) pageSize));
    }

    public boolean hasNext() {
        return page < totalPages();
    }

    public boolean hasPrevious() {
        return page > 1;
    }

    public record Entry(String playerUuid,
                        String playerName,
                        BigDecimal amount,
                        String action,
                        long time,
                        String failureReason,
                        int position) {
    }
}
