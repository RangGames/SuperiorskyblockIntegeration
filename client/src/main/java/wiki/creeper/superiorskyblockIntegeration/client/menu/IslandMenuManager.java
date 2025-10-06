package wiki.creeper.superiorskyblockIntegeration.client.menu;

import com.google.gson.JsonObject;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import wiki.creeper.superiorskyblockIntegeration.api.NetworkOperationResult;
import wiki.creeper.superiorskyblockIntegeration.api.NetworkSkyblockService;
import wiki.creeper.superiorskyblockIntegeration.api.PlayerMetadataService;
import wiki.creeper.superiorskyblockIntegeration.client.cache.ClientCache;
import wiki.creeper.superiorskyblockIntegeration.client.services.FarmHistoryService;
import wiki.creeper.superiorskyblockIntegeration.client.services.FarmRankingService;
import wiki.creeper.superiorskyblockIntegeration.client.services.FarmRewardService;
import wiki.creeper.superiorskyblockIntegeration.client.services.FarmShopService;
import wiki.creeper.superiorskyblockIntegeration.client.services.QuestProgressService;
import wiki.creeper.superiorskyblockIntegeration.common.quest.QuestType;

/**
 * Centralises island menu lifecycle and event dispatching.
 */
public final class IslandMenuManager implements Listener {

    private final JavaPlugin plugin;
    private final NetworkSkyblockService networkService;
    private final ClientCache cache;
    private QuestProgressService questProgressService;
    private FarmRankingService farmRankingService;
    private FarmHistoryService farmHistoryService;
    private FarmRewardService farmRewardService;
    private FarmShopService farmShopService;
    private PlayerMetadataService metadataService;
    private final Map<UUID, AbstractMenu> openMenus = new ConcurrentHashMap<>();
    private final Map<UUID, InvitePrompt> invitePrompts = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> historyPages = new ConcurrentHashMap<>();

    public IslandMenuManager(JavaPlugin plugin,
                             NetworkSkyblockService networkService,
                             ClientCache cache) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.networkService = Objects.requireNonNull(networkService, "networkService");
        this.cache = Objects.requireNonNull(cache, "cache");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public NetworkSkyblockService network() {
        return networkService;
    }

    public ClientCache cache() {
        return cache;
    }

    public void openMenu(Player player, AbstractMenu menu) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(menu, "menu");
        openMenus.put(player.getUniqueId(), menu);
        menu.open(player);
    }

    public void openMainMenu(Player player) {
        openMenu(player, new MainMenu(this));
    }

    public void openQuestHub(Player player) {
        fetchQuestState(player, data -> openMenu(player, new QuestHubMenu(this, data)));
    }

    public void openFarmRanking(Player player) {
        if (farmRankingService == null) {
            player.sendMessage(ChatColor.RED + "[Skyblock] 순위 서비스를 사용할 수 없습니다.");
            return;
        }
        farmRankingService.fetchTop(player).thenAccept(result -> {
            if (!result.successful()) {
                handleRankingFailure(player, "순위 정보를 불러오지 못했습니다", result);
                return;
            }
            runSync(() -> openMenu(player, new FarmRankingMenu(this, result.data())));
        }).exceptionally(ex -> {
            plugin().getLogger().warning("Failed to fetch farm ranking for " + player.getName() + ": " + ex.getMessage());
            runSync(() -> player.sendMessage(ChatColor.RED + "[Skyblock] 내부 오류로 순위 정보를 불러오지 못했습니다."));
            return null;
        });
    }

    public void openFarmMemberRanking(Player player, String islandId) {
        if (farmRankingService == null) {
            player.sendMessage(ChatColor.RED + "[Skyblock] 순위 서비스를 사용할 수 없습니다.");
            return;
        }
        farmRankingService.fetchMembers(player, islandId).thenAccept(result -> {
            if (!result.successful()) {
                handleRankingFailure(player, "기여도 정보를 불러오지 못했습니다", result);
                return;
            }
            runSync(() -> openMenu(player, new FarmMemberRankingMenu(this, islandId, result.data())));
        }).exceptionally(ex -> {
            plugin().getLogger().warning("Failed to fetch member ranking for " + player.getName() + ": " + ex.getMessage());
            runSync(() -> player.sendMessage(ChatColor.RED + "[Skyblock] 내부 오류로 기여도 정보를 불러오지 못했습니다."));
            return null;
        });
    }

    public void openFarmHistory(Player player, int page) {
        historyPages.put(player.getUniqueId(), page);
        if (farmHistoryService == null) {
            player.sendMessage(ChatColor.RED + "[Skyblock] 히스토리 서비스를 사용할 수 없습니다.");
            return;
        }
        int pageSize = 12;
        farmHistoryService.list(player, page, pageSize).thenAccept(result -> {
            if (!result.successful()) {
                handleHistoryFailure(player, "히스토리 정보를 불러오지 못했습니다", result);
                return;
            }
            List<FarmHistoryService.Period> data = result.data();
            if ((data == null || data.isEmpty()) && page > 1) {
                openFarmHistory(player, page - 1);
                return;
            }
            runSync(() -> openMenu(player, new FarmHistoryMenu(this, page, data)));
        }).exceptionally(ex -> {
            plugin().getLogger().warning("Failed to fetch farm history for " + player.getName() + ": " + ex.getMessage());
            runSync(() -> player.sendMessage(ChatColor.RED + "[Skyblock] 내부 오류로 히스토리 정보를 불러오지 못했습니다."));
            return null;
        });
    }

    public void openMembersMenu(Player player) {
        runSync(() -> openMenu(player, new MembersMenu(this)));
    }

    public void openPendingInvites(Player player) {
        runSync(() -> openMenu(player, new PendingInvitesMenu(this)));
    }

    public void openFarmRewards(Player player) {
        if (farmRewardService == null) {
            player.sendMessage(ChatColor.RED + "[Skyblock] 보상 정보를 사용할 수 없습니다.");
            return;
        }
        farmRewardService.fetch(player).thenAccept(rewards ->
                runSync(() -> openMenu(player, new FarmRewardMenu(this, rewards))))
                .exceptionally(ex -> {
                    plugin().getLogger().warning("Failed to fetch farm rewards for " + player.getName() + ": " + ex.getMessage());
                    runSync(() -> player.sendMessage(ChatColor.RED + "[Skyblock] 보상 정보를 불러오지 못했습니다."));
                    return null;
                });
    }

    public void openFarmShop(Player player) {
        if (farmShopService == null) {
            player.sendMessage(ChatColor.RED + "[Skyblock] 상점 서비스를 사용할 수 없습니다.");
            return;
        }
        farmShopService.fetch(player).thenAccept(items ->
                runSync(() -> openMenu(player, new FarmShopMenu(this, items))))
                .exceptionally(ex -> {
                    plugin().getLogger().warning("Failed to fetch farm shop for " + player.getName() + ": " + ex.getMessage());
                    runSync(() -> player.sendMessage(ChatColor.RED + "[Skyblock] 상점 정보를 불러오지 못했습니다."));
                    return null;
                });
    }

    public void openBorderMenu(Player player) {
        network().getWorldBorderState(player).thenAccept(result -> {
            if (result.failed()) {
                notifyFailure(player, "경계선 정보를 불러오지 못했습니다", result);
                return;
            }
            JsonObject data = result.data();
            boolean enabled = data != null && data.has("enabled") && data.get("enabled").getAsBoolean();
            String color = data != null && data.has("color") ? data.get("color").getAsString() : "GREEN";
            runSync(() -> openMenu(player, new BorderMenu(this, enabled, color)));
        }).exceptionally(ex -> {
            plugin().getLogger().warning("Failed to fetch border state for " + player.getName() + ": " + ex.getMessage());
            runSync(() -> player.sendMessage(ChatColor.RED + "[Skyblock] 내부 오류로 경계선 정보를 불러오지 못했습니다."));
            return null;
        });
    }

    public void openFarmHistoryDetail(Player player, String periodId) {
        if (farmHistoryService == null) {
            player.sendMessage(ChatColor.RED + "[Skyblock] 히스토리 서비스를 사용할 수 없습니다.");
            return;
        }
        farmHistoryService.detail(player, periodId).thenAccept(result -> {
            if (!result.successful()) {
                handleHistoryFailure(player, "히스토리 정보를 불러오지 못했습니다", result);
                return;
            }
            runSync(() -> openMenu(player, new FarmHistoryDetailMenu(this, result.data())));
        }).exceptionally(ex -> {
            plugin().getLogger().warning("Failed to fetch history detail for " + player.getName() + ": " + ex.getMessage());
            runSync(() -> player.sendMessage(ChatColor.RED + "[Skyblock] 내부 오류로 히스토리 정보를 불러오지 못했습니다."));
            return null;
        });
    }

    public int getHistoryPage(Player player) {
        return historyPages.getOrDefault(player.getUniqueId(), 1);
    }

    public void refreshPendingInvites(Player player) {
        AbstractMenu menu = openMenus.get(player.getUniqueId());
        if (menu instanceof PendingInvitesMenu) {
            openMenu(player, new PendingInvitesMenu(this));
        }
    }

    public void openQuestSelect(Player player, QuestType type, boolean canManage) {
        if (player == null || type == null) {
            return;
        }
        if (metadataService == null) {
            runSync(() -> openMenu(player, new QuestSelectMenu(this, type, null, canManage)));
            return;
        }
        metadataService.get(player.getUniqueId(), "island_q").thenAccept(optional -> {
            Integer selected = parseSelectedDifficulty(optional, type);
            runSync(() -> openMenu(player, new QuestSelectMenu(this, type, selected, canManage)));
        }).exceptionally(ex -> {
            plugin.getLogger().warning("Failed to read quest metadata for " + player.getName() + ": " + ex.getMessage());
            runSync(() -> openMenu(player, new QuestSelectMenu(this, type, null, canManage)));
            return null;
        });
    }

    public void openQuestAccept(Player player, QuestType type, int questCount, ItemStack preview, boolean canManage) {
        if (player == null || type == null) {
            return;
        }
        runSync(() -> openMenu(player, new QuestAcceptMenu(this, type, questCount, preview, canManage)));
    }

    public void openQuestList(Player player, QuestType type, JsonObject block) {
        if (player == null || type == null) {
            return;
        }
        if (block == null) {
            fetchQuestState(player, data -> openMenu(player, new QuestListMenu(this, type, extractBlock(data, type))));
        } else {
            runSync(() -> openMenu(player, new QuestListMenu(this, type, block)));
        }
    }

    public void assignQuest(Player player, QuestType type, int questCount) {
        if (player == null || type == null) {
            return;
        }
        networkService.questAssign(player, type, questCount).thenAccept(result -> {
            if (result.failed()) {
                if ("QUEST_PERMISSION_DENIED".equalsIgnoreCase(result.errorCode())) {
                    runSync(() -> player.sendMessage(ChatColor.RED + "[Skyblock] 팜장 또는 부팜장만 퀘스트를 발급할 수 있습니다."));
                } else {
                    notifyFailure(player, "퀘스트 발급에 실패했습니다", result);
                }
                return;
            }
            player.sendMessage(ChatColor.GREEN + "[Skyblock] " + (type.isDaily() ? "일간" : "주간") + " 퀘스트가 발급되었습니다.");
            JsonObject data = result.data();
            if (questProgressService != null && data != null && data.has("islandId")) {
                questProgressService.resetForIsland(data.get("islandId").getAsString(), type);
            }
            runSync(() -> openMenu(player, new QuestHubMenu(this, data)));
        }).exceptionally(ex -> {
            plugin().getLogger().warning("Failed to assign quest for " + player.getName() + ": " + ex.getMessage());
            runSync(() -> player.sendMessage(ChatColor.RED + "[Skyblock] 내부 오류로 퀘스트 발급에 실패했습니다."));
            return null;
        });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        AbstractMenu menu = openMenus.get(player.getUniqueId());
        if (menu == null) {
            return;
        }
        if (!menu.matches(event.getView().getTopInventory())) {
            return;
        }
        event.setCancelled(true);
        menu.handleClick(player, event);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        AbstractMenu menu = openMenus.get(player.getUniqueId());
        if (menu == null) {
            return;
        }
        if (!menu.matches(event.getInventory())) {
            return;
        }
        openMenus.remove(player.getUniqueId());
        menu.handleClose(player, event.getInventory());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        AbstractMenu menu = openMenus.remove(player.getUniqueId());
        if (menu != null) {
            menu.handleClose(player, menu.inventory());
        }
        invitePrompts.remove(player.getUniqueId());
        historyPages.remove(player.getUniqueId());
    }

    void reopen(Player player, AbstractMenu menu) {
        openMenu(player, menu);
    }

    public void shutdown() {
        openMenus.keySet().forEach(uuid -> {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.closeInventory();
            }
        });
        openMenus.clear();
        invitePrompts.clear();
        historyPages.clear();
    }

    public void beginInvitePrompt(Player player, Runnable onCancel, Consumer<String> consumer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(consumer, "consumer");
        invitePrompts.put(player.getUniqueId(), new InvitePrompt(consumer, onCancel));
        player.sendMessage("§6[Skyblock] §f채팅으로 초대할 닉네임을 입력하세요. §7(취소하려면 '취소' 입력)");
    }

    @EventHandler
    public void onAsyncChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        InvitePrompt prompt = invitePrompts.remove(player.getUniqueId());
        if (prompt == null) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage().trim();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (message.equalsIgnoreCase("취소") || message.equalsIgnoreCase("cancel")) {
                player.sendMessage("§6[Skyblock] §f초대를 취소했습니다.");
                if (prompt.onCancel() != null) {
                    prompt.onCancel().run();
                }
                return;
            }
            prompt.consumer().accept(message);
        });
    }

    private void fetchQuestState(Player player, Consumer<JsonObject> onSuccess) {
        networkService.questState(player).thenAccept(result -> {
            if (result.failed()) {
                notifyFailure(player, "퀘스트 정보를 불러오지 못했습니다", result);
                return;
            }
            JsonObject data = result.data();
            runSync(() -> onSuccess.accept(data));
        }).exceptionally(ex -> {
            plugin().getLogger().warning("Failed to fetch quest state for " + player.getName() + ": " + ex.getMessage());
            runSync(() -> player.sendMessage(ChatColor.RED + "[Skyblock] 내부 오류로 퀘스트 정보를 불러오지 못했습니다."));
            return null;
        });
    }

    private JsonObject extractBlock(JsonObject state, QuestType type) {
        if (state == null) {
            return new JsonObject();
        }
        String key = type.isDaily() ? "daily" : "weekly";
        return state.has(key) && state.get(key).isJsonObject() ? state.getAsJsonObject(key) : new JsonObject();
    }

    private void notifyFailure(Player player, String prefix, NetworkOperationResult result) {
        runSync(() -> player.sendMessage(ChatColor.RED + "[Skyblock] " + prefix + ": " + describeError(result)));
    }

    private String describeError(NetworkOperationResult result) {
        if (result == null) {
            return "알 수 없음";
        }
        String message = result.errorMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        return result.errorCode();
    }

    public void setQuestProgressService(QuestProgressService questProgressService) {
        this.questProgressService = questProgressService;
    }

    public void setFarmRankingService(FarmRankingService farmRankingService) {
        this.farmRankingService = farmRankingService;
    }

    public void setFarmHistoryService(FarmHistoryService farmHistoryService) {
        this.farmHistoryService = farmHistoryService;
    }

    public void setMetadataService(PlayerMetadataService metadataService) {
        this.metadataService = metadataService;
    }

    public void setFarmRewardService(FarmRewardService farmRewardService) {
        this.farmRewardService = farmRewardService;
    }

    public void setFarmShopService(FarmShopService farmShopService) {
        this.farmShopService = farmShopService;
    }

    private void handleRankingFailure(Player player,
                                      String prefix,
                                      FarmRankingService.Result<?> result) {
        if (result.operationResult() != null) {
            notifyFailure(player, prefix, result.operationResult());
        } else if (result.errorMessage() != null) {
            runSync(() -> player.sendMessage(ChatColor.RED + "[Skyblock] " + prefix + ": " + result.errorMessage()));
        } else {
            runSync(() -> player.sendMessage(ChatColor.RED + "[Skyblock] " + prefix + "."));
        }
    }

    private void handleHistoryFailure(Player player,
                                      String prefix,
                                      FarmHistoryService.Result<?> result) {
        if (result.operationResult() != null) {
            notifyFailure(player, prefix, result.operationResult());
        } else if (result.errorMessage() != null) {
            runSync(() -> player.sendMessage(ChatColor.RED + "[Skyblock] " + prefix + ": " + result.errorMessage()));
        } else {
            runSync(() -> player.sendMessage(ChatColor.RED + "[Skyblock] " + prefix + "."));
        }
    }

    private void runSync(Runnable runnable) {
        plugin.getServer().getScheduler().runTask(plugin, runnable);
    }

    private record InvitePrompt(Consumer<String> consumer, Runnable onCancel) {
    }

    private Integer parseSelectedDifficulty(Optional<String> metadata, QuestType type) {
        if (metadata.isEmpty()) {
            return null;
        }
        String raw = metadata.get();
        String[] parts = raw.split(":", 2);
        if (parts.length != 2) {
            return null;
        }
        if (!type.name().equalsIgnoreCase(parts[0])) {
            return null;
        }
        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public void persistQuestSelection(Player player, QuestType type, int questCount) {
        if (metadataService == null) {
            return;
        }
        Duration ttl = type.isDaily() ? Duration.ofDays(1) : Duration.ofDays(7);
        metadataService.put(player.getUniqueId(), "island_q", type.name() + ':' + questCount, ttl)
                .exceptionally(ex -> {
                    plugin.getLogger().warning("Failed to store quest metadata for " + player.getName() + ": " + ex.getMessage());
                    return null;
                });
    }
}
