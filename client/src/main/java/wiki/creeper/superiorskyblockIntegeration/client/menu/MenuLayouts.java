package wiki.creeper.superiorskyblockIntegeration.client.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;

/**
 * Provides shared slot layouts and decoration helpers for inventory menus.
 */
final class MenuLayouts {

    private static final int[] FRAME_27 = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26
    };

    private static final int[] FRAME_36 = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 17,
            18, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35
    };

    private static final int[] FRAME_45 = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 17,
            18, 26,
            27, 35,
            36, 37, 38, 39, 40, 41, 42, 43, 44
    };

    private static final int[] FRAME_54 = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 17,
            18, 26,
            27, 35,
            36, 44,
            45, 46, 47, 48, 49, 50, 51, 52, 53
    };

    private static final int[] CONTENT_27 = {
            10, 11, 12, 13, 14, 15, 16
    };

    private static final int[] CONTENT_36 = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25
    };

    private static final int[] CONTENT_45 = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private static final int[] CONTENT_54 = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private MenuLayouts() {
    }

    static void applyFrame(AbstractMenu menu, Inventory inventory, ItemStack border) {
        if (inventory == null || border == null) {
            return;
        }
        int[] frame = frameSlots(inventory.getSize());
        if (frame.length == 0) {
            return;
        }
        Arrays.stream(frame).forEach(slot -> menu.setItem(slot, border.clone()));
    }

    static int[] primarySlots(int size) {
        return switch (size) {
            case 27 -> CONTENT_27;
            case 36 -> CONTENT_36;
            case 45 -> CONTENT_45;
            case 54 -> CONTENT_54;
            default -> new int[0];
        };
    }

    private static int[] frameSlots(int size) {
        return switch (size) {
            case 27 -> FRAME_27;
            case 36 -> FRAME_36;
            case 45 -> FRAME_45;
            case 54 -> FRAME_54;
            default -> new int[0];
        };
    }
}
