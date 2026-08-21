package net.managerhub.center.paper.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandsFileWriterTest {

    private static final String ENABLED = """
            # Center2 - Commands.yml
            config-version: 1

            commands:

              # Opens the Center-Info menu.
              center-info:
                enabled: true    # switched on
                command: "center info"
                aliases:
            """;

    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("the command is switched off and everything else stays untouched")
    void switchesTheCommandOff() throws IOException {
        final Path file = write(ENABLED);

        assertTrue(CommandsFileWriter.disableCommand(file, "center-info"));

        final String content = Files.readString(file, StandardCharsets.UTF_8);
        assertEquals(ENABLED.replace("enabled: true    # switched on", "enabled: false    # switched on"), content);
        assertTrue(content.contains("# Opens the Center-Info menu."));
        assertTrue(content.contains("aliases:"));
    }

    @Test
    @DisplayName("an already switched off command is not written again")
    void doesNothingWhenAlreadyDisabled() throws IOException {
        final Path file = write(ENABLED.replace("enabled: true", "enabled: false"));
        final String before = Files.readString(file, StandardCharsets.UTF_8);

        assertFalse(CommandsFileWriter.disableCommand(file, "center-info"));
        assertEquals(before, Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("windows line endings survive the change")
    void keepsWindowsLineEndings() throws IOException {
        final Path file = write(ENABLED.replace("\n", "\r\n"));

        assertTrue(CommandsFileWriter.disableCommand(file, "center-info"));

        final String content = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(content.contains("enabled: false"));
        assertFalse(content.contains("\n\n"), "a line feed without a carriage return was written");
    }

    @Test
    @DisplayName("only the entry of the given command is changed")
    void changesOnlyTheGivenCommand() throws IOException {
        final Path file = write("""
                config-version: 1

                commands:
                  other-command:
                    enabled: true
                    command: "other"
                  center-info:
                    enabled: true
                    command: "center info"
                """);

        assertTrue(CommandsFileWriter.disableCommand(file, "center-info"));

        assertEquals("""
                config-version: 1

                commands:
                  other-command:
                    enabled: true
                    command: "other"
                  center-info:
                    enabled: false
                    command: "center info"
                """, Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("a missing entry is reported instead of guessed")
    void reportsMissingEntry() throws IOException {
        final Path file = write("""
                config-version: 1

                commands:
                  center-info:
                    command: "center info"
                """);

        assertThrows(IOException.class, () -> CommandsFileWriter.disableCommand(file, "center-info"));
    }

    private Path write(final String content) throws IOException {
        final Path file = temporaryDirectory.resolve("Commands.yml");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}
