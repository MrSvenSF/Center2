package net.managerhub.center.common.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.managerhub.center.common.config.ConfigurationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PermissionsTest {

    @Test
    @DisplayName("a node is accepted and normalized to lower case")
    void acceptsValidNodes() throws ConfigurationException {
        assertEquals("center.admin", Permissions.normalizeNode("permissions.admin.permission", "center.admin"));
        assertEquals("center.admin.reload",
                Permissions.normalizeNode("permissions.reload.permission", "Center.Admin.Reload"));
        assertEquals("my-network.center_2", Permissions.normalizeNode("path", " my-network.center_2 "));
    }

    @ParameterizedTest
    @DisplayName("an invalid node is rejected")
    @ValueSource(strings = {
            "",
            "   ",
            ".center.admin",
            "center.admin.",
            "center..admin",
            "center admin",
            "center.*",
            "center.admin!"
    })
    void rejectsInvalidNodes(final String node) {
        assertThrows(ConfigurationException.class, () -> Permissions.normalizeNode("path", node));
    }

    @Test
    @DisplayName("a missing node is rejected")
    void rejectsNull() {
        assertThrows(ConfigurationException.class, () -> Permissions.normalizeNode("path", null));
    }

    @Test
    @DisplayName("the master node may end with the wildcard")
    void acceptsTheMasterWildcard() throws ConfigurationException {
        assertEquals("center.admin.*",
                Permissions.normalizeNode("permissions.admin-all.permission", "center.admin.*", true));
        assertEquals("center.admin.*",
                Permissions.normalizeNode("permissions.admin-all.permission", "Center.Admin.*", true));
    }

    @Test
    @DisplayName("a normal node never accepts the wildcard")
    void refusesTheWildcardForANormalNode() {
        assertThrows(ConfigurationException.class,
                () -> Permissions.normalizeNode("permissions.admin.permission", "center.admin.*"));
        assertThrows(ConfigurationException.class,
                () -> Permissions.normalizeNode("permissions.admin.permission", "center.admin.*", false));
    }

    @ParameterizedTest
    @DisplayName("the wildcard stays refused everywhere except at the end")
    @ValueSource(strings = {
            "*",
            "*.admin",
            "center.*.modules",
            "center.*.modules.*",
            "center.admin.**",
            "center.admin.*x",
            "center.admin.x*",
            "center..*"
    })
    void refusesEveryOtherWildcard(final String node) {
        assertThrows(ConfigurationException.class,
                () -> Permissions.normalizeNode("permissions.admin-all.permission", node, true));
    }
}
