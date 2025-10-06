package wiki.creeper.superiorskyblockIntegeration.client.menu;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import wiki.creeper.superiorskyblockIntegeration.common.quest.QuestType;

/**
 * Hopper-style confirmation menu for quest selection.
 */
public final class QuestAcceptMenu extends AbstractMenu {

    private final QuestType type;
    private final int questCount;
    private final ItemStack preview;
    private final boolean canManage;

    QuestAcceptMenu(IslandMenuManager manager,
                    QuestType type,
                    int questCount,
                    ItemStack preview,
                    boolean canManage) {
        super(manager);
        this.type = type;
        this.questCount = questCount;
        this.preview = preview != null ? preview.clone() : null;
        this.canManage = canManage;
    }

    @Override
    protected String title(Player player) {
        return type.isDaily() ? "§e일간 퀘스트 수락" : "§b주간 퀘스트 수락";
    }

    @Override
    protected int size() {
        return 9;
    }

    @Override
    protected void build(Player player, Inventory inventory) {
        fill(icon(Material.GRAY_STAINED_GLASS_PANE, " "));

        setItem(0, icon(Material.PLAYER_HEAD, "&a&l[수락]", "&7선택한 퀘스트를 발급합니다."));
        if (preview != null) {
            setItem(2, preview);
        }
        setItem(3, icon(Material.PAPER, "&f정보",
                "&6&l| &f발급 대상: &e" + (type.isDaily() ? "일간" : "주간"),
                "&6&l| &f퀘스트 개수: &e" + questCount + "개"));
        setItem(4, icon(Material.PLAYER_HEAD, "&c&l[거절]", "&7선택을 취소하고 이전으로 돌아갑니다."));
    }

    @Override
    protected void onClick(Player player, InventoryClickEvent event) {
        super.onClick(player, event);
        int slot = event.getRawSlot();
        if (slot == 0) {
            if (!canManage) {
                player.sendMessage(org.bukkit.ChatColor.RED + "[Skyblock] 퀘스트를 발급할 권한이 없습니다.");
                manager().openQuestHub(player);
                return;
            }
            manager().assignQuest(player, type, questCount);
            manager().persistQuestSelection(player, type, questCount);
        } else if (slot == 4) {
            manager().openQuestSelect(player, type, canManage);
        }
    }
}
