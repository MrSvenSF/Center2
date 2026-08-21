package net.managerhub.center.common.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.managerhub.center.common.config.ConfigurationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CommandNamesTest {

    @Test
    @DisplayName("valid names are accepted and normalized to lower case")
    void acceptsValidNames() throws ConfigurationException {
        assertEquals("center", CommandNames.validate("root.label", "center"));
        assertEquals("center", CommandNames.validate("root.label", "Center"));
        assertEquals("center-2", CommandNames.validate("root.label", "Center-2"));
        assertEquals("center_2", CommandNames.validate("root.label", "center_2"));
    }

    @ParameterizedTest
    @DisplayName("invalid names are rejected")
    @ValueSource(strings = {
            "/center",
            "cen ter",
            "center:menu",
            "center!",
            "center.menu",
            "",
            "   "
    })
    void rejectsInvalidNames(final String name) {
        assertThrows(ConfigurationException.class, () -> CommandNames.validate("root.label", name));
    }

    @Test
    @DisplayName("a too long name is rejected")
    void rejectsTooLongName() {
        final String tooLong = "a".repeat(CommandNames.MAX_LENGTH + 1);
        assertThrows(ConfigurationException.class, () -> CommandNames.validate("root.label", tooLong));
    }

    @Test
    @DisplayName("a missing name is rejected")
    void rejectsNull() {
        assertThrows(ConfigurationException.class, () -> CommandNames.validate("root.label", null));
    }
}
