package net.managerhub.center.paper.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import net.managerhub.center.common.config.ConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YamlReaderTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsValidMiniMessage() throws Exception {
        final YamlReader reader = readerWith("<gradient:#750000:#ffffff>Center2</gradient>");

        assertDoesNotThrow(() -> reader.requireMiniMessage("message"));
    }

    @Test
    void acceptsUnclosedFormattingTagUsedByDefaultConfiguration() throws Exception {
        final YamlReader reader = readerWith("<white>Status");

        assertDoesNotThrow(() -> reader.requireMiniMessage("message"));
    }

    @Test
    void rejectsInvalidMiniMessageColor() throws Exception {
        final YamlReader reader = readerWith("<gradient:not-a-color:#ffffff>Broken</gradient>");

        assertThrows(ConfigurationException.class, () -> reader.requireMiniMessage("message"));
    }

    @Test
    void acceptsEscapedLiteralTag() throws Exception {
        final YamlReader reader = readerWith("\\<not-a-formatting-tag>");

        assertDoesNotThrow(() -> reader.requireMiniMessage("message"));
    }

    private YamlReader readerWith(final String value) throws Exception {
        final Path file = temporaryDirectory.resolve("Test.yml");
        Files.writeString(file, "message: '" + value + "'\n");
        return YamlReader.read(file, "Test.yml");
    }
}
