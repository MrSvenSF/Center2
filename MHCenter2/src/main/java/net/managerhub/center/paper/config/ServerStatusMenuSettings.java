package net.managerhub.center.paper.config;

/**
 * Validated content of {@code Menus/CenterServerStatus.yml}.
 *
 * <p>The Paper servers of the network are not configured here. They come from
 * the server list of the proxy and are placed into the free slots by the code.</p>
 *
 * @param layout   title, size and background of the menu
 * @param velocity look of the proxy entry
 * @param back     look of the back button
 */
public record ServerStatusMenuSettings(MenuLayout layout, MenuItemSettings velocity, MenuItemSettings back) {
}
