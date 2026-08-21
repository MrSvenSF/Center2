package net.managerhub.center.paper.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.managerhub.center.Center;
import net.managerhub.center.common.command.CommandSpec;
import net.managerhub.center.common.command.CommandsDefinition;
import net.managerhub.center.common.config.ConfigurationException;

/**
 * Reads and validates {@code Commands.yml}.
 *
 * <p>Only the commands that Center2 already knows may appear here. The file
 * decides whether a command is active, which command path calls it and which
 * alias paths call it as well; what the command does is fixed in the Java code.
 * {@code /center reload} and the module overview {@code /center modules} are
 * fixed system commands and are therefore not part of this file.</p>
 */
final class CommandsConfigLoader {

    /** The internal keys Center2 accepts below {@code commands}. */
    private static final Set<String> KNOWN_COMMANDS = Set.copyOf(CommandsDefinition.CONFIGURABLE_KEYS);

    /** The keys one command entry may contain. {@code aliases} is optional. */
    private static final Set<String> REQUIRED_KEYS = Set.of("enabled", "command");
    private static final Set<String> OPTIONAL_KEYS = Set.of("aliases");

    private CommandsConfigLoader() {
        throw new AssertionError("No instances.");
    }

    static CommandsDefinition load(final Path file) throws ConfigurationException {
        final YamlReader reader = YamlReader.read(file, Center.COMMANDS_FILE);
        reader.requireConfigVersion();
        reader.requireExactKeys("commands", KNOWN_COMMANDS);

        final List<CommandSpec> commands = new ArrayList<>();
        // The file order is fixed, so an error message always names the same entry.
        for (final String key : CommandsDefinition.CONFIGURABLE_KEYS) {
            commands.add(command(reader, key));
        }
        return CommandsDefinition.validated(commands);
    }

    private static CommandSpec command(final YamlReader reader, final String key) throws ConfigurationException {
        final String path = "commands." + key;
        for (final String entry : reader.requireSection(path).getKeys(false)) {
            if (!REQUIRED_KEYS.contains(entry) && !OPTIONAL_KEYS.contains(entry)) {
                throw reader.error("'" + path + "' contains the unknown entry '" + entry + "'. A command only knows: "
                        + "enabled, command and the optional aliases.");
            }
        }
        return CommandSpec.of(
                key,
                path,
                reader.requireBoolean(path + ".enabled"),
                reader.requireString(path + ".command"),
                reader.optionalStringList(path + ".aliases"));
    }
}
