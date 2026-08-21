package net.managerhub.center.paper.config;

import org.bukkit.Material;

/**
 * Configurable look of one menu entry.
 *
 * <p>The visible name of an entry comes from the language file. Only the admin
 * button of {@code Menus/CenterInfo.yml} carries its own name, see
 * {@link CenterInfoMenuSettings}.</p>
 *
 * @param slot     inventory slot, counted from 0
 * @param material item material
 */
public record MenuItemSettings(int slot, Material material) {
}
