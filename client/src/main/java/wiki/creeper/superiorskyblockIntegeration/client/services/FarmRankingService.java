package wiki.creeper.superiorskyblockIntegeration.client.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import wiki.creeper.superiorskyblockIntegeration.api.NetworkOperationResult;
import wiki.creeper.superiorskyblockIntegeration.api.NetworkSkyblockService;

/**
 * Provides convenience wrappers for farm ranking lookups.
 */
public final class FarmRankingService {

    public record IslandEntry(String islandId,
                              String islandName,
                              String ownerUuid,
                              String ownerName,
                              long points,
                              long dailyPoints,
                              long weeklyPoints) {
    }

    public record MemberEntry(String playerUuid,
                              String playerName,
                              long points) {
    }

    private final NetworkSkyblockService network;

    public FarmRankingService(NetworkSkyblockService network) {
        this.network = Objects.requireNonNull(network, "network");
    }

    public CompletableFuture<Result<List<IslandEntry>>> fetchTop(Player actor) {
        return fetchTop(actor, 10);
    }

    public CompletableFuture<Result<List<IslandEntry>>> fetchTop(Player actor, int limit) {
        return network.farmRankingTop(actor, limit).thenApply(result -> {
            if (result.failed()) {
                return Result.<List<IslandEntry>>failure(result);
            }
            return Result.success(parseIslands(result.data()));
        }).exceptionally(ex -> Result.<List<IslandEntry>>error(ex.getMessage()));
    }

    public CompletableFuture<Result<List<MemberEntry>>> fetchMembers(Player actor, String islandId) {
        return fetchMembers(actor, islandId, 25);
    }

    public CompletableFuture<Result<List<MemberEntry>>> fetchMembers(Player actor, String islandId, int limit) {
        return network.farmRankingMembers(actor, islandId, limit).thenApply(result -> {
            if (result.failed()) {
                return Result.<List<MemberEntry>>failure(result);
            }
            return Result.success(parseMembers(result.data()));
        }).exceptionally(ex -> Result.<List<MemberEntry>>error(ex.getMessage()));
    }

    private List<IslandEntry> parseIslands(JsonObject data) {
        if (data == null || !data.has("islands") || !data.get("islands").isJsonArray()) {
            return Collections.emptyList();
        }
        List<IslandEntry> islands = new ArrayList<>();
        JsonArray array = data.getAsJsonArray("islands");
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject obj = element.getAsJsonObject();
            String islandId = getString(obj, "islandId");
            if (islandId == null) {
                continue;
            }
            String islandName = getString(obj, "islandName");
            String ownerUuid = getString(obj, "ownerUuid");
            String ownerName = getString(obj, "ownerName");
            long points = obj.has("points") ? obj.get("points").getAsLong() : 0L;
            long daily = obj.has("dailyPoints") ? obj.get("dailyPoints").getAsLong() : 0L;
            long weekly = obj.has("weeklyPoints") ? obj.get("weeklyPoints").getAsLong() : 0L;
            islands.add(new IslandEntry(islandId, islandName, ownerUuid, ownerName, points, daily, weekly));
        }
        return islands;
    }

    private List<MemberEntry> parseMembers(JsonObject data) {
        if (data == null || !data.has("members") || !data.get("members").isJsonArray()) {
            return Collections.emptyList();
        }
        List<MemberEntry> members = new ArrayList<>();
        JsonArray array = data.getAsJsonArray("members");
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject obj = element.getAsJsonObject();
            String playerUuid = getString(obj, "playerUuid");
            if (playerUuid == null) {
                continue;
            }
            String playerName = getString(obj, "playerName");
            long points = obj.has("points") ? obj.get("points").getAsLong() : 0L;
            members.add(new MemberEntry(playerUuid, playerName, points));
        }
        return members;
    }

    private String getString(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : null;
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
