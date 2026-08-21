package net.managerhub.center.paper.config;

import org.bukkit.Material;

/**
 * The frame every MHCenter2 menu file configures: title, size and background.
 *
 * @param title          visible menu title as raw MiniMessage text
 * @param rows           number of rows, 3 to 6
 * @param fillerEnabled  whether empty slots are filled with a background item
 * @param fillerMaterial material of the background item
 */
public record MenuLayout(String title, int rows, boolean fillerEnabled, Material fillerMaterial) {

    /** Smallest allowed number of rows, a menu always has at least 27 slots. */
    public static final int MIN_ROWS = 3;

    /** Largest allowed number of rows. */
    public static final int MAX_ROWS = 6;

    /** @return the number of inventory slots. */
    public int size() {
        return rows * 9;
    }
}
