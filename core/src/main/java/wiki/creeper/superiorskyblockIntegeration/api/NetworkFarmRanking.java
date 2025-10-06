package wiki.creeper.superiorskyblockIntegeration.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class NetworkFarmRanking {

    private NetworkFarmRanking() {
    }

    public record IslandEntry(String islandId,
                              String islandName,
                              String ownerUuid,
                              String ownerName,
                              long points,
                              long dailyPoints,
                              long weeklyPoints) {
    }

    public record MemberEntry(String islandId,
                              String playerUuid,
                              String playerName,
                              long points) {
    }

    public static CompletableFuture<Result<List<IslandEntry>>> top(Player actor, int limit) {
        return NetworkSkyblockAPI.service()
                .map(service -> service.farmRankingTop(actor, limit)
                        .thenApply(NetworkFarmRanking::toIslandResult))
                .orElseGet(() -> CompletableFuture.completedFuture(Result.<List<IslandEntry>>error("NetworkSkyblockService unavailable")));
    }

    public static CompletableFuture<Result<List<IslandEntry>>> top(int limit) {
        return top(null, limit);
    }

    public static CompletableFuture<Result<List<MemberEntry>>> members(Player actor, String islandId, int limit) {
        if (islandId == null || islandId.isBlank()) {
            return CompletableFuture.completedFuture(Result.<List<MemberEntry>>error("islandId is required"));
        }
        return NetworkSkyblockAPI.service()
                .map(service -> service.farmRankingMembers(actor, islandId, limit)
                        .thenApply(result -> {
                            if (result.failed()) {
                                return Result.<List<MemberEntry>>failure(result);
                            }
                            return Result.<List<MemberEntry>>success(parseMembers(islandId, result.data()));
                        }))
                .orElseGet(() -> CompletableFuture.completedFuture(Result.<List<MemberEntry>>error("NetworkSkyblockService unavailable")));
    }

    public static CompletableFuture<Result<List<MemberEntry>>> members(String islandId, int limit) {
        return members(null, islandId, limit);
    }

    public static CompletableFuture<Result<List<MemberEntry>>> members(Player actor, UUID islandUuid, int limit) {
        return members(actor, islandUuid != null ? islandUuid.toString() : null, limit);
    }

    public static CompletableFuture<Result<List<MemberEntry>>> members(UUID islandUuid, int limit) {
        return members(null, islandUuid, limit);
    }

    public static CompletableFuture<NetworkOperationResult> increment(Player actor,
                                                                      UUID islandUuid,
                                                                      long totalIncrement,
                                                                      long dailyIncrement,
                                                                      long weeklyIncrement,
                                                                      UUID contributorUuid) {
        if (islandUuid == null) {
            return CompletableFuture.completedFuture(
                    NetworkOperationResult.failure("BAD_REQUEST", "islandUuid is required", false));
        }
        return NetworkSkyblockAPI.service()
                .map(service -> service.farmRankingIncrement(actor, islandUuid, totalIncrement, dailyIncrement, weeklyIncrement, contributorUuid))
                .orElseGet(() -> CompletableFuture.completedFuture(
                        NetworkOperationResult.failure("SERVICE_UNAVAILABLE", "NetworkSkyblockService not registered", false)));
    }

    public static CompletableFuture<NetworkOperationResult> increment(UUID islandUuid,
                                                                      long totalIncrement,
                                                                      long dailyIncrement,
                                                                      long weeklyIncrement,
                                                                      UUID contributorUuid) {
        return increment(null, islandUuid, totalIncrement, dailyIncrement, weeklyIncrement, contributorUuid);
    }

    private static Result<List<IslandEntry>> toIslandResult(NetworkOperationResult result) {
        if (result.failed()) {
            return Result.<List<IslandEntry>>failure(result);
        }
        return Result.<List<IslandEntry>>success(parseIslands(result.data()));
    }

    private static List<IslandEntry> parseIslands(JsonObject data) {
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

    private static List<MemberEntry> parseMembers(String islandId, JsonObject data) {
        if (data == null || !data.has("members") || !data.get("members").isJsonArray()) {
            return Collections.emptyList();
        }
        String resolvedIslandId = islandId;
        if ((resolvedIslandId == null || resolvedIslandId.isBlank()) && data.has("islandId")) {
            resolvedIslandId = getString(data, "islandId");
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
            members.add(new MemberEntry(resolvedIslandId, playerUuid, playerName, points));
        }
        return members;
    }

    private static String getString(JsonObject obj, String key) {
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
            return data != null && operationResult == null && errorMessage == null;
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
