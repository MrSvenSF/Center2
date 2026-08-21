package net.managerhub.center.paper.config;

/**
 * Validated content of {@code Menus/CenterInfo.yml}.
 *
 * <p>The creator and the organization entry only expose their slot. Their name,
 * identity and description are fixed in the code and can neither be renamed nor
 * disabled.</p>
 *
 * @param layout           title, size and background of the menu
 * @param creatorSlot      slot of the fixed creator entry
 * @param organizationSlot slot of the fixed organization entry
 * @param admin            look of the admin button
 * @param adminName        visible name of the admin button as raw MiniMessage text
 * @param close            look of the close button
 */
public record CenterInfoMenuSettings(MenuLayout layout,
                                     int creatorSlot,
                                     int organizationSlot,
                                     MenuItemSettings admin,
                                     String adminName,
                                     MenuItemSettings close) {
}
