package net.managerhub.center.paper.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import net.managerhub.center.common.command.CommandPath;
import net.managerhub.center.common.command.CommandSpec;
import net.managerhub.center.common.command.CommandsDefinition;
import net.managerhub.center.common.config.ConfigurationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandsConfigLoaderTest {

    private static final String DEFAULT_FILE = """
            config-version: 3

            commands:
              center-info:
                enabled: true
                command: "center info"
                aliases:

              modules-reload:
                enabled: true
                command: "center modules reload"
                aliases:

              modules-enable:
                enabled: true
                command: "center modules enable"
                aliases:

              modules-disable:
                enabled: true
                command: "center modules disable"
                aliases:
            """;

    @TempDir
    Path directory;

    @Test
    @DisplayName("the three module actions are read from Commands.yml")
    void readsTheModuleActions() throws Exception {
        final CommandsDefinition definition = load(DEFAULT_FILE);

        assertEquals("center modules reload", path(definition, CommandsDefinition.MODULES_RELOAD_KEY));
        assertEquals("center modules enable", path(definition, CommandsDefinition.MODULES_ENABLE_KEY));
        assertEquals("center modules disable", path(definition, CommandsDefinition.MODULES_DISABLE_KEY));
        assertEquals(4, definition.enabledCommands().size());
    }

    @Test
    @DisplayName("a switched off module action is not registered but still validated")
    void respectsEnabledFalse() throws Exception {
        final CommandsDefinition definition = load(DEFAULT_FILE.replaceFirst(
                "  modules-reload:\n    enabled: true", "  modules-reload:\n    enabled: false"));

        assertTrue(definition.command(CommandsDefinition.MODULES_RELOAD_KEY).isPresent());
        assertFalse(definition.enabledCommands().stream()
                .anyMatch(spec -> spec.key().equals(CommandsDefinition.MODULES_RELOAD_KEY)));
    }

    @Test
    @DisplayName("a renamed module action keeps its internal key")
    void acceptsARenamedPath() throws Exception {
        final CommandsDefinition definition =
                load(DEFAULT_FILE.replace("\"center modules reload\"", "\"center mod rescan\""));

        final CommandSpec spec = definition.command(CommandsDefinition.MODULES_RELOAD_KEY).orElseThrow();
        assertEquals("center mod rescan", spec.path().display());
        assertEquals("center", spec.path().rootName());
    }

    @Test
    @DisplayName("an alias of a module action is accepted")
    void acceptsAnAlias() throws Exception {
        final CommandsDefinition definition = load(DEFAULT_FILE.replace(
                "    command: \"center modules enable\"\n    aliases:",
                "    command: \"center modules enable\"\n    aliases:\n      - \"center modon\""));

        final CommandSpec spec = definition.command(CommandsDefinition.MODULES_ENABLE_KEY).orElseThrow();
        assertEquals(List.of("center modules enable", "center modon"),
                spec.allPaths().stream().map(CommandPath::display).toList());
    }

    @Test
    @DisplayName("a configured path may not take the fixed reload command")
    void refusesConflictWithTheReloadCommand() {
        assertThrows(ConfigurationException.class,
                () -> load(DEFAULT_FILE.replace("\"center modules reload\"", "\"center reload\"")));
    }

    @Test
    @DisplayName("a configured path may not take the fixed module overview")
    void refusesConflictWithTheModuleOverview() {
        assertThrows(ConfigurationException.class,
                () -> load(DEFAULT_FILE.replace("\"center modules enable\"", "\"center modules\"")));
    }

    @Test
    @DisplayName("two module actions may not use the same path")
    void refusesDuplicatePaths() {
        assertThrows(ConfigurationException.class,
                () -> load(DEFAULT_FILE.replace("\"center modules disable\"", "\"center modules enable\"")));
    }

    @Test
    @DisplayName("an alias that takes a fixed system command is refused")
    void refusesConflictingAlias() {
        assertThrows(ConfigurationException.class, () -> load(DEFAULT_FILE.replace(
                "    command: \"center modules disable\"\n    aliases:",
                "    command: \"center modules disable\"\n    aliases:\n      - \"center modules\"")));
    }

    @Test
    @DisplayName("an invalid command path is refused")
    void refusesInvalidPath() {
        assertThrows(ConfigurationException.class,
                () -> load(DEFAULT_FILE.replace("\"center modules enable\"", "\"/center modules on\"")));
    }

    @Test
    @DisplayName("a missing module action is refused")
    void refusesMissingEntry() {
        final String withoutDisable = DEFAULT_FILE.substring(0, DEFAULT_FILE.indexOf("  modules-disable:"));

        assertThrows(ConfigurationException.class, () -> load(withoutDisable));
    }

    private static String path(final CommandsDefinition definition, final String key) {
        return definition.command(key).orElseThrow().path().display();
    }

    private CommandsDefinition load(final String content) throws ConfigurationException, IOException {
        final Path file = directory.resolve("Commands.yml");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return CommandsConfigLoader.load(file);
    }
}
