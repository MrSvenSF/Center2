package net.managerhub.center.paper.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.Set;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PermissionsSettingsTest {

    private static final String MASTER = "center.admin.*";
    private static final String ADMIN = "center.admin";
    private static final String RELOAD = "center.admin.reload";
    private static final String MODULES = "center.admin.modules";
    private static final String MODULES_RELOAD = "center.admin.modules.reload";
    private static final String MODULES_ENABLE = "center.admin.modules.enable";
    private static final String MODULES_DISABLE = "center.admin.modules.disable";

    private static final PermissionsSettings PERMISSIONS = new PermissionsSettings(
            new PermissionSetting(MASTER, false),
            new PermissionSetting(ADMIN, false),
            new PermissionSetting(RELOAD, false),
            new PermissionSetting(MODULES, false),
            new PermissionSetting(MODULES_RELOAD, false),
            new PermissionSetting(MODULES_ENABLE, false),
            new PermissionSetting(MODULES_DISABLE, false));

    @Test
    @DisplayName("the master permission alone opens every admin function")
    void masterPermissionIsEnough() {
        final Player master = player(MASTER);

        assertTrue(PERMISSIONS.adminGate().allows(master));
        assertTrue(PERMISSIONS.reloadGate().allows(master));
        assertTrue(PERMISSIONS.modulesGate().allows(master));
        assertTrue(PERMISSIONS.modulesReloadGate().allows(master));
        assertTrue(PERMISSIONS.modulesEnableGate().allows(master));
        assertTrue(PERMISSIONS.modulesDisableGate().allows(master));
    }

    @Test
    @DisplayName("the admin permission alone opens no module function")
    void adminAloneIsNotEnoughForModules() {
        final Player admin = player(ADMIN);

        assertTrue(PERMISSIONS.adminGate().allows(admin));
        assertFalse(PERMISSIONS.modulesGate().allows(admin));
        assertFalse(PERMISSIONS.modulesReloadGate().allows(admin));
        assertFalse(PERMISSIONS.modulesEnableGate().allows(admin));
        assertFalse(PERMISSIONS.modulesDisableGate().allows(admin));
    }

    @Test
    @DisplayName("a sub permission alone never opens the admin barrier")
    void subPermissionAloneIsRefused() {
        // This is the important one: center.admin.modules.reload without
        // center.admin must not make anybody a module administrator.
        assertFalse(PERMISSIONS.modulesReloadGate().allows(player(MODULES_RELOAD)));
        assertFalse(PERMISSIONS.modulesEnableGate().allows(player(MODULES_ENABLE)));
        assertFalse(PERMISSIONS.modulesDisableGate().allows(player(MODULES_DISABLE)));
        assertFalse(PERMISSIONS.reloadGate().allows(player(RELOAD)));

        // Not even together with the module permission, the admin gate is missing.
        assertFalse(PERMISSIONS.modulesReloadGate().allows(player(MODULES, MODULES_RELOAD)));
    }

    @Test
    @DisplayName("admin together with modules opens the module overview")
    void adminAndModulesOpenTheOverview() {
        final Player viewer = player(ADMIN, MODULES);

        assertTrue(PERMISSIONS.modulesGate().allows(viewer));
        assertFalse(PERMISSIONS.modulesReloadGate().allows(viewer));
        assertFalse(PERMISSIONS.modulesEnableGate().allows(viewer));
        assertFalse(PERMISSIONS.modulesDisableGate().allows(viewer));
    }

    @Test
    @DisplayName("every module action needs admin, modules and its own permission")
    void moduleActionsNeedTheCompleteChain() {
        assertTrue(PERMISSIONS.modulesReloadGate().allows(player(ADMIN, MODULES, MODULES_RELOAD)));
        assertTrue(PERMISSIONS.modulesEnableGate().allows(player(ADMIN, MODULES, MODULES_ENABLE)));
        assertTrue(PERMISSIONS.modulesDisableGate().allows(player(ADMIN, MODULES, MODULES_DISABLE)));

        // The permission of one action never covers another action.
        assertFalse(PERMISSIONS.modulesEnableGate().allows(player(ADMIN, MODULES, MODULES_RELOAD)));
        assertFalse(PERMISSIONS.modulesDisableGate().allows(player(ADMIN, MODULES, MODULES_ENABLE)));
    }

    @Test
    @DisplayName("the reload command needs admin and the reload permission")
    void reloadNeedsAdminAndReload() {
        assertTrue(PERMISSIONS.reloadGate().allows(player(ADMIN, RELOAD)));
        assertFalse(PERMISSIONS.reloadGate().allows(player(ADMIN)));
    }

    @Test
    @DisplayName("a player without any permission is refused everywhere")
    void playerWithoutPermissionsIsRefused() {
        final Player nobody = player();

        assertFalse(PERMISSIONS.adminGate().allows(nobody));
        assertFalse(PERMISSIONS.reloadGate().allows(nobody));
        assertFalse(PERMISSIONS.modulesGate().allows(nobody));
        assertFalse(PERMISSIONS.modulesReloadGate().allows(nobody));
        assertFalse(PERMISSIONS.modulesEnableGate().allows(nobody));
        assertFalse(PERMISSIONS.modulesDisableGate().allows(nobody));
    }

    /**
     * @param granted the permission nodes this player really has
     * @return a player that only answers the two permission questions
     */
    private static Player player(final String... granted) {
        final Set<String> nodes = Set.of(granted);
        return (Player) Proxy.newProxyInstance(
                PermissionsSettingsTest.class.getClassLoader(),
                new Class<?>[] {Player.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "isPermissionSet", "hasPermission" -> nodes.contains(String.valueOf(arguments[0]));
                    case "toString" -> "Player" + nodes;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
