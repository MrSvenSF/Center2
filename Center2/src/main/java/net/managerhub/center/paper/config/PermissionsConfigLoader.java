package net.managerhub.center.paper.config;

import java.nio.file.Path;
import java.util.Set;

import net.managerhub.center.Center;
import net.managerhub.center.common.config.ConfigurationException;
import net.managerhub.center.common.permission.Permissions;

/**
 * Reads and validates {@code Permissions.yml}.
 *
 * <p>Only the entries Center2 knows may appear, and each of them only carries a
 * node and the {@code op} rule.</p>
 */
final class PermissionsConfigLoader {

    private static final Set<String> KNOWN_PERMISSIONS = Set.of(
            Permissions.ADMIN_ALL_KEY,
            Permissions.ADMIN_KEY,
            Permissions.RELOAD_KEY,
            Permissions.MODULES_KEY,
            Permissions.MODULES_RELOAD_KEY,
            Permissions.MODULES_ENABLE_KEY,
            Permissions.MODULES_DISABLE_KEY);
    private static final Set<String> ENTRY_KEYS = Set.of("permission", "op");

    private PermissionsConfigLoader() {
        throw new AssertionError("No instances.");
    }

    static PermissionsSettings load(final Path file) throws ConfigurationException {
        final YamlReader reader = YamlReader.read(file, Center.PERMISSIONS_FILE);
        reader.requireConfigVersion();
        reader.requireExactKeys("permissions", KNOWN_PERMISSIONS);

        return new PermissionsSettings(
                permission(reader, Permissions.ADMIN_ALL_KEY),
                permission(reader, Permissions.ADMIN_KEY),
                permission(reader, Permissions.RELOAD_KEY),
                permission(reader, Permissions.MODULES_KEY),
                permission(reader, Permissions.MODULES_RELOAD_KEY),
                permission(reader, Permissions.MODULES_ENABLE_KEY),
                permission(reader, Permissions.MODULES_DISABLE_KEY));
    }

    private static PermissionSetting permission(final YamlReader reader, final String key)
            throws ConfigurationException {
        final String path = "permissions." + key;
        for (final String entry : reader.requireSection(path).getKeys(false)) {
            if (!ENTRY_KEYS.contains(entry)) {
                throw reader.error("'" + path + "' contains the unknown entry '" + entry
                        + "'. A permission only knows: permission and op.");
            }
        }
        // Only the master entry may end with the wildcard, every other node stays strict.
        return new PermissionSetting(
                Permissions.normalizeNode(path + ".permission", reader.requireString(path + ".permission"),
                        Permissions.ADMIN_ALL_KEY.equals(key)),
                reader.requireBoolean(path + ".op"));
    }
}
