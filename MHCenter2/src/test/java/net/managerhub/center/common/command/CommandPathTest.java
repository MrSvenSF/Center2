package net.managerhub.center.common.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.managerhub.center.common.config.ConfigurationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CommandPathTest {

    @Test
    @DisplayName("a path with several parts is split into name and arguments")
    void splitsSeveralParts() throws ConfigurationException {
        final CommandPath path = CommandPath.of("commands.center-info.command", "center info");

        assertEquals(List.of("center", "info"), path.segments());
        assertEquals("center", path.rootName());
        assertEquals(List.of("info"), path.tail());
        assertEquals("center info", path.display());
    }

    @Test
    @DisplayName("a path with one part has no arguments")
    void acceptsSinglePart() throws ConfigurationException {
        final CommandPath path = CommandPath.of("commands.center-info.command", "center");

        assertEquals("center", path.rootName());
        assertTrue(path.tail().isEmpty());
    }

    @Test
    @DisplayName("a path is normalized to lower case and extra spaces are ignored")
    void normalizesPath() throws ConfigurationException {
        assertEquals("center info", CommandPath.of("path", "Center   INFO").display());
        assertEquals("network info", CommandPath.of("path", "  network info  ").display());
    }

    @ParameterizedTest
    @DisplayName("an invalid path is rejected")
    @ValueSource(strings = {
            "",
            "   ",
            "/center info",
            "center:info",
            "center info!",
            "center.info"
    })
    void rejectsInvalidPaths(final String raw) {
        assertThrows(ConfigurationException.class, () -> CommandPath.of("commands.center-info.command", raw));
    }

    @Test
    @DisplayName("a missing path is rejected")
    void rejectsNull() {
        assertThrows(ConfigurationException.class, () -> CommandPath.of("commands.center-info.command", null));
    }
}
