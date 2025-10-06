package wiki.creeper.superiorskyblockIntegeration.client.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import wiki.creeper.superiorskyblockIntegeration.api.NetworkOperationResult;
import wiki.creeper.superiorskyblockIntegeration.api.NetworkSkyblockService;

/**
 * Fetches farm ranking reward tiers from the gateway and caches them for reuse.
 */
public final class FarmRewardService {

    private final NetworkSkyblockService networkService;
    private final AtomicReference<List<RewardTier>> cache = new AtomicReference<>();

    public FarmRewardService(NetworkSkyblockService networkService) {
        this.networkService = networkService;
    }

    public CompletableFuture<List<RewardTier>> fetch(Player player) {
        List<RewardTier> current = cache.get();
        if (current != null) {
            return CompletableFuture.completedFuture(current);
        }
        return networkService.farmRewardTable(player)
                .thenApply(result -> {
                    if (result.failed()) {
                        throw new IllegalStateException("Failed to load farm rewards: " + result.errorCode());
                    }
                    List<RewardTier> tiers = parse(result.data());
                    cache.set(tiers);
                    return tiers;
                });
    }

    private List<RewardTier> parse(JsonObject data) {
        if (data == null || !data.has("rewards") || !data.get("rewards").isJsonArray()) {
            return Collections.emptyList();
        }
        JsonArray array = data.getAsJsonArray("rewards");
        List<RewardTier> tiers = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            int minRank = entry.has("minRank") ? entry.get("minRank").getAsInt() : 1;
            int maxRank = entry.has("maxRank") ? entry.get("maxRank").getAsInt() : minRank;
            String title = entry.has("title") ? entry.get("title").getAsString() : "&f랭킹 보상";
            String icon = entry.has("icon") ? entry.get("icon").getAsString() : "CHEST";
            int moonlight = entry.has("moonlight") ? entry.get("moonlight").getAsInt() : 0;
            int farmPoints = entry.has("farmPoints") ? entry.get("farmPoints").getAsInt() : 0;
            List<String> lore = new ArrayList<>();
            if (entry.has("lore") && entry.get("lore").isJsonArray()) {
                for (JsonElement line : entry.getAsJsonArray("lore")) {
                    lore.add(line.getAsString());
                }
            }
            tiers.add(new RewardTier(minRank, maxRank, title, icon, moonlight, farmPoints, Collections.unmodifiableList(lore)));
        }
        return Collections.unmodifiableList(tiers);
    }

    public record RewardTier(int minRank,
                             int maxRank,
                             String title,
                             String icon,
                             int moonlight,
                             int farmPoints,
                             List<String> lore) { }
}
