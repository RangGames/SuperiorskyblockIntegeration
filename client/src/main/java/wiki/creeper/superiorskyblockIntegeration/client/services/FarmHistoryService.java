package wiki.creeper.superiorskyblockIntegeration.client.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import wiki.creeper.superiorskyblockIntegeration.api.NetworkOperationResult;
import wiki.creeper.superiorskyblockIntegeration.api.NetworkSkyblockService;

/**
 * Provides farm history snapshots for GUI consumption.
 */
public final class FarmHistoryService {

    public record Period(String periodId,
                         String displayName,
                         long createdAt,
                         int entries) {
    }

    public record Entry(int rank,
                        String islandId,
                        String islandName,
                        String ownerUuid,
                        String ownerName,
                        long points,
                        long dailyPoints,
                        long weeklyPoints) {
    }

    public record HistoryDetail(Period period, List<Entry> entries) {
    }

    private final NetworkSkyblockService network;

    public FarmHistoryService(NetworkSkyblockService network) {
        this.network = network;
    }

    public CompletableFuture<Result<List<Period>>> list(Player actor, int page, int pageSize) {
        return network.farmHistoryList(actor, page, pageSize).thenApply(result -> {
            if (result.failed()) {
                return Result.<List<Period>>failure(result);
            }
            return Result.success(parsePeriods(result.data()));
        }).exceptionally(ex -> Result.<List<Period>>error(ex.getMessage()));
    }

    public CompletableFuture<Result<HistoryDetail>> detail(Player actor, String periodId) {
        return network.farmHistoryDetail(actor, periodId).thenApply(result -> {
            if (result.failed()) {
                return Result.<HistoryDetail>failure(result);
            }
            return Result.success(parseDetail(result.data()));
        }).exceptionally(ex -> Result.<HistoryDetail>error(ex.getMessage()));
    }

    private List<Period> parsePeriods(JsonObject data) {
        if (data == null || !data.has("periods") || !data.get("periods").isJsonArray()) {
            return Collections.emptyList();
        }
        List<Period> periods = new ArrayList<>();
        JsonArray array = data.getAsJsonArray("periods");
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject obj = element.getAsJsonObject();
            periods.add(new Period(
                    obj.has("periodId") ? obj.get("periodId").getAsString() : "",
                    obj.has("displayName") ? obj.get("displayName").getAsString() : "",
                    obj.has("createdAt") ? obj.get("createdAt").getAsLong() : 0L,
                    obj.has("entries") ? obj.get("entries").getAsInt() : 0
            ));
        }
        return periods;
    }

    private HistoryDetail parseDetail(JsonObject data) {
        Period period = new Period(
                data.has("periodId") ? data.get("periodId").getAsString() : "",
                data.has("displayName") ? data.get("displayName").getAsString() : "",
                data.has("createdAt") ? data.get("createdAt").getAsLong() : 0L,
                data.has("entries") && data.get("entries").isJsonArray() ? data.getAsJsonArray("entries").size() : 0
        );
        List<Entry> entries = new ArrayList<>();
        if (data.has("entries") && data.get("entries").isJsonArray()) {
            for (JsonElement element : data.getAsJsonArray("entries")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject obj = element.getAsJsonObject();
                entries.add(new Entry(
                        obj.has("rank") ? obj.get("rank").getAsInt() : 0,
                        obj.has("islandId") ? obj.get("islandId").getAsString() : "",
                        obj.has("islandName") ? obj.get("islandName").getAsString() : "",
                        obj.has("ownerUuid") && !obj.get("ownerUuid").isJsonNull() ? obj.get("ownerUuid").getAsString() : null,
                        obj.has("ownerName") ? obj.get("ownerName").getAsString() : "",
                        obj.has("points") ? obj.get("points").getAsLong() : 0L,
                        obj.has("dailyPoints") ? obj.get("dailyPoints").getAsLong() : 0L,
                        obj.has("weeklyPoints") ? obj.get("weeklyPoints").getAsLong() : 0L
                ));
            }
        }
        return new HistoryDetail(period, entries);
    }

    public static final class Result<T> {
        private final T data;
        private final NetworkOperationResult operationResult;
        private final String errorMessage;

        private Result(T data, NetworkOperationResult operationResult, String errorMessage) {
            this.data = data;
            this.operationResult = operationResult;
            this.errorMessage = errorMessage;
        }

        public static <T> Result<T> success(T data) {
            return new Result<>(data, null, null);
        }

        public static <T> Result<T> failure(NetworkOperationResult result) {
            return new Result<>(null, result, null);
        }

        public static <T> Result<T> error(String message) {
            return new Result<>(null, null, message);
        }

        public boolean successful() {
            return data != null && errorMessage == null && operationResult == null;
        }

        public T data() {
            return data;
        }

        public NetworkOperationResult operationResult() {
            return operationResult;
        }

        public String errorMessage() {
            return errorMessage;
        }
    }
}
