package net.managerhub.center.paper.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PermissionSettingTest {

    private static final String NODE = "center.admin";

    @Test
    @DisplayName("with op: false the permission itself is required")
    void strictSettingNeedsThePermission() {
        final PermissionSetting setting = new PermissionSetting(NODE, false);

        assertTrue(setting.allows(player(true, true)));
        assertFalse(setting.allows(player(false, false)));
    }

    @Test
    @DisplayName("with op: false an operator without the permission is refused")
    void strictSettingIgnoresOperators() {
        final PermissionSetting setting = new PermissionSetting(NODE, false);

        // Bukkit answers hasPermission with true for an operator as long as the
        // permission was never set. That must not open the admin area.
        assertFalse(setting.allows(player(false, true)));
    }

    @Test
    @DisplayName("with op: false an explicitly removed permission is refused")
    void strictSettingRespectsNegatedPermission() {
        final PermissionSetting setting = new PermissionSetting(NODE, false);

        assertFalse(setting.allows(player(true, false)));
    }

    @Test
    @DisplayName("with op: true the normal platform behaviour applies")
    void relaxedSettingFollowsThePlatform() {
        final PermissionSetting setting = new PermissionSetting(NODE, true);

        assertTrue(setting.allows(player(false, true)));
        assertTrue(setting.allows(player(true, true)));
        assertFalse(setting.allows(player(true, false)));
    }

    @Test
    @DisplayName("the console is always allowed")
    void consoleIsAlwaysAllowed() {
        assertTrue(new PermissionSetting(NODE, false).allows(console()));
        assertTrue(new PermissionSetting(NODE, true).allows(console()));
    }

    /**
     * @param permissionSet what {@code isPermissionSet} answers
     * @param hasPermission what {@code hasPermission} answers
     * @return a player that only answers those two questions
     */
    private static Player player(final boolean permissionSet, final boolean hasPermission) {
        return (Player) sender(Player.class, permissionSet, hasPermission);
    }

    private static CommandSender console() {
        return (CommandSender) sender(ConsoleCommandSender.class, false, true);
    }

    private static Object sender(final Class<?> type, final boolean permissionSet, final boolean hasPermission) {
        return Proxy.newProxyInstance(
                PermissionSettingTest.class.getClassLoader(),
                new Class<?>[] {type},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "isPermissionSet" -> permissionSet;
                    case "hasPermission" -> hasPermission;
                    case "toString" -> type.getSimpleName();
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
