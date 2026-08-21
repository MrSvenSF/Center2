package net.managerhub.center.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultFilesTest {

    private static final String RESOURCE = "defaults/paper/Menus/CenterInfo.yml";

    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("a missing file is created with the bundled content")
    void createsMissingFile() throws IOException {
        final Path target = temporaryDirectory.resolve("Menus/CenterInfo.yml");

        assertEquals(DefaultFiles.Installation.CREATED, DefaultFiles.install(target, RESOURCE));
        assertTrue(Files.size(target) > 0L);
        assertTrue(Files.readString(target, StandardCharsets.UTF_8).contains("items:"));
    }

    @Test
    @DisplayName("a file of zero bytes is not a configuration and is restored")
    void repairsZeroByteFile() throws IOException {
        final Path target = temporaryDirectory.resolve("CenterInfo.yml");
        Files.write(target, new byte[0]);

        assertEquals(DefaultFiles.Installation.REPAIRED, DefaultFiles.install(target, RESOURCE));
        assertTrue(Files.readString(target, StandardCharsets.UTF_8).contains("items:"));
    }

    @Test
    @DisplayName("a file that only holds a byte order mark is restored as well")
    void repairsByteOrderMarkOnlyFile() throws IOException {
        final Path target = temporaryDirectory.resolve("CenterInfo.yml");
        Files.write(target, new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

        assertEquals(DefaultFiles.Installation.REPAIRED, DefaultFiles.install(target, RESOURCE));
        assertTrue(Files.readString(target, StandardCharsets.UTF_8).contains("items:"));
    }

    @Test
    @DisplayName("a file that only holds blank lines is restored as well")
    void repairsWhitespaceOnlyFile() throws IOException {
        final Path target = temporaryDirectory.resolve("CenterInfo.yml");
        Files.writeString(target, "\r\n   \n\t\n", StandardCharsets.UTF_8);

        assertEquals(DefaultFiles.Installation.REPAIRED, DefaultFiles.install(target, RESOURCE));
        assertTrue(Files.readString(target, StandardCharsets.UTF_8).contains("items:"));
    }

    @Test
    @DisplayName("a file that only holds a comment is kept")
    void keepsCommentOnlyFile() throws IOException {
        final Path target = temporaryDirectory.resolve("CenterInfo.yml");
        final String comment = "# I emptied this on purpose\n";
        Files.writeString(target, comment, StandardCharsets.UTF_8);

        assertEquals(DefaultFiles.Installation.KEPT, DefaultFiles.install(target, RESOURCE));
        assertEquals(comment, Files.readString(target, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("a file with content is never overwritten")
    void keepsUserConfiguration() throws IOException {
        final Path target = temporaryDirectory.resolve("CenterInfo.yml");
        final String user = "config-version: 1\ntitle: \"My own menu\"\n";
        Files.writeString(target, user, StandardCharsets.UTF_8);

        assertEquals(DefaultFiles.Installation.KEPT, DefaultFiles.install(target, RESOURCE));
        assertEquals(user, Files.readString(target, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("a broken but filled configuration is left to the validation")
    void keepsBrokenUserConfiguration() throws IOException {
        final Path target = temporaryDirectory.resolve("CenterInfo.yml");
        final String broken = "this is not valid yaml: [\n";
        Files.writeString(target, broken, StandardCharsets.UTF_8);

        assertEquals(DefaultFiles.Installation.KEPT, DefaultFiles.install(target, RESOURCE));
        assertEquals(broken, Files.readString(target, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("a missing bundled resource is reported")
    void reportsMissingResource() {
        final Path target = temporaryDirectory.resolve("Unknown.yml");

        assertThrows(IOException.class, () -> DefaultFiles.install(target, "defaults/paper/Unknown.yml"));
    }
}
