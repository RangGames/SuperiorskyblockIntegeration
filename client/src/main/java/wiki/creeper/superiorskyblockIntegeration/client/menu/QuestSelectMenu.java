package wiki.creeper.superiorskyblockIntegeration.client.menu;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;

import wiki.creeper.superiorskyblockIntegeration.common.quest.QuestDifficulty;
import wiki.creeper.superiorskyblockIntegeration.common.quest.QuestRewards;
import wiki.creeper.superiorskyblockIntegeration.common.quest.QuestType;

/**
 * Difficulty selection menu for daily/weekly quests.
 */
public final class QuestSelectMenu extends AbstractMenu {

    private final QuestType type;
    private final Integer selected;
    private final boolean canManage;

    public QuestSelectMenu(IslandMenuManager manager, QuestType type, Integer selected, boolean canManage) {
        super(manager);
        this.type = type;
        this.selected = selected;
        this.canManage = canManage;
    }

    @Override
    protected String title(Player player) {
        return type.isDaily() ? "§e일간 퀘스트 선택" : "§b주간 퀘스트 선택";
    }

    @Override
    protected int size() {
        return 36;
    }

    @Override
    protected void build(Player player, Inventory inventory) {
        fill(icon(Material.GRAY_STAINED_GLASS_PANE, " "));

        List<QuestDifficulty> difficulties = Arrays.stream(QuestDifficulty.values())
                .filter(diff -> diff.type() == type)
                .toList();

        for (int i = 0; i < difficulties.size(); i++) {
            QuestDifficulty difficulty = difficulties.get(i);
            int slot = switch (i) {
                case 0 -> 20;
                case 1 -> 22;
                case 2 -> 24;
                default -> -1;
            };
            if (slot >= 0) {
                inventory.setItem(slot, createIcon(difficulty));
            }
        }

        setItem(31, icon(Material.BARRIER, "&c돌아가기", "&7퀘스트 메뉴로 돌아갑니다."));
    }

    @Override
    protected void onClick(Player player, InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot == 31) {
            manager().openQuestHub(player);
            return;
        }
        QuestDifficulty difficulty = difficultyFromSlot(slot);
        if (difficulty == null) {
            return;
        }
        if (!canManage) {
            player.sendMessage(ChatColor.RED + "[Skyblock] 퀘스트를 발급할 권한이 없습니다.");
            manager().openQuestHub(player);
            return;
        }
        ItemStack preview = event.getCurrentItem() != null ? event.getCurrentItem().clone() : null;
        manager().openQuestAccept(player, type, difficulty.questCount(), preview, canManage);
    }

    private QuestDifficulty difficultyFromSlot(int slot) {
        return switch (slot) {
            case 20 -> QuestDifficulty.of(type, type.isDaily() ? 3 : 5).orElse(null);
            case 22 -> QuestDifficulty.of(type, type.isDaily() ? 4 : 6).orElse(null);
            case 24 -> QuestDifficulty.of(type, type.isDaily() ? 5 : 7).orElse(null);
            default -> null;
        };
    }

    private ItemStack createIcon(QuestDifficulty difficulty) {
        int questCount = difficulty.questCount();
        int moonlight = QuestRewards.moonlightReward(type, questCount);
        int farmPoint = QuestRewards.farmPointReward(type);
        String tier = tierLabel(questCount);
        boolean isSelected = selected != null && selected == questCount;
        String label = (type.isDaily() ? "일간" : "주간") + " 퀘스트";
        String actionLine;
        if (canManage) {
            actionLine = isSelected ? "&e이미 선택된 난이도입니다." : "&a클릭 시 이 난이도를 발급합니다.";
        } else {
            actionLine = "&c팜장 또는 부팜장에게만 허용됩니다.";
        }
        return icon(Material.PLAYER_HEAD,
                "&6&l[!] &f" + label + " &7&o(" + tier + ")" + (isSelected ? " &7(선택됨)" : ""),
                " ",
                "&6&l| &f퀘스트 개수: &e" + questCount + "개",
                " ",
                "&6&l| &f보상 목록",
                "&f  - 달빛: &e" + formatNumber(moonlight),
                "&f  - 팜 포인트: &e" + formatNumber(farmPoint),
                " ",
                actionLine);
    }

    private String formatNumber(int value) {
        return String.format("%,d", value);
    }

    private String tierLabel(int questCount) {
        if (type.isDaily()) {
            return switch (questCount) {
                case 3 -> "난이도 하";
                case 4 -> "난이도 중";
                case 5 -> "난이도 상";
                default -> "난이도";
            };
        }
        return switch (questCount) {
            case 5 -> "난이도 하";
            case 6 -> "난이도 중";
            case 7 -> "난이도 상";
            default -> "난이도";
        };
    }
}
