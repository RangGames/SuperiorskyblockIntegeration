package wiki.creeper.superiorskyblockIntegeration.client.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import de.rapha149.signgui.SignGUI;
import de.rapha149.signgui.SignGUIAction;
import de.rapha149.signgui.SignGUIResult;
import de.rapha149.signgui.exception.SignGUIException;
import de.rapha149.signgui.exception.SignGUIVersionException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collectors;

import wiki.creeper.superiorskyblockIntegeration.api.NetworkOperationResult;
import wiki.creeper.superiorskyblockIntegeration.api.NetworkSkyblockService;
import wiki.creeper.superiorskyblockIntegeration.api.PlayerMetadataService;
import wiki.creeper.superiorskyblockIntegeration.client.cache.ClientCache;
import wiki.creeper.superiorskyblockIntegeration.client.model.BankHistoryPage;
import wiki.creeper.superiorskyblockIntegeration.client.model.BankSnapshot;
import wiki.creeper.superiorskyblockIntegeration.client.model.RolePermissionSnapshot;
import wiki.creeper.superiorskyblockIntegeration.client.services.ClientHeadDataService;
import wiki.creeper.superiorskyblockIntegeration.client.services.FarmHistoryService;
import wiki.creeper.superiorskyblockIntegeration.client.services.FarmRankingService;
import wiki.creeper.superiorskyblockIntegeration.client.services.FarmRewardService;
import wiki.creeper.superiorskyblockIntegeration.client.services.FarmShopService;
import wiki.creeper.superiorskyblockIntegeration.client.services.QuestProgressService;
import wiki.creeper.superiorskyblockIntegeration.client.services.PlayerPresenceService;
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
    private ClientHeadDataService headDataService;
    private PlayerPresenceService presenceService;
    private final Map<UUID, AbstractMenu> openMenus = new ConcurrentHashMap<>();
    private final Map<UUID, InvitePrompt> invitePrompts = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> historyPages = new ConcurrentHashMap<>();
    private final Map<UUID, BankPrompt> bankPrompts = new ConcurrentHashMap<>();
    private final Map<UUID, WarpRenamePrompt> warpRenamePrompts = new ConcurrentHashMap<>();
    private final Map<UUID, RuleAddPrompt> ruleAddPrompts = new ConcurrentHashMap<>();
    private final Map<UUID, CoopAddPrompt> coopAddPrompts = new ConcurrentHashMap<>();
    private final Map<UUID, BanAddPrompt> banAddPrompts = new ConcurrentHashMap<>();
    private final Map<UUID, String> viewerIslandIds = new ConcurrentHashMap<>();
    private static final int MAX_REWARD_SLOTS = 27;
    private static final Base64.Encoder BASE64_ENCODER = Base64.getEncoder();
    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();
    private static final DateTimeFormatter WARP_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneId.systemDefault());
    private static final String PREFIX = ChatColor.GOLD + "[Skyblock] " + ChatColor.WHITE;
    private static final int BANK_HISTORY_PAGE_SIZE = 10;

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

    public Optional<PlayerMetadataService> metadataService() {
        return Optional.ofNullable(metadataService);
    }

    public Optional<ClientHeadDataService> headDataService() {
        return Optional.ofNullable(headDataService);
    }

    public Optional<PlayerPresenceService> presenceService() {
        return Optional.ofNullable(presenceService);
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

    public void openPowerRewardEditor(Player player, int tier) {
        network().adminLoadPowerRewards(player, tier).thenAccept(result -> {
            if (result == null || result.failed()) {
                notifyFailure(player, "점수 보상을 불러오지 못했습니다", result);
                return;
            }
            JsonArray itemsArray = result.data() != null && result.data().has("items")
                    ? result.data().getAsJsonArray("items")
                    : new JsonArray();
            List<ItemStack> items = decodeRewardItems(itemsArray);
            runSync(() -> openMenu(player, new PowerRewardMenu(this, tier, items)));
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Failed to load score rewards for tier " + tier, ex);
            runSync(() -> player.sendMessage(ChatColor.RED + "[Skyblock] 점수 보상을 불러오는 중 오류가 발생했습니다."));
            return null;
        });
    }

    public void savePowerRewards(Player player, int tier, List<ItemStack> items) {
        if (player == null || !player.isOnline()) {
            return;
        }
        JsonArray payload = encodeRewardItems(items);
        networkService.adminSavePowerRewards(player, tier, payload).thenAccept(result ->
                runSync(() -> {
                    if (result == null || result.failed()) {
                        notifyFailure(player, "점수 보상을 저장하지 못했습니다", result);
                        return;
                    }
                    player.sendMessage(ChatColor.GREEN + "[Skyblock] 점수 보상 (티어 " + tier + ") 을 저장했습니다.");
                })
        ).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Failed to save score rewards for tier " + tier, ex);
            runSync(() -> player.sendMessage(ChatColor.RED + "[Skyblock] 점수 보상을 저장하는 중 오류가 발생했습니다."));
            return null;
        });
    }

    public void openTopRewardEditor(Player player, int rank) {
        network().adminLoadTopRewards(player, rank).thenAccept(result -> {
            if (result == null || result.failed()) {
                notifyFailure(player, "순위 보상을 불러오지 못했습니다", result);
                return;
            }
            JsonArray itemsArray = result.data() != null && result.data().has("items")
                    ? result.data().getAsJsonArray("items")
                    : new JsonArray();
            List<ItemStack> items = decodeRewardItems(itemsArray);
            runSync(() -> openMenu(player, new TopRewardMenu(this, rank, items)));
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Failed to load top rewards for rank " + rank, ex);
            runSync(() -> player.sendMessage(ChatColor.RED + "[Skyblock] 순위 보상을 불러오는 중 오류가 발생했습니다."));
            return null;
        });
    }

    public void saveTopRewards(Player player, int rank, List<ItemStack> items) {
        if (player == null || !player.isOnline()) {
            return;
        }
        JsonArray payload = encodeRewardItems(items);
        networkService.adminSaveTopRewards(player, rank, payload).thenAccept(result ->
                runSync(() -> {
                    if (result == null || result.failed()) {
                        notifyFailure(player, "순위 보상을 저장하지 못했습니다", result);
                        return;
                    }
                    player.sendMessage(ChatColor.GREEN + "[Skyblock] 순위 보상 (#" + rank + ") 을 저장했습니다.");
                })
        ).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Failed to save top rewards for rank " + rank, ex);
            runSync(() -> player.sendMessage(ChatColor.RED + "[Skyblock] 순위 보상을 저장하는 중 오류가 발생했습니다."));
            return null;
        });
    }

    public void openRolePermissions(Player player) {
        network().rolePermissions(player).thenAccept(result -> {
            if (result == null || result.failed()) {
                notifyFailure(player, "권한 정보를 불러오지 못했습니다", result);
                return;
            }
            RolePermissionSnapshot snapshot = RolePermissionSnapshot.from(result.data());
            runSync(() -> openMenu(player, new RolePermissionMenu(this, snapshot)));
        }).exceptionally(ex -> {
            plugin.getLogger().warning("Failed to fetch role permissions for " + player.getName() + ": " + ex.getMessage());
            runSync(() -> player.sendMessage(ChatColor.RED + "[Skyblock] 내부 오류로 권한 정보를 불러오지 못했습니다."));
            return null;
        });
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

    public void openBankMenu(Player player) {
        networkService.bankState(player).thenAccept(result -> {
            if (result == null || result.failed()) {
                notifyFailure(player, "은행 정보를 불러오지 못했습니다", result);
                return;
            }
            BankSnapshot snapshot = BankSnapshot.from(result.data());
            runSync(() -> openMenu(player, new BankMenu(this, snapshot)));
        }).exceptionally(ex -> {
            plugin.getLogger().warning("Failed to fetch bank state for " + player.getName() + ": " + ex.getMessage());
            runSync(() -> player.sendMessage(ChatColor.RED + "[Skyblock] 내부 오류로 은행 정보를 불러오지 못했습니다."));
            return null;
        });
    }

    public void openCoopMenu(Player player) {
        runSync(() -> openMenu(player, new CoopMenu(this)));
    }

    public void openBanMenu(Player player) {
        runSync(() -> openMenu(player, new BanMenu(this)));
    }

    public void openBankHistory(Player player, int page) {
        int safePage = Math.max(1, page);
        networkService.bankHistory(player, safePage, BANK_HISTORY_PAGE_SIZE).thenAccept(result -> {
            if (result == null || result.failed()) {
                notifyFailure(player, "은행 기록을 불러오지 못했습니다", result);
                return;
            }
            BankHistoryPage history = BankHistoryPage.from(result.data());
            runSync(() -> openMenu(player, new BankHistoryMenu(this, history)));
        }).exceptionally(ex -> {
            plugin.getLogger().warning("Failed to fetch bank history for " + player.getName() + ": " + ex.getMessage());
            runSync(() -> player.sendMessage(ChatColor.RED + "[Skyblock] 내부 오류로 은행 기록을 불러오지 못했습니다."));
            return null;
        });
    }

    public void setBankLock(Player player, boolean locked, Runnable onComplete) {
        networkService.bankSetLock(player, locked).thenAccept(result ->
                runSync(() -> {
                    if (result == null || result.failed()) {
                        notifyFailure(player, locked ? "금고 잠금을 설정하지 못했습니다" : "금고 잠금을 해제하지 못했습니다", result);
                    } else {
                        player.sendMessage(ChatColor.GREEN + "[Skyblock] 금고 잠금을 " + (locked ? "설정" : "해제") + "했습니다.");
                    }
                    if (onComplete != null) {
                        onComplete.run();
                    }
                })
        ).exceptionally(ex -> {
            plugin.getLogger().warning("Failed to toggle bank lock for " + player.getName() + ": " + ex.getMessage());
            runSync(() -> {
                player.sendMessage(ChatColor.RED + "[Skyblock] 내부 오류로 은행 잠금 상태를 변경하지 못했습니다.");
                if (onComplete != null) {
                    onComplete.run();
                }
            });
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

    public void openHomeWarps(Player player) {
        Objects.requireNonNull(player, "player");
        network().listHomeWarps(player).thenAccept(result -> {
            if (result == null || result.failed()) {
                notifyFailure(player, "홈 정보를 불러오지 못했습니다", result);
                return;
            }
            JsonObject data = result.data() != null ? result.data() : new JsonObject();
            if (data.has("islandId") && !data.get("islandId").isJsonNull()) {
                viewerIslandIds.put(player.getUniqueId(), data.get("islandId").getAsString());
            }
            runSync(() -> openMenu(player, new HomeWarpMenu(this, data)));
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Failed to fetch home warps for " + player.getName(), ex);
            runSync(() -> player.sendMessage(ChatColor.RED + PREFIX + "홈 정보를 불러오는 중 오류가 발생했습니다."));
            return null;
        });
    }

    public void requestHomeWarpCreation(Player player) {
        Objects.requireNonNull(player, "player");
        if (!player.isOnline()) {
            return;
        }
        Location current = player.getLocation();
        if (current.getWorld() == null) {
            player.sendMessage(ChatColor.RED + PREFIX + "현재 위치의 월드를 확인할 수 없습니다.");
            return;
        }
        String name = "홈 " + WARP_NAME_FORMAT.format(Instant.now());
        Location snapshot = new Location(current.getWorld(), current.getX(), current.getY(), current.getZ(), current.getYaw(), current.getPitch());
        network().createHomeWarp(player, name, snapshot).thenAccept(result ->
                runSync(() -> {
                    if (result == null || result.failed()) {
                        if (result != null && "CONFLICT".equalsIgnoreCase(result.errorCode())) {
                            player.sendMessage(ChatColor.RED + PREFIX + "홈은 최대 2개까지 등록할 수 있습니다.");
                        } else {
                            notifyFailure(player, "홈을 생성하지 못했습니다", result);
                        }
                        openHomeWarps(player);
                        return;
                    }
                    player.sendMessage(PREFIX + "새로운 홈을 등록했습니다.");
                    openHomeWarps(player);
                })
        ).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Failed to create home warp for " + player.getName(), ex);
            runSync(() -> player.sendMessage(ChatColor.RED + PREFIX + "홈을 생성하는 중 오류가 발생했습니다."));
            return null;
        });
    }

    public void toggleHomeWarpPrivacy(Player player, String warpName) {
        Objects.requireNonNull(player, "player");
        network().toggleHomeWarpPrivacy(player, warpName).thenAccept(result ->
                runSync(() -> {
                    if (result == null || result.failed()) {
                        notifyFailure(player, "홈 공개 상태를 변경하지 못했습니다", result);
                        openHomeWarps(player);
                        return;
                    }
                    JsonObject data = result.data();
                    boolean nowPrivate = data != null && data.has("private") && data.get("private").getAsBoolean();
                    player.sendMessage(PREFIX + "홈 '" + warpName + "'을 " + (nowPrivate ? "비공개" : "공개") + "로 설정했습니다.");
                    openHomeWarps(player);
                })
        ).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Failed to toggle warp privacy for " + player.getName(), ex);
            runSync(() -> player.sendMessage(ChatColor.RED + PREFIX + "홈 공개 상태를 변경하는 중 오류가 발생했습니다."));
            return null;
        });
    }

    public void deleteHomeWarp(Player player, String warpName) {
        Objects.requireNonNull(player, "player");
        network().deleteHomeWarp(player, warpName).thenAccept(result ->
                runSync(() -> {
                    if (result == null || result.failed()) {
                        notifyFailure(player, "홈을 삭제하지 못했습니다", result);
                        openHomeWarps(player);
                        return;
                    }
                    player.sendMessage(PREFIX + "홈 '" + warpName + "'을 삭제했습니다.");
                    openHomeWarps(player);
                })
        ).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Failed to delete home warp for " + player.getName(), ex);
            runSync(() -> player.sendMessage(ChatColor.RED + PREFIX + "홈을 삭제하는 중 오류가 발생했습니다."));
            return null;
        });
    }

    public void beginWarpRenamePrompt(Player player, String warpName) {
        Objects.requireNonNull(player, "player");
        player.closeInventory();
        warpRenamePrompts.put(player.getUniqueId(), new WarpRenamePrompt(warpName));
        player.sendMessage(PREFIX + "채팅에 새로운 홈 이름을 입력하세요. §7(취소하려면 '취소')");
    }

    private void handleWarpRenamePrompt(Player player, WarpRenamePrompt prompt, String message) {
        String trimmed = ChatColor.stripColor(message).trim();
        if (trimmed.equalsIgnoreCase("취소") || trimmed.equalsIgnoreCase("cancel")) {
            player.sendMessage(PREFIX + "홈 이름 변경을 취소했습니다.");
            openHomeWarps(player);
            return;
        }
        if (trimmed.isEmpty()) {
            player.sendMessage(ChatColor.RED + PREFIX + "올바른 이름을 입력해주세요.");
            warpRenamePrompts.put(player.getUniqueId(), prompt);
            return;
        }
        if (trimmed.length() > 16) {
            player.sendMessage(ChatColor.RED + PREFIX + "홈 이름은 16자 이하로 입력해주세요.");
            warpRenamePrompts.put(player.getUniqueId(), prompt);
            return;
        }
        network().renameHomeWarp(player, prompt.warpName(), trimmed).thenAccept(result ->
                runSync(() -> {
                    if (result == null || result.failed()) {
                        notifyFailure(player, "홈 이름을 변경하지 못했습니다", result);
                        openHomeWarps(player);
                        return;
                    }
                    player.sendMessage(PREFIX + "홈 이름을 '" + trimmed + "'으로 변경했습니다.");
                    openHomeWarps(player);
                })
        ).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Failed to rename home warp for " + player.getName(), ex);
            runSync(() -> player.sendMessage(ChatColor.RED + PREFIX + "홈 이름을 변경하는 중 오류가 발생했습니다."));
            return null;
        });
    }

    public void openPlayerWarps(Player player, String targetIdentifier) {
        Objects.requireNonNull(player, "player");
        network().listPlayerWarps(player, targetIdentifier).thenAccept(result -> {
            if (result == null || result.failed()) {
                notifyFailure(player, "상대방 홈 정보를 불러오지 못했습니다", result);
                return;
            }
            JsonObject data = result.data() != null ? result.data() : new JsonObject();
            runSync(() -> openMenu(player, new PlayerWarpMenu(this, data)));
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Failed to fetch player warps for " + player.getName(), ex);
            runSync(() -> player.sendMessage(ChatColor.RED + PREFIX + "홈 정보를 불러오는 중 오류가 발생했습니다."));
            return null;
        });
    }

    public void openGlobalWarpBrowser(Player player, int page) {
        Objects.requireNonNull(player, "player");
        network().listGlobalWarps(player, Math.max(1, page), 36).thenAccept(result -> {
            if (result == null || result.failed()) {
                notifyFailure(player, "공개 홈 목록을 불러오지 못했습니다", result);
                return;
            }
            JsonObject data = result.data() != null ? result.data() : new JsonObject();
            runSync(() -> openMenu(player, new WarpBrowserMenu(this, data)));
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Failed to fetch global warps for " + player.getName(), ex);
            runSync(() -> player.sendMessage(ChatColor.RED + PREFIX + "공개 홈 목록을 불러오는 중 오류가 발생했습니다."));
            return null;
        });
    }

    public void visitWarp(Player player, String islandId, String warpName) {
        Objects.requireNonNull(player, "player");
        String resolvedIslandId = islandId;
        if ((resolvedIslandId == null || resolvedIslandId.isBlank()) && viewerIslandIds.containsKey(player.getUniqueId())) {
            resolvedIslandId = viewerIslandIds.get(player.getUniqueId());
        }
        if (resolvedIslandId == null || resolvedIslandId.isBlank()) {
            player.sendMessage(ChatColor.RED + PREFIX + "이동할 홈 정보를 찾을 수 없습니다.");
            return;
        }
        final String targetIslandId = resolvedIslandId;
        network().visitWarp(player, targetIslandId, warpName).thenAccept(result ->
                runSync(() -> {
                    if (result == null || result.failed()) {
                        if (result != null && "FORBIDDEN".equalsIgnoreCase(result.errorCode())) {
                            player.sendMessage(ChatColor.RED + PREFIX + "비공개 홈에는 이동할 수 없습니다.");
                        } else if (result != null && "NOT_FOUND".equalsIgnoreCase(result.errorCode())) {
                            player.sendMessage(ChatColor.RED + PREFIX + "해당 홈을 찾을 수 없습니다.");
                        } else {
                            notifyFailure(player, "홈으로 이동하지 못했습니다", result);
                        }
                        return;
                    }
                    player.sendMessage(PREFIX + "해당 홈으로 이동합니다...");
                })
        ).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Failed to visit warp for " + player.getName(), ex);
            runSync(() -> player.sendMessage(ChatColor.RED + PREFIX + "홈으로 이동하는 중 오류가 발생했습니다."));
            return null;
        });
    }

    public void openRulesMenu(Player player) {
        Objects.requireNonNull(player, "player");
        network().listIslandRules(player).thenAccept(result -> {
            if (result == null || result.failed()) {
                notifyFailure(player, "팜 규칙을 불러오지 못했습니다", result);
                return;
            }
            JsonObject data = result.data() != null ? result.data() : new JsonObject();
            runSync(() -> openMenu(player, new RulesMenu(this, data)));
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Failed to fetch island rules for " + player.getName(), ex);
            runSync(() -> player.sendMessage(ChatColor.RED + PREFIX + "팜 규칙을 불러오는 중 오류가 발생했습니다."));
            return null;
        });
    }

    public void beginRuleAddPrompt(Player player) {
        Objects.requireNonNull(player, "player");
        player.closeInventory();
        ruleAddPrompts.put(player.getUniqueId(), new RuleAddPrompt());
        player.sendMessage(PREFIX + "채팅에 추가할 규칙을 입력하세요. §7(취소하려면 '취소')");
    }

    private void handleRuleAddPrompt(Player player, String message) {
        if (message.equalsIgnoreCase("취소") || message.equalsIgnoreCase("cancel")) {
            player.sendMessage(PREFIX + "규칙 추가를 취소했습니다.");
            openRulesMenu(player);
            return;
        }
        String normalized = ChatColor.stripColor(message).trim();
        if (normalized.isEmpty()) {
            player.sendMessage(ChatColor.RED + PREFIX + "빈 규칙은 등록할 수 없습니다. 다시 입력해주세요.");
            ruleAddPrompts.put(player.getUniqueId(), new RuleAddPrompt());
            return;
        }
        if (normalized.length() > 64) {
            player.sendMessage(ChatColor.RED + PREFIX + "규칙은 64자 이하로 입력해주세요.");
            ruleAddPrompts.put(player.getUniqueId(), new RuleAddPrompt());
            return;
        }
        network().addIslandRule(player, normalized).thenAccept(result ->
                runSync(() -> {
                    if (result == null || result.failed()) {
                        notifyFailure(player, "규칙을 추가하지 못했습니다", result);
                        openRulesMenu(player);
                        return;
                    }
                    player.sendMessage(PREFIX + "새로운 규칙이 등록되었습니다.");
                    openRulesMenu(player);
                })
        ).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Failed to add island rule for " + player.getName(), ex);
            runSync(() -> player.sendMessage(ChatColor.RED + PREFIX + "규칙을 추가하는 중 오류가 발생했습니다."));
            return null;
        });
    }

    private void handleCoopPrompt(Player player, CoopAddPrompt prompt, String message) {
        Runnable finish = prompt.finishAction();
        if (message.equalsIgnoreCase("취소") || message.equalsIgnoreCase("cancel")) {
            player.sendMessage(PREFIX + "알바 고용을 취소했습니다.");
            if (finish != null) {
                finish.run();
            }
            return;
        }
        String target = ChatColor.stripColor(message).trim();
        if (target.isEmpty()) {
            player.sendMessage(ChatColor.RED + PREFIX + "닉네임을 정확히 입력해주세요.");
            coopAddPrompts.put(player.getUniqueId(), new CoopAddPrompt(finish));
            return;
        }
        player.sendMessage(PREFIX + "알바 고용을 처리 중입니다...");
        network().addCoopPlayer(player, target).thenAccept(result ->
                runSync(() -> {
                    if (result == null || result.failed()) {
                        notifyFailure(player, "알바 고용에 실패했습니다", result);
                    } else {
                        String name = extractPlayerName(result.data(), target);
                        player.sendMessage(ChatColor.GREEN + PREFIX + name + "님을 알바로 고용했습니다.");
                    }
                    if (finish != null) {
                        finish.run();
                    }
                })
        ).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Failed to add coop player for " + player.getName(), ex);
            runSync(() -> {
                player.sendMessage(ChatColor.RED + PREFIX + "알바 고용 처리 중 오류가 발생했습니다.");
                if (finish != null) {
                    finish.run();
                }
            });
            return null;
        });
    }

    private void handleBanPrompt(Player player, BanAddPrompt prompt, String message) {
        Runnable finish = prompt.finishAction();
        if (message.equalsIgnoreCase("취소") || message.equalsIgnoreCase("cancel")) {
            player.sendMessage(PREFIX + "플레이어 차단을 취소했습니다.");
            if (finish != null) {
                finish.run();
            }
            return;
        }
        String target = ChatColor.stripColor(message).trim();
        if (target.isEmpty()) {
            player.sendMessage(ChatColor.RED + PREFIX + "닉네임을 정확히 입력해주세요.");
            banAddPrompts.put(player.getUniqueId(), new BanAddPrompt(finish));
            return;
        }
        player.sendMessage(PREFIX + "플레이어 차단을 처리 중입니다...");
        network().addBannedPlayer(player, target).thenAccept(result ->
                runSync(() -> {
                    if (result == null || result.failed()) {
                        notifyFailure(player, "플레이어를 차단하지 못했습니다", result);
                    } else {
                        String name = extractPlayerName(result.data(), target);
                        player.sendMessage(ChatColor.GREEN + PREFIX + name + "님을 차단했습니다.");
                    }
                    if (finish != null) {
                        finish.run();
                    }
                })
        ).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Failed to ban player for " + player.getName(), ex);
            runSync(() -> {
                player.sendMessage(ChatColor.RED + PREFIX + "플레이어 차단 중 오류가 발생했습니다.");
                if (finish != null) {
                    finish.run();
                }
            });
            return null;
        });
    }

    public void removeIslandRule(Player player, int ruleIndex) {
        Objects.requireNonNull(player, "player");
        network().removeIslandRule(player, ruleIndex).thenAccept(result ->
                runSync(() -> {
                    if (result == null || result.failed()) {
                        notifyFailure(player, "규칙을 삭제하지 못했습니다", result);
                        openRulesMenu(player);
                        return;
                    }
                    String removed = getString(result.data(), "removed", "");
                    player.sendMessage(PREFIX + ruleIndex + "번 규칙을 삭제했습니다." + (removed.isBlank() ? "" : " (" + removed + ")"));
                    openRulesMenu(player);
                })
        ).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Failed to remove island rule for " + player.getName(), ex);
            runSync(() -> player.sendMessage(ChatColor.RED + PREFIX + "규칙을 삭제하는 중 오류가 발생했습니다."));
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
        bankPrompts.remove(player.getUniqueId());
        warpRenamePrompts.remove(player.getUniqueId());
        ruleAddPrompts.remove(player.getUniqueId());
        coopAddPrompts.remove(player.getUniqueId());
        banAddPrompts.remove(player.getUniqueId());
        viewerIslandIds.remove(player.getUniqueId());
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
        bankPrompts.clear();
        warpRenamePrompts.clear();
        ruleAddPrompts.clear();
        coopAddPrompts.clear();
        banAddPrompts.clear();
        viewerIslandIds.clear();
    }

    public void beginInvitePrompt(Player player, Runnable onCancel, Consumer<String> consumer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(consumer, "consumer");
        invitePrompts.put(player.getUniqueId(), new InvitePrompt(consumer, onCancel));
        player.sendMessage("§6[Skyblock] §f채팅으로 초대할 닉네임을 입력하세요. §7(취소하려면 '취소' 입력)");
    }

    public void beginCoopPrompt(Player player, Runnable onComplete) {
        Objects.requireNonNull(player, "player");
        coopAddPrompts.put(player.getUniqueId(), new CoopAddPrompt(onComplete));
        player.sendMessage("§6[Skyblock] §f채팅으로 고용할 닉네임을 입력하세요. §7(취소하려면 '취소' 입력)");
    }

    public void beginBanPrompt(Player player, Runnable onComplete) {
        Objects.requireNonNull(player, "player");
        player.closeInventory();
        banAddPrompts.put(player.getUniqueId(), new BanAddPrompt(onComplete));
        player.sendMessage("§6[Skyblock] §f채팅으로 차단할 닉네임을 입력하세요. §7(취소하려면 '취소' 입력)");
    }

    @EventHandler
    public void onAsyncChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        BankPrompt bankPrompt = bankPrompts.get(uuid);
        if (bankPrompt != null && bankPrompt.mode() == BankPromptMode.CHAT) {
            event.setCancelled(true);
            String message = event.getMessage().trim();
            plugin.getServer().getScheduler().runTask(plugin, () -> handleBankPrompt(player, bankPrompt, message));
            return;
        }

        WarpRenamePrompt renamePrompt = warpRenamePrompts.remove(uuid);
        if (renamePrompt != null) {
            event.setCancelled(true);
            String message = event.getMessage().trim();
            plugin.getServer().getScheduler().runTask(plugin, () -> handleWarpRenamePrompt(player, renamePrompt, message));
            return;
        }

        RuleAddPrompt rulePrompt = ruleAddPrompts.remove(uuid);
        if (rulePrompt != null) {
            event.setCancelled(true);
            String message = event.getMessage().trim();
            plugin.getServer().getScheduler().runTask(plugin, () -> handleRuleAddPrompt(player, message));
            return;
        }

        CoopAddPrompt coopPrompt = coopAddPrompts.remove(uuid);
        if (coopPrompt != null) {
            event.setCancelled(true);
            String message = event.getMessage().trim();
            plugin.getServer().getScheduler().runTask(plugin, () -> handleCoopPrompt(player, coopPrompt, message));
            return;
        }

        BanAddPrompt banPrompt = banAddPrompts.remove(uuid);
        if (banPrompt != null) {
            event.setCancelled(true);
            String message = event.getMessage().trim();
            plugin.getServer().getScheduler().runTask(plugin, () -> handleBanPrompt(player, banPrompt, message));
            return;
        }

        InvitePrompt prompt = invitePrompts.remove(uuid);
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

    public void notifyFailure(Player player, String prefix, NetworkOperationResult result) {
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

    public void setHeadDataService(ClientHeadDataService headDataService) {
        this.headDataService = headDataService;
    }

    public void setPresenceService(PlayerPresenceService presenceService) {
        this.presenceService = presenceService;
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

    public JsonArray encodeRewardItems(List<ItemStack> items) {
        JsonArray array = new JsonArray();
        if (items == null) {
            return array;
        }
        for (ItemStack stack : items) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            try {
                String encoded = encodeItem(stack.clone());
                if (encoded != null && !encoded.isBlank()) {
                    array.add(encoded);
                }
            } catch (IOException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to serialize reward item", ex);
            }
            if (array.size() >= MAX_REWARD_SLOTS) {
                break;
            }
        }
        return array;
    }

    public List<ItemStack> decodeRewardItems(JsonArray array) {
        List<ItemStack> items = new ArrayList<>();
        if (array == null) {
            return items;
        }
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive()) {
                continue;
            }
            String token = element.getAsString();
            try {
                ItemStack stack = decodeItem(token);
                if (stack != null && !stack.getType().isAir()) {
                    items.add(stack);
                }
            } catch (IOException | ClassNotFoundException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to deserialize reward item", ex);
            }
            if (items.size() >= MAX_REWARD_SLOTS) {
                break;
            }
        }
        return items;
    }

    private String encodeItem(ItemStack item) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             BukkitObjectOutputStream data = new BukkitObjectOutputStream(output)) {
            data.writeObject(item);
            data.flush();
            return BASE64_ENCODER.encodeToString(output.toByteArray());
        }
    }

    private ItemStack decodeItem(String token) throws IOException, ClassNotFoundException {
        if (token == null || token.isBlank()) {
            return null;
        }
        byte[] bytes = BASE64_DECODER.decode(token);
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes);
             BukkitObjectInputStream data = new BukkitObjectInputStream(input)) {
            Object object = data.readObject();
            return object instanceof ItemStack stack ? stack : null;
        }
    }

    private record InvitePrompt(Consumer<String> consumer, Runnable onCancel) {
    }

    enum BankPromptType {
        DEPOSIT,
        WITHDRAW
    }

    enum BankPromptMode {
        SIGN,
        CHAT
    }

    private record BankPrompt(BankPromptType type, Runnable finishAction, BankPromptMode mode) {
        BankPrompt withMode(BankPromptMode newMode) {
            return new BankPrompt(type, finishAction, newMode);
        }
    }

    private record WarpRenamePrompt(String warpName) {
    }

    private record RuleAddPrompt() {
    }

    private record CoopAddPrompt(Runnable finishAction) {
    }

    private record BanAddPrompt(Runnable finishAction) {
    }

    private String getString(JsonObject obj, String key, String defaultValue) {
        if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return defaultValue;
        }
        try {
            return obj.get(key).getAsString();
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private String extractPlayerName(JsonObject data, String fallback) {
        if (data == null) {
            return fallback;
        }
        JsonObject player = data.has("player") && data.get("player").isJsonObject()
                ? data.getAsJsonObject("player")
                : null;
        return getString(player, "name", fallback);
    }

    private boolean openBankSign(Player player, BankPrompt prompt, String initialInput) {
        String[] lines = bankSignLines(prompt.type(), initialInput);
        try {
            SignGUI gui = SignGUI.builder()
                    .setLines(lines)
                    .setType(Material.OAK_SIGN)
                    .setColor(DyeColor.YELLOW)
                    .setHandler((p, result) -> handleBankSignResult(p, prompt, result))
                    .callHandlerSynchronously(plugin)
                    .build();
            player.closeInventory();
            gui.open(player);
            String action = prompt.type() == BankPromptType.DEPOSIT ? "입금" : "출금";
            player.sendMessage("§6[Skyblock] §f사인의 첫 줄에 " + action + " 금액을 입력하세요. §7(취소: '취소' 또는 'cancel')");
            return true;
        } catch (SignGUIVersionException ex) {
            plugin.getLogger().warning("SignGUI does not support this server version: " + ex.getMessage());
        } catch (SignGUIException ex) {
            plugin.getLogger().warning("Failed to open SignGUI for " + player.getName() + ": " + ex.getMessage());
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Unexpected error while opening SignGUI for " + player.getName() + ": " + ex.getMessage());
        }
        return false;
    }

    private String[] bankSignLines(BankPromptType type, String currentInput) {
        String action = type == BankPromptType.DEPOSIT ? "입금" : "출금";
        String input = currentInput != null ? currentInput : "";
        return new String[]{
                input,
                ChatColor.GOLD + action + " 금액을 입력하세요",
                ChatColor.YELLOW + "숫자만 입력 가능",
                ChatColor.GRAY + "취소: 취소 / cancel"
        };
    }

    private List<SignGUIAction> handleBankSignResult(Player player, BankPrompt prompt, SignGUIResult result) {
        String raw = result.getLineWithoutColor(0);
        String input = raw != null ? raw.trim() : "";
        if (input.equalsIgnoreCase("취소") || input.equalsIgnoreCase("cancel")) {
            bankPrompts.remove(player.getUniqueId());
            player.sendMessage("§6[Skyblock] §f은행 입력을 취소했습니다.");
            if (prompt.finishAction() != null) {
                prompt.finishAction().run();
            } else {
                openBankMenu(player);
            }
            return Collections.emptyList();
        }
        if (input.isBlank()) {
            bankPrompts.remove(player.getUniqueId());
            player.sendMessage("§6[Skyblock] §f은행 입력을 취소했습니다.");
            if (prompt.finishAction() != null) {
                prompt.finishAction().run();
            } else {
                openBankMenu(player);
            }
            return Collections.emptyList();
        }
        String normalised = input.replace(",", "").trim();
        BigDecimal amount;
        try {
            amount = new BigDecimal(normalised);
        } catch (NumberFormatException ex) {
            return List.of(
                    SignGUIAction.run(() -> player.sendMessage("§c[Skyblock] 금액은 숫자로 입력해주세요.")),
                    SignGUIAction.displayNewLines(bankSignLines(prompt.type(), input))
            );
        }
        if (amount.signum() <= 0) {
            return List.of(
                    SignGUIAction.run(() -> player.sendMessage("§c[Skyblock] 양수 금액만 입력할 수 있습니다.")),
                    SignGUIAction.displayNewLines(bankSignLines(prompt.type(), input))
            );
        }

        processBankTransaction(player, prompt, amount);
        return Collections.emptyList();
    }

    private void handleBankPrompt(Player player, BankPrompt prompt, String message) {
        UUID uuid = player.getUniqueId();
        if (message.equalsIgnoreCase("취소") || message.equalsIgnoreCase("cancel")) {
            bankPrompts.remove(uuid);
            player.sendMessage("§6[Skyblock] §f은행 입력을 취소했습니다.");
            if (prompt.finishAction() != null) {
                prompt.finishAction().run();
            } else {
                openBankMenu(player);
            }
            return;
        }

        String normalised = message.replace(",", "").trim();
        BigDecimal amount;
        try {
            amount = new BigDecimal(normalised);
        } catch (NumberFormatException ex) {
            player.sendMessage("§c[Skyblock] 금액은 숫자로 입력해주세요.");
            if (prompt.mode() == BankPromptMode.CHAT) {
                bankPrompts.put(uuid, prompt);
            }
            return;
        }
        if (amount.signum() <= 0) {
            player.sendMessage("§c[Skyblock] 양수 금액만 입력할 수 있습니다.");
            if (prompt.mode() == BankPromptMode.CHAT) {
                bankPrompts.put(uuid, prompt);
            }
            return;
        }

        processBankTransaction(player, prompt, amount);
    }

    private void processBankTransaction(Player player, BankPrompt prompt, BigDecimal amount) {
        UUID uuid = player.getUniqueId();
        bankPrompts.remove(uuid);
        player.sendMessage("§6[Skyblock] §f처리 중입니다...");

        boolean deposit = prompt.type() == BankPromptType.DEPOSIT;
        var future = deposit
                ? networkService.bankDeposit(player, amount)
                : networkService.bankWithdraw(player, amount);

        future
                .thenAccept(result -> runSync(() -> {
                    if (result == null || result.failed()) {
                        notifyFailure(player, deposit ? "입금에 실패했습니다" : "출금에 실패했습니다", result);
                    } else {
                        player.sendMessage(ChatColor.GREEN + "[Skyblock] " +
                                (deposit ? "입금이 완료되었습니다." : "출금이 완료되었습니다."));
                    }
                    if (prompt.finishAction() != null) {
                        prompt.finishAction().run();
                    } else {
                        openBankMenu(player);
                    }
                }))
                .exceptionally(ex -> {
                    plugin.getLogger().warning("Failed to process bank transaction for " + player.getName() + ": " + ex.getMessage());
                    runSync(() -> {
                        player.sendMessage(ChatColor.RED + "[Skyblock] 은행 작업 중 오류가 발생했습니다.");
                        if (prompt.finishAction() != null) {
                            prompt.finishAction().run();
                        } else {
                            openBankMenu(player);
                        }
                    });
                    return null;
                });
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

    public void beginBankPrompt(Player player, BankPromptType type, Runnable onCancel) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(type, "type");
        UUID uuid = player.getUniqueId();
        BankPrompt prompt = new BankPrompt(type, onCancel, BankPromptMode.SIGN);
        bankPrompts.put(uuid, prompt);
        if (openBankSign(player, prompt, "")) {
            return;
        }
        BankPrompt chatPrompt = prompt.withMode(BankPromptMode.CHAT);
        bankPrompts.put(uuid, chatPrompt);
        player.sendMessage("§c[Skyblock] 사인 입력을 사용할 수 없어 채팅 입력으로 전환합니다.");
        String action = type == BankPromptType.DEPOSIT ? "입금" : "출금";
        player.sendMessage("§6[Skyblock] §f채팅으로 " + action + "할 금액을 입력하세요. §7(취소하려면 '취소' 입력)");
    }

    public String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return "0";
        }
        DecimalFormat format = new DecimalFormat("#,##0");
        format.setRoundingMode(RoundingMode.DOWN);
        return format.format(amount);
    }
}
