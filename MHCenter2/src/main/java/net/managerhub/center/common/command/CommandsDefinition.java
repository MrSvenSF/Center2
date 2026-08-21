package net.managerhub.center.common.command;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.managerhub.center.common.config.ConfigurationException;

/**
 * The complete, validated content of {@code Commands.yml}.
 *
 * <p>This type is platform neutral and is built only through
 * {@link #validated(List)}, so an instance always represents a conflict free
 * command layout.</p>
 *
 * <p>{@code /center reload} and the module overview {@code /center modules} are
 * not part of {@code Commands.yml}. They are fixed MHCenter2 system commands, so
 * their paths are only reserved here to make sure that no configured command can
 * take one of them away. The three module actions are normal configurable core
 * commands and therefore do live in the file.</p>
 *
 * @param commands the configurable commands in configuration order
 */
public record CommandsDefinition(List<CommandSpec> commands) {

    /** Internal key of the Center-Info command. */
    public static final String CENTER_INFO_KEY = "center-info";

    /** Internal key of the fixed reload system command. */
    public static final String RELOAD_KEY = "reload";

    /** Fixed, non configurable path of the reload system command. */
    public static final CommandPath RELOAD_PATH = new CommandPath(List.of("center", "reload"));

    /** Internal key of the fixed module overview command. */
    public static final String MODULES_KEY = "modules";

    /** Internal key of the configurable module reload command. */
    public static final String MODULES_RELOAD_KEY = "modules-reload";

    /** Internal key of the configurable module enable command. */
    public static final String MODULES_ENABLE_KEY = "modules-enable";

    /** Internal key of the configurable module disable command. */
    public static final String MODULES_DISABLE_KEY = "modules-disable";

    /** Fixed, non configurable path of the module overview command. */
    public static final CommandPath MODULES_PATH = new CommandPath(List.of("center", "modules"));

    /** The configurable commands of {@code Commands.yml}, in file order. */
    public static final List<String> CONFIGURABLE_KEYS =
            List.of(CENTER_INFO_KEY, MODULES_RELOAD_KEY, MODULES_ENABLE_KEY, MODULES_DISABLE_KEY);

    /** The paths no configured command may take, because MHCenter2 owns them. */
    public static final List<CommandPath> SYSTEM_PATHS = List.of(RELOAD_PATH, MODULES_PATH);

    public CommandsDefinition {
        commands = List.copyOf(commands);
    }

    /**
     * Checks the whole command layout for conflicts and builds the definition.
     *
     * <p>Every entry is checked, including disabled ones, so a configuration error
     * can never hide behind {@code enabled: false}.</p>
     *
     * @param commands validated commands
     * @return the validated definition
     * @throws ConfigurationException if a command path is used twice
     */
    public static CommandsDefinition validated(final List<CommandSpec> commands) throws ConfigurationException {
        final Map<String, String> taken = new LinkedHashMap<>();
        for (final CommandPath system : SYSTEM_PATHS) {
            taken.put(system.display(), "the fixed system command /" + system.display());
        }
        for (final CommandSpec command : commands) {
            final String path = "commands." + command.key();
            claim(taken, command.path(), path + ".command");
            final List<CommandPath> aliases = command.aliases();
            for (int i = 0; i < aliases.size(); i++) {
                claim(taken, aliases.get(i), path + ".aliases[" + i + "]");
            }
        }
        return new CommandsDefinition(commands);
    }

    /** @return the command with the given internal key. */
    public Optional<CommandSpec> command(final String key) {
        return commands.stream().filter(spec -> spec.key().equals(key)).findFirst();
    }

    /** @return all enabled commands in configuration order. */
    public List<CommandSpec> enabledCommands() {
        return commands.stream().filter(CommandSpec::enabled).toList();
    }

    /**
     * @param key internal key of the command
     * @return a copy in which the given command is disabled
     */
    public CommandsDefinition withDisabled(final String key) {
        return new CommandsDefinition(commands.stream()
                .map(spec -> spec.key().equals(key) ? spec.disabled() : spec)
                .toList());
    }

    private static void claim(final Map<String, String> taken,
                              final CommandPath candidate,
                              final String configPath) throws ConfigurationException {
        final String owner = taken.putIfAbsent(candidate.display(), "'" + configPath + "'");
        if (owner != null) {
            throw new ConfigurationException("Commands.yml: '" + configPath + "' uses the command path \""
                    + candidate.display() + "\" which is already used by " + owner
                    + ". Command paths must be unique, upper and lower case are treated as the same path.");
        }
    }
}
