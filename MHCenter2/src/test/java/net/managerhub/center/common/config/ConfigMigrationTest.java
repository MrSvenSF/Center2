package net.managerhub.center.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import net.managerhub.center.Center;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigMigrationTest {

    /** The bundled default of the command configuration of this MHCenter2 version. */
    private static final String COMMANDS_DEFAULT = "defaults/paper/" + Center.COMMANDS_FILE;

    /** The bundled default of the permission configuration of this MHCenter2 version. */
    private static final String PERMISSIONS_DEFAULT = "defaults/paper/" + Center.PERMISSIONS_FILE;

    @TempDir
    Path directory;

    @Test
    @DisplayName("missing default entries are added and the version is raised")
    void addsMissingEntries() throws Exception {
        // A Commands.yml of an older MHCenter2: it only knows the info command.
        final Path file = write(Center.COMMANDS_FILE, """
                # MHCenter2 - Commands.yml

                config-version: 1

                commands:
                  center-info:
                    enabled: true
                    command: "network info"
                    aliases:
                """);

        final ConfigMigration.Result result = ConfigMigration.apply(file, COMMANDS_DEFAULT);

        assertTrue(result.changed());
        assertEquals(1, result.fromVersion());
        assertEquals(Center.CONFIG_VERSION, result.toVersion());
        assertTrue(result.added().contains("commands.modules-reload.command"), result.added().toString());

        final String migrated = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(migrated.contains("config-version: " + Center.CONFIG_VERSION));
        assertTrue(migrated.contains("modules-reload:"));
        assertTrue(migrated.contains("modules-enable:"));
        assertTrue(migrated.contains("modules-disable:"));
    }

    @Test
    @DisplayName("a value the administrator changed is never touched")
    void keepsExistingValues() throws Exception {
        final Path file = write(Center.COMMANDS_FILE, """
                config-version: 1

                commands:
                  center-info:
                    enabled: false
                    command: "network info"
                    aliases:
                      - "netinfo"
                """);

        ConfigMigration.apply(file, COMMANDS_DEFAULT);

        final String migrated = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(migrated.contains("enabled: false"), migrated);
        assertTrue(migrated.contains("command: \"network info\""), migrated);
        assertTrue(migrated.contains("- \"netinfo\""), migrated);
        assertFalse(migrated.contains("command: \"center info\""), "the default must not overwrite the own value");
    }

    @Test
    @DisplayName("a new permission entry is added below the existing ones")
    void addsANewPermission() throws Exception {
        final Path file = write(Center.PERMISSIONS_FILE, """
                config-version: 1

                permissions:
                  admin:
                    permission: "my.own.admin"
                    op: true
                """);

        final ConfigMigration.Result result = ConfigMigration.apply(file, PERMISSIONS_DEFAULT);

        final String migrated = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(result.added().contains("permissions.admin-all.permission"), result.added().toString());
        assertTrue(migrated.contains("permission: \"my.own.admin\""), migrated);
        assertTrue(migrated.contains("op: true"), migrated);
        assertTrue(migrated.contains("center.admin.*"), migrated);
        assertTrue(migrated.contains("center.admin.modules.disable"), migrated);
    }

    @Test
    @DisplayName("a complete file is not touched at all")
    void leavesACompleteFileAlone() throws Exception {
        final Path file = write(Center.COMMANDS_FILE, bundled(COMMANDS_DEFAULT));
        final String before = Files.readString(file, StandardCharsets.UTF_8);

        final ConfigMigration.Result result = ConfigMigration.apply(file, COMMANDS_DEFAULT);

        assertFalse(result.changed());
        assertTrue(result.added().isEmpty());
        assertEquals(before, Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("a file of a newer MHCenter2 is never rewritten")
    void refusesANewerFile() throws Exception {
        final String newer = """
                config-version: 99

                commands:
                  something-we-do-not-know:
                    enabled: true
                """;
        final Path file = write(Center.COMMANDS_FILE, newer);

        final ConfigMigration.Result result = ConfigMigration.apply(file, COMMANDS_DEFAULT);

        assertTrue(result.skippedNewer());
        assertFalse(result.changed());
        assertEquals(newer, Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("a file that does not exist is not created here")
    void ignoresAMissingFile() throws Exception {
        final ConfigMigration.Result result =
                ConfigMigration.apply(directory.resolve("nothing.yml"), COMMANDS_DEFAULT);

        assertFalse(result.changed());
        assertTrue(result.added().isEmpty());
    }

    @Test
    @DisplayName("the migrated file is valid again for the command loader")
    void producesAFileTheLoaderAccepts() throws Exception {
        final Path file = write(Center.COMMANDS_FILE, """
                config-version: 1

                commands:
                  center-info:
                    enabled: true
                    command: "center info"
                    aliases:
                """);

        ConfigMigration.apply(file, COMMANDS_DEFAULT);

        // The whole point of the migration: no administrator has to delete a file
        // because MHCenter2 learned a new entry.
        final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertTrue(lines.contains("config-version: " + Center.CONFIG_VERSION));
        assertTrue(lines.stream().anyMatch(line -> line.contains("\"center modules reload\"")));
    }

    private Path write(final String name, final String content) throws Exception {
        final Path file = directory.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private static String bundled(final String resource) throws Exception {
        try (var in = ConfigMigrationTest.class.getClassLoader().getResourceAsStream(resource)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
