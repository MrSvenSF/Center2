package net.managerhub.center.paper.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Switches one command off inside {@code Commands.yml}.
 *
 * <p>MHCenter2 writes {@code enabled: false} into the file when the menu that
 * belongs to a command is switched off in {@code MainConfig.yml}, so an
 * administrator sees the real state directly in {@code Commands.yml}.</p>
 *
 * <p>Only that single value is touched. Comments, order, indentation, quoting
 * and an empty {@code aliases:} stay exactly as the administrator wrote them,
 * and {@code enabled: true} is never written back automatically.</p>
 */
public final class CommandsFileWriter {

    private static final Pattern COMMANDS_SECTION = Pattern.compile("^commands:\\s*(#.*)?$");
    private static final Pattern ENABLED_ENTRY = Pattern.compile("^(\\s+enabled:\\s*)(\\S+)(\\s*(?:#.*)?)$");

    private CommandsFileWriter() {
        throw new AssertionError("No instances.");
    }

    /**
     * Sets {@code commands.<key>.enabled} to {@code false}.
     *
     * @param file       location of {@code Commands.yml}
     * @param commandKey internal key of the command, for example {@code center-info}
     * @return {@code true} if the file was changed, {@code false} if it already said {@code false}
     * @throws IOException if the file cannot be read, written or does not have the expected structure
     */
    public static boolean disableCommand(final Path file, final String commandKey) throws IOException {
        final String content = Files.readString(file, StandardCharsets.UTF_8);
        final String separator = content.contains("\r\n") ? "\r\n" : "\n";
        final List<String> lines = new ArrayList<>(List.of(content.split("\\R", -1)));

        final int commandLine = commandEntryLine(lines, commandKey);
        final int commandIndent = indentOf(lines.get(commandLine));

        for (int index = commandLine + 1; index < lines.size(); index++) {
            final String line = lines.get(index).stripTrailing();
            if (line.isBlank() || line.stripLeading().startsWith("#")) {
                continue;
            }
            if (indentOf(line) <= commandIndent) {
                break;
            }
            final Matcher enabled = ENABLED_ENTRY.matcher(line);
            if (!enabled.matches()) {
                continue;
            }
            if ("false".equals(enabled.group(2))) {
                return false;
            }
            lines.set(index, enabled.group(1) + "false" + enabled.group(3));
            write(file, String.join(separator, lines));
            return true;
        }
        throw new IOException("the entry 'commands." + commandKey + ".enabled' was not found");
    }

    private static int commandEntryLine(final List<String> lines, final String commandKey) throws IOException {
        final Pattern entry = Pattern.compile("^\\s+" + Pattern.quote(commandKey) + ":\\s*(#.*)?$");
        boolean insideCommands = false;
        for (int index = 0; index < lines.size(); index++) {
            final String line = lines.get(index).stripTrailing();
            if (COMMANDS_SECTION.matcher(line).matches()) {
                insideCommands = true;
                continue;
            }
            if (insideCommands && entry.matcher(line).matches()) {
                return index;
            }
        }
        throw new IOException("the section 'commands." + commandKey + "' was not found");
    }

    private static int indentOf(final String line) {
        int indent = 0;
        while (indent < line.length() && line.charAt(indent) == ' ') {
            indent++;
        }
        return indent;
    }

    private static void write(final Path file, final String content) throws IOException {
        final Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, content, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (final AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
