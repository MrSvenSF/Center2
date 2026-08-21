package net.managerhub.center.common.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The rule that keeps a reload from quietly taking a command away from a running
 * module.
 */
class CommandConflictsTest {

    @Test
    @DisplayName("a path a module already serves is found in the new command configuration")
    void findsTheConflict() throws Exception {
        final ModuleCommandRegistry registry = new ModuleCommandRegistry();
        registry.register("TestModule", path("center test"), sender -> { });

        final Optional<ModuleCommandRegistry.Registered> swallowed = CommandConflicts.swallowedModuleCommand(
                CommandConflicts.corePaths(CommandsDefinition.SYSTEM_PATHS, commands("center test")),
                registry.all());

        assertTrue(swallowed.isPresent());
        assertEquals("TestModule", swallowed.get().moduleId());
        assertEquals("center test", swallowed.get().path().display());
    }

    @Test
    @DisplayName("upper and lower case are the same path here too")
    void caseDoesNotHide() throws Exception {
        final ModuleCommandRegistry registry = new ModuleCommandRegistry();
        registry.register("TestModule", path("center test"), sender -> { });

        assertTrue(CommandConflicts.swallowedModuleCommand(
                Set.of("Center Test"), registry.all()).isPresent());
    }

    @Test
    @DisplayName("an alias of the core counts as a path of the core")
    void aliasesCount() throws Exception {
        final ModuleCommandRegistry registry = new ModuleCommandRegistry();
        registry.register("TestModule", path("center test"), sender -> { });
        final CommandsDefinition commands = CommandsDefinition.validated(List.of(
                CommandSpec.of("center-info", "commands.center-info", true, "center info",
                        List.of("center test"))));

        assertTrue(CommandConflicts.swallowedModuleCommand(
                CommandConflicts.corePaths(CommandsDefinition.SYSTEM_PATHS, commands),
                registry.all()).isPresent());
    }

    @Test
    @DisplayName("a switched off core command still owns its path")
    void disabledCommandsCount() throws Exception {
        final ModuleCommandRegistry registry = new ModuleCommandRegistry();
        registry.register("TestModule", path("center test"), sender -> { });
        final CommandsDefinition commands = CommandsDefinition.validated(List.of(
                CommandSpec.of("center-info", "commands.center-info", false, "center test", List.of())));

        assertTrue(CommandConflicts.swallowedModuleCommand(
                        CommandConflicts.corePaths(CommandsDefinition.SYSTEM_PATHS, commands), registry.all())
                .isPresent(), "a path that is only switched off comes back with the next reload");
    }

    @Test
    @DisplayName("a command configuration that leaves the module alone is fine")
    void noConflict() throws Exception {
        final ModuleCommandRegistry registry = new ModuleCommandRegistry();
        registry.register("TestModule", path("center test"), sender -> { });

        assertTrue(CommandConflicts.swallowedModuleCommand(
                CommandConflicts.corePaths(CommandsDefinition.SYSTEM_PATHS, commands("center info")),
                registry.all()).isEmpty());
    }

    @Test
    @DisplayName("the fixed system paths belong to the core as well")
    void systemPathsAreCorePaths() throws Exception {
        final Set<String> paths =
                CommandConflicts.corePaths(CommandsDefinition.SYSTEM_PATHS, commands("center info"));

        assertTrue(paths.contains("center reload"));
        assertTrue(paths.contains("center modules"));
        assertTrue(paths.contains("center info"));
    }

    private static CommandsDefinition commands(final String infoPath) throws Exception {
        return CommandsDefinition.validated(List.of(
                CommandSpec.of("center-info", "commands.center-info", true, infoPath, List.of())));
    }

    private static CommandPath path(final String raw) throws Exception {
        return CommandPath.of("command", raw);
    }
}
