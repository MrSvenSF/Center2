package net.managerhub.center.paper.config;

/**
 * Validated content of {@code Menus/CenterAdmin.yml}.
 *
 * @param layout       title, size and background of the menu
 * @param status       look of the status entry
 * @param serverStatus look of the server status entry
 * @param modules      look of the module entry
 * @param back         look of the back button
 */
public record CenterAdminMenuSettings(MenuLayout layout,
                                      MenuItemSettings status,
                                      MenuItemSettings serverStatus,
                                      MenuItemSettings modules,
                                      MenuItemSettings back) {
}
