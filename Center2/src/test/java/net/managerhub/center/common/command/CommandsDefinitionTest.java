package net.managerhub.center.common.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.managerhub.center.common.config.ConfigurationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CommandsDefinitionTest {

    private static CommandSpec centerInfo(final boolean enabled, final String path, final String... aliases)
            throws ConfigurationException {
        return CommandSpec.of(CommandsDefinition.CENTER_INFO_KEY, "commands." + CommandsDefinition.CENTER_INFO_KEY,
                enabled, path, List.of(aliases));
    }

    @Test
    @DisplayName("a valid layout is accepted")
    void acceptsValidLayout() throws ConfigurationException {
        final CommandsDefinition definition =
                CommandsDefinition.validated(List.of(centerInfo(true, "center info", "network info")));

        final CommandSpec spec = definition.command(CommandsDefinition.CENTER_INFO_KEY).orElseThrow();
        assertEquals("center info", spec.path().display());
        assertEquals(List.of("center info", "network info"),
                spec.allPaths().stream().map(CommandPath::display).toList());
        assertEquals(1, definition.enabledCommands().size());
    }

    @Test
    @DisplayName("a renamed command path keeps the internal key")
    void renamingKeepsTheKey() throws ConfigurationException {
        final CommandsDefinition definition = CommandsDefinition.validated(List.of(centerInfo(true, "network info")));

        final CommandSpec spec = definition.command(CommandsDefinition.CENTER_INFO_KEY).orElseThrow();
        assertEquals(CommandsDefinition.CENTER_INFO_KEY, spec.key());
        assertEquals("network", spec.path().rootName());
    }

    @Test
    @DisplayName("a disabled command is not registered but still validated")
    void validatesDisabledCommands() throws ConfigurationException {
        final CommandsDefinition definition = CommandsDefinition.validated(List.of(centerInfo(false, "center info")));

        assertTrue(definition.enabledCommands().isEmpty());
        assertTrue(definition.command(CommandsDefinition.CENTER_INFO_KEY).isPresent());
        assertThrows(ConfigurationException.class,
                () -> CommandsDefinition.validated(List.of(centerInfo(false, "center reload"))));
    }

    @Test
    @DisplayName("a path that repeats one of its own aliases is rejected")
    void rejectsDuplicatePath() throws ConfigurationException {
        final List<CommandSpec> commands = List.of(centerInfo(true, "center info", "center info"));

        assertThrows(ConfigurationException.class, () -> CommandsDefinition.validated(commands));
    }

    @Test
    @DisplayName("upper and lower case count as the same path")
    void rejectsDuplicatePathIgnoringCase() throws ConfigurationException {
        final List<CommandSpec> commands = List.of(centerInfo(true, "center info", "Center INFO"));

        assertThrows(ConfigurationException.class, () -> CommandsDefinition.validated(commands));
    }

    @Test
    @DisplayName("the fixed reload path cannot be taken over by a configured command")
    void rejectsPathOfTheReloadSystemCommand() throws ConfigurationException {
        final List<CommandSpec> byPath = List.of(centerInfo(true, "center reload"));
        final List<CommandSpec> byAlias = List.of(centerInfo(true, "center info", "center reload"));

        assertThrows(ConfigurationException.class, () -> CommandsDefinition.validated(byPath));
        assertThrows(ConfigurationException.class, () -> CommandsDefinition.validated(byAlias));
    }

    @Test
    @DisplayName("the reload path is fixed and belongs to the reload system command")
    void reloadPathIsFixed() {
        assertEquals("center reload", CommandsDefinition.RELOAD_PATH.display());
        assertEquals("center", CommandsDefinition.RELOAD_PATH.rootName());
        assertEquals(List.of("reload"), CommandsDefinition.RELOAD_PATH.tail());
    }

    @Test
    @DisplayName("a command can be switched off afterwards without touching the rest")
    void switchesOneCommandOff() throws ConfigurationException {
        final CommandsDefinition definition = CommandsDefinition.validated(List.of(centerInfo(true, "center info")));

        final CommandsDefinition disabled = definition.withDisabled(CommandsDefinition.CENTER_INFO_KEY);

        assertTrue(definition.command(CommandsDefinition.CENTER_INFO_KEY).orElseThrow().enabled());
        assertFalse(disabled.command(CommandsDefinition.CENTER_INFO_KEY).orElseThrow().enabled());
        assertEquals("center info",
                disabled.command(CommandsDefinition.CENTER_INFO_KEY).orElseThrow().path().display());
    }
}
