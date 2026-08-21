package net.managerhub.center.common.command;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Finds a command path two owners want at the same time.
 *
 * <p>There is one rule and it points in one direction only: a running module may
 * never take a path of the core - that is refused when the module registers - and
 * the core may never take a path away from a running module either. The second
 * half is what this class is for, because it can only happen at a reload, when
 * {@code Commands.yml} suddenly names a path a module already serves.</p>
 *
 * <p>Both platforms use it. Paper builds the core paths from
 * {@code Commands.yml} plus its system commands, the proxy from its fixed
 * paths.</p>
 */
public final class CommandConflicts {

    private CommandConflicts() {
        throw new AssertionError("No instances.");
    }

    /**
     * Collects every path a command configuration owns.
     *
     * <p>Disabled commands count too: their path belongs to the core and comes
     * back the moment somebody switches the command on again. Aliases count as
     * well, for the same reason.</p>
     *
     * @param systemPaths the fixed paths of the core
     * @param commands    the configured commands, enabled and disabled
     * @return every path of the core, as it is written in the configuration
     */
    public static Set<String> corePaths(final List<CommandPath> systemPaths, final CommandsDefinition commands) {
        final Set<String> paths = new LinkedHashSet<>();
        for (final CommandPath system : systemPaths) {
            paths.add(system.display());
        }
        for (final CommandSpec spec : commands.commands()) {
            for (final CommandPath path : spec.allPaths()) {
                paths.add(path.display());
            }
        }
        return Set.copyOf(paths);
    }

    /**
     * Looks for a module command the given core paths would swallow.
     *
     * @param corePaths      every path the core wants
     * @param moduleCommands the commands the running modules have registered
     * @return the first module command that would be lost, or empty if there is none
     */
    public static Optional<ModuleCommandRegistry.Registered> swallowedModuleCommand(
            final Set<String> corePaths,
            final List<ModuleCommandRegistry.Registered> moduleCommands) {
        return moduleCommands.stream()
                .filter(command -> corePaths.stream()
                        .anyMatch(path -> path.equalsIgnoreCase(command.path().display())))
                .findFirst();
    }
}
