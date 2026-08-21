package net.managerhub.center.paper.config;

import org.bukkit.command.CommandSender;

/**
 * Validated content of {@code Permissions.yml}.
 *
 * <p>The record only holds the configured nodes. What a function really requires
 * is built here as a {@link PermissionGate}, and commands, tab completion and the
 * menu all ask exactly these gates. There is no second authorization logic
 * anywhere else.</p>
 *
 * <p>Two rules hold for every gate:</p>
 * <ul>
 *   <li>{@code admin-all} alone is enough for every administrative function.</li>
 *   <li>A more specific permission never opens the general admin barrier: it only
 *       counts together with {@code admin}, and a module action additionally only
 *       together with {@code modules}.</li>
 * </ul>
 *
 * @param adminAll       master permission of the whole MHCenter2 admin area
 * @param admin          permission of the MHCenter2 admin area
 * @param reload         permission of {@code /center reload}
 * @param modules        permission of the module overview and the module menu
 * @param modulesReload  permission of the module reload command
 * @param modulesEnable  permission that starts a module
 * @param modulesDisable permission that stops a module
 */
public record PermissionsSettings(PermissionSetting adminAll,
                                  PermissionSetting admin,
                                  PermissionSetting reload,
                                  PermissionSetting modules,
                                  PermissionSetting modulesReload,
                                  PermissionSetting modulesEnable,
                                  PermissionSetting modulesDisable) {

    /** @return the gate of the admin button, the admin menu and the server status. */
    public PermissionGate adminGate() {
        return sender -> masterOr(sender, admin);
    }

    /** @return the gate of {@code /center reload}. */
    public PermissionGate reloadGate() {
        return sender -> masterOr(sender, admin, reload);
    }

    /** @return the gate of the module overview, the module area and the module detail view. */
    public PermissionGate modulesGate() {
        return sender -> masterOr(sender, admin, modules);
    }

    /** @return the gate that reads the module folder again. */
    public PermissionGate modulesReloadGate() {
        return sender -> masterOr(sender, admin, modules, modulesReload);
    }

    /** @return the gate that starts a module. */
    public PermissionGate modulesEnableGate() {
        return sender -> masterOr(sender, admin, modules, modulesEnable);
    }

    /** @return the gate that stops a module. */
    public PermissionGate modulesDisableGate() {
        return sender -> masterOr(sender, admin, modules, modulesDisable);
    }

    /**
     * @param required every permission that is needed together
     * @return {@code true} with the master permission alone, otherwise only if the
     *         sender has all of the required permissions
     */
    private boolean masterOr(final CommandSender sender, final PermissionSetting... required) {
        if (adminAll.allows(sender)) {
            return true;
        }
        for (final PermissionSetting permission : required) {
            if (!permission.allows(sender)) {
                return false;
            }
        }
        return true;
    }
}
