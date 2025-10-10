package wiki.creeper.superiorskyblockIntegeration.client.menu;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Base class for simple inventory menus.
 */
abstract class AbstractMenu {

    private static final Material DEFAULT_BACKGROUND = Material.GRAY_STAINED_GLASS_PANE;
    private static final Material DEFAULT_FRAME = Material.BLACK_STAINED_GLASS_PANE;

    private final IslandMenuManager manager;
    private Inventory inventory;
    private UUID viewer;

    protected AbstractMenu(IslandMenuManager manager) {
        this.manager = manager;
    }

    final void open(Player player) {
        this.viewer = player.getUniqueId();
        this.inventory = Bukkit.createInventory(player, size(), title(player));
        build(player, inventory);
        player.openInventory(inventory);
        onOpen(player);
    }

    protected abstract String title(Player player);

    protected abstract int size();

    protected abstract void build(Player player, Inventory inventory);

    protected void onOpen(Player player) {
        // optional override
    }

    protected void onClose(Player player) {
        // optional override
    }

    protected void onClick(Player player, InventoryClickEvent event) {
        event.setCancelled(true);
    }

    final void handleClose(Player player, Inventory closedInventory) {
        if (matches(closedInventory)) {
            onClose(player);
        }
    }

    final void handleClick(Player player, InventoryClickEvent event) {
        onClick(player, event);
    }

    final boolean matches(Inventory inventory) {
        return this.inventory != null && this.inventory.equals(inventory);
    }

    protected Inventory inventory() {
        return inventory;
    }

    protected boolean isViewing(Player player) {
        return inventory != null && player != null
                && player.getOpenInventory().getTopInventory().equals(inventory);
    }

    protected Player viewer() {
        return viewer != null ? manager.plugin().getServer().getPlayer(viewer) : null;
    }

    protected IslandMenuManager manager() {
        return manager;
    }

    protected UUID viewerId() {
        return viewer;
    }

    protected void setItem(int slot, ItemStack item) {
        if (inventory != null && slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, item);
        }
    }

    protected ItemStack icon(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            if (lore != null && lore.length > 0) {
                List<String> lines = new ArrayList<>(lore.length);
                Arrays.stream(lore).forEach(line -> lines.add(color(line)));
                meta.setLore(lines);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    protected void fill(ItemStack filler) {
        if (inventory == null) {
            return;
        }
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler != null ? filler.clone() : null);
        }
    }

    protected ItemStack glass(Material material) {
        return icon(material, " ");
    }

    protected void decorateDefault(Inventory inventory) {
        decorate(inventory, DEFAULT_BACKGROUND, DEFAULT_FRAME);
    }

    protected void decorate(Inventory inventory, Material background, Material frame) {
        if (inventory == null) {
            return;
        }
        ItemStack backgroundItem = glass(background);
        fill(backgroundItem);
        MenuLayouts.applyFrame(this, inventory, glass(frame));
    }

    protected int[] primarySlots() {
        Inventory inventory = inventory();
        int size = inventory != null ? inventory.getSize() : size();
        return MenuLayouts.primarySlots(size);
    }

    protected void placeNavigation(ItemStack left, ItemStack middle, ItemStack right) {
        Inventory inventory = inventory();
        if (inventory == null) {
            return;
        }
        int size = inventory.getSize();
        if (size >= 9 && left != null) {
            setItem(size - 9, left);
        }
        if (size >= 5 && middle != null) {
            setItem(size - 5, middle);
        }
        if (size >= 1 && right != null) {
            setItem(size - 1, right);
        }
    }

    protected ItemStack backButton(String target) {
        return icon(Material.ARROW, "&a뒤로가기", "&7" + target + "으로 돌아갑니다.");
    }

    protected ItemStack closeButton() {
        return icon(Material.BARRIER, "&c닫기", "&7메뉴를 닫습니다.");
    }

    protected ItemStack refreshButton() {
        return icon(Material.CLOCK, "&e새로 고침", "&7최신 정보를 다시 불러옵니다.");
    }

    protected ItemStack mainMenuButton() {
        return icon(Material.COMPASS, "&a메인 메뉴", "&7팜 관리 홈으로 이동합니다.");
    }

    protected void runSync(Runnable runnable) {
        manager.plugin().getServer().getScheduler().runTask(manager.plugin(), runnable);
    }

    private String color(String input) {
        return input != null ? ChatColor.translateAlternateColorCodes('&', input) : null;
    }

    protected ItemStack playerHead(UUID uuid, String nameHint, String displayName, String... lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta baseMeta = head.getItemMeta();
        if (!(baseMeta instanceof SkullMeta meta)) {
            return head;
        }
        if (uuid != null) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
            meta.setOwningPlayer(offline);
        } else if (nameHint != null) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(nameHint);
            meta.setOwningPlayer(offline);
        }
        meta.setDisplayName(color(displayName));
        if (lore != null && lore.length > 0) {
            List<String> lines = new ArrayList<>(lore.length);
            Arrays.stream(lore).forEach(line -> lines.add(color(line)));
            meta.setLore(lines);
        }
        head.setItemMeta(meta);
        return head;
    }

    protected ItemStack withStringTag(ItemStack item, String key, String value) {
        if (item == null) {
            return null;
        }
        ItemStack clone = item.clone();
        ItemMeta meta = clone.getItemMeta();
        if (meta != null) {
            NamespacedKey namespacedKey = new NamespacedKey(manager().plugin(), key);
            if (value == null) {
                meta.getPersistentDataContainer().remove(namespacedKey);
            } else {
                meta.getPersistentDataContainer().set(namespacedKey, PersistentDataType.STRING, value);
            }
            clone.setItemMeta(meta);
        }
        return clone;
    }

    protected String readStringTag(ItemStack item, String key) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        NamespacedKey namespacedKey = new NamespacedKey(manager().plugin(), key);
        return item.getItemMeta().getPersistentDataContainer().get(namespacedKey, PersistentDataType.STRING);
    }
}
