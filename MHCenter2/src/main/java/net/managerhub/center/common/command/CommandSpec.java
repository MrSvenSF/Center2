package net.managerhub.center.common.command;

import java.util.ArrayList;
import java.util.List;

import net.managerhub.center.common.config.ConfigurationException;

/**
 * Validated configuration of one built-in MHCenter2 command.
 *
 * <p>The configuration only decides whether the command is active, which command
 * path calls it and which alternative paths call it as well. What the command
 * actually does is fixed in the Java code and is bound to {@link #key()}.</p>
 *
 * @param key     fixed internal key, for example {@code center-info}
 * @param enabled whether the command is registered and executable
 * @param path    the configured command path
 * @param aliases alternative command paths, may be empty
 */
public record CommandSpec(String key, boolean enabled, CommandPath path, List<CommandPath> aliases) {

    public CommandSpec {
        aliases = List.copyOf(aliases);
    }

    /**
     * Validates raw configuration values and builds a specification from them.
     *
     * @param key        internal key of the command
     * @param configPath configuration path, used for error messages
     * @param enabled    activation state
     * @param rawPath    raw command path
     * @param rawAliases raw alternative command paths, may be empty
     * @return the validated specification
     * @throws ConfigurationException if any value is invalid
     */
    public static CommandSpec of(final String key,
                                 final String configPath,
                                 final boolean enabled,
                                 final String rawPath,
                                 final List<String> rawAliases) throws ConfigurationException {
        final CommandPath validatedPath = CommandPath.of(configPath + ".command", rawPath);
        final List<CommandPath> validatedAliases = new ArrayList<>();
        for (int i = 0; i < rawAliases.size(); i++) {
            validatedAliases.add(CommandPath.of(configPath + ".aliases[" + i + "]", rawAliases.get(i)));
        }
        return new CommandSpec(key, enabled, validatedPath, validatedAliases);
    }

    /** @return a copy that is disabled, or this instance if it is disabled already. */
    public CommandSpec disabled() {
        return enabled ? new CommandSpec(key, false, path, aliases) : this;
    }

    /** @return the configured path followed by all alias paths. */
    public List<CommandPath> allPaths() {
        final List<CommandPath> paths = new ArrayList<>(aliases.size() + 1);
        paths.add(path);
        paths.addAll(aliases);
        return List.copyOf(paths);
    }
}
