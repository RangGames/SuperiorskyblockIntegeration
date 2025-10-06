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
 * Fetches farm shop items from the gateway.
 */
public final class FarmShopService {

    private final NetworkSkyblockService networkService;
    private final AtomicReference<List<ShopItem>> cache = new AtomicReference<>();

    public FarmShopService(NetworkSkyblockService networkService) {
        this.networkService = networkService;
    }

    public CompletableFuture<List<ShopItem>> fetch(Player player) {
        List<ShopItem> current = cache.get();
        if (current != null) {
            return CompletableFuture.completedFuture(current);
        }
        return networkService.farmShopTable(player)
                .thenApply(result -> {
                    if (result.failed()) {
                        throw new IllegalStateException("Failed to load farm shop table: " + result.errorCode());
                    }
                    List<ShopItem> items = parse(result.data());
                    cache.set(items);
                    return items;
                });
    }

    private List<ShopItem> parse(JsonObject data) {
        if (data == null || !data.has("items") || !data.get("items").isJsonArray()) {
            return Collections.emptyList();
        }
        JsonArray array = data.getAsJsonArray("items");
        List<ShopItem> items = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            int slot = entry.has("slot") ? entry.get("slot").getAsInt() : -1;
            String title = entry.has("title") ? entry.get("title").getAsString() : "&f아이템";
            String icon = entry.has("icon") ? entry.get("icon").getAsString() : "BARRIER";
            String currency = entry.has("currency") ? entry.get("currency").getAsString() : "none";
            int price = entry.has("price") ? entry.get("price").getAsInt() : 0;
            String command = entry.has("command") ? entry.get("command").getAsString() : "";
            boolean enabled = !entry.has("enabled") || entry.get("enabled").getAsBoolean();
            List<String> lore = new ArrayList<>();
            if (entry.has("lore") && entry.get("lore").isJsonArray()) {
                for (JsonElement line : entry.getAsJsonArray("lore")) {
                    lore.add(line.getAsString());
                }
            }
            items.add(new ShopItem(slot, title, icon, currency, price, Collections.unmodifiableList(lore), command, enabled));
        }
        return Collections.unmodifiableList(items);
    }

    public record ShopItem(int slot,
                           String title,
                           String icon,
                           String currency,
                           int price,
                           List<String> lore,
                           String command,
                           boolean enabled) { }
}

