package net.managerhub.center.velocity.command;

import com.velocitypowered.api.command.CommandSource;

/**
 * The administrative permissions of Center2 on the proxy.
 *
 * <p>Velocity has no {@code Permissions.yml}: the proxy side of Center2 brings no
 * configuration files besides {@code MainConfig.yml} and the language files, so
 * the nodes are fixed here. They are the same nodes the Paper side uses by
 * default, and the same rule applies: the master permission alone is enough, and
 * a more specific permission never opens the general admin barrier on its
 * own.</p>
 *
 * <p>The proxy console always passes, exactly like the server console on
 * Paper.</p>
 */
public final class ProxyPermissions {

    /** Master permission for every Center2 admin function. */
    public static final String ADMIN_ALL = "center.admin.*";

    /** General admin permission. */
    public static final String ADMIN = "center.admin";

    /** Reloading the configuration of the proxy. */
    public static final String RELOAD = "center.admin.reload";

    /** Module overview. */
    public static final String MODULES = "center.admin.modules";

    /** Reading the module folder again. */
    public static final String MODULES_RELOAD = "center.admin.modules.reload";

    /** Starting a module. */
    public static final String MODULES_ENABLE = "center.admin.modules.enable";

    /** Stopping a module. */
    public static final String MODULES_DISABLE = "center.admin.modules.disable";

    private ProxyPermissions() {
        throw new AssertionError("No instances.");
    }

    /**
     * @param source   who wants to use a function
     * @param required every permission that is needed together
     * @return {@code true} with the master permission alone, otherwise only with
     *         all of the required permissions
     */
    public static boolean allows(final CommandSource source, final String... required) {
        if (source.hasPermission(ADMIN_ALL)) {
            return true;
        }
        for (final String permission : required) {
            if (!source.hasPermission(permission)) {
                return false;
            }
        }
        return true;
    }
}
