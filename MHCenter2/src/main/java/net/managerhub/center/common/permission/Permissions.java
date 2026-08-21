package net.managerhub.center.common.permission;

import java.util.Locale;
import java.util.regex.Pattern;

import net.managerhub.center.Center;
import net.managerhub.center.common.config.ConfigurationException;

/**
 * Central and only place where a MHCenter2 permission node is checked for shape.
 *
 * <p>The nodes themselves are configured in {@code Permissions.yml}. MHCenter2
 * brings no roles, no groups and no permission storage of its own; the node is
 * handed to the permission system of the platform, so LuckPerms and everything
 * else keep working as usual.</p>
 */
public final class Permissions {

    /** Internal key of the master permission that covers every administrative function. */
    public static final String ADMIN_ALL_KEY = "admin-all";

    /** Internal key of the administrative permission. */
    public static final String ADMIN_KEY = "admin";

    /** Internal key of the reload permission. */
    public static final String RELOAD_KEY = "reload";

    /** Internal key of the permission for the module overview and the module menu. */
    public static final String MODULES_KEY = "modules";

    /** Internal key of the permission that reads the module folder again. */
    public static final String MODULES_RELOAD_KEY = "modules-reload";

    /** Internal key of the permission that starts a module. */
    public static final String MODULES_ENABLE_KEY = "modules-enable";

    /** Internal key of the permission that stops a module. */
    public static final String MODULES_DISABLE_KEY = "modules-disable";

    /** The wildcard that may end the master permission, and nothing else. */
    public static final String WILDCARD = "*";

    private static final Pattern SEGMENT = Pattern.compile("[a-z0-9_-]+");

    private Permissions() {
        throw new AssertionError("No instances.");
    }

    /**
     * Validates a configured permission node and returns its normalized form.
     *
     * @param path configuration path, used for the error message
     * @param raw  raw value from {@code Permissions.yml}
     * @return the normalized (lower case) node
     * @throws ConfigurationException if the node is empty or malformed
     */
    public static String normalizeNode(final String path, final String raw) throws ConfigurationException {
        return normalizeNode(path, raw, false);
    }

    /**
     * Validates a configured permission node and returns its normalized form.
     *
     * <p>The wildcard is allowed only as the last part of the master permission,
     * so {@code center.admin.*} is accepted while {@code *.admin},
     * {@code center.*.modules}, {@code center.admin.**} and a bare {@code *} stay
     * invalid. Every other node is validated exactly as strictly as before.</p>
     *
     * @param path       configuration path, used for the error message
     * @param raw        raw value from {@code Permissions.yml}
     * @param masterNode whether this entry is the master permission and may
     *                   therefore end with the wildcard
     * @return the normalized (lower case) node
     * @throws ConfigurationException if the node is empty or malformed
     */
    public static String normalizeNode(final String path,
                                       final String raw,
                                       final boolean masterNode) throws ConfigurationException {
        if (raw == null || raw.isBlank()) {
            throw invalid(path, String.valueOf(raw), "it must not be empty");
        }
        final String node = raw.trim().toLowerCase(Locale.ROOT);
        if (node.startsWith(".") || node.endsWith(".")) {
            throw invalid(path, raw, "it must not start or end with a dot");
        }
        final String[] segments = node.split("\\.", -1);
        for (int index = 0; index < segments.length; index++) {
            final String segment = segments[index];
            if (segment.isEmpty()) {
                throw invalid(path, raw, "it must not contain empty segments");
            }
            if (SEGMENT.matcher(segment).matches()) {
                continue;
            }
            if (!isTerminalWildcard(masterNode, segments, index)) {
                throw invalid(path, raw, "the part '" + segment
                        + "' contains unsupported characters, allowed are a-z, 0-9, '_' and '-'"
                        + (masterNode ? ", and '" + WILDCARD + "' as the last part" : ""));
            }
        }
        return node;
    }

    /**
     * The wildcard is a deliberate exception for one node, never a general
     * relaxation of the validation.
     *
     * @return {@code true} if this segment is the allowed wildcard of a master node
     */
    private static boolean isTerminalWildcard(final boolean masterNode, final String[] segments, final int index) {
        return masterNode
                && WILDCARD.equals(segments[index])
                && index == segments.length - 1
                && segments.length > 1;
    }

    private static ConfigurationException invalid(final String path, final String value, final String reason) {
        return new ConfigurationException(Center.PERMISSIONS_FILE + ": '" + path
                + "' contains the invalid permission \"" + value + "\": " + reason + ".");
    }
}
