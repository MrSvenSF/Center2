package net.managerhub.center.paper.config;

import net.managerhub.center.common.command.CommandsDefinition;
import net.managerhub.center.common.language.Language;
import net.managerhub.center.common.remote.RemoteSettings;

/**
 * One immutable snapshot of every configuration file of Center2 on Paper.
 *
 * <p>A snapshot only exists when every value in every file was accepted, so the
 * running plugin never sees a half validated configuration.</p>
 *
 * @param main             validated content of {@code MainConfig.yml}
 * @param permissions      validated content of {@code Permissions.yml}
 * @param commands         validated command layout, already switched off where a menu is off
 * @param language         validated texts of the selected language
 * @param centerInfoMenu   validated content of {@code Menus/CenterInfo.yml}
 * @param centerAdminMenu  validated content of {@code Menus/CenterAdmin.yml}
 * @param serverStatusMenu validated content of {@code Menus/CenterServerStatus.yml}
 */
public record CenterConfiguration(MainSettings main,
                                  PermissionsSettings permissions,
                                  CommandsDefinition commands,
                                  Language language,
                                  CenterInfoMenuSettings centerInfoMenu,
                                  CenterAdminMenuSettings centerAdminMenu,
                                  ServerStatusMenuSettings serverStatusMenu) {

    /** @return {@code true} if the Center-Info menu may be opened. */
    public boolean centerInfoMenuEnabled() {
        return main.centerInfoMenuEnabled();
    }

    /** @return the optional remote database of the network. */
    public RemoteSettings remote() {
        return main.remote();
    }
}
