package net.managerhub.center.common.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Set;

import net.managerhub.center.Center;
import net.managerhub.center.common.module.ModuleStateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteModuleStateTest {

    @TempDir
    Path directory;

    @Test
    @DisplayName("a switched off module is still switched off after a restart")
    void keepsTheDisabledModuleAcrossRestarts() throws Exception {
        final Path file = directory.resolve(Center.DATABASE_DIRECTORY).resolve(Center.DATABASE_FILE);

        try (SqliteDatabase database = open(file)) {
            database.setModuleDisabled("test", true);
            assertEquals(Set.of("test"), database.disabledModules());
        }

        // Opening the file again is what a server restart looks like.
        try (SqliteDatabase database = open(file)) {
            assertEquals(Set.of("test"), database.disabledModules());
            database.setModuleDisabled("test", false);
        }

        try (SqliteDatabase database = open(file)) {
            assertTrue(database.disabledModules().isEmpty());
        }
    }

    @Test
    @DisplayName("the id is stored without case")
    void storesTheIdWithoutCase() throws Exception {
        final Path file = directory.resolve(Center.DATABASE_DIRECTORY).resolve(Center.DATABASE_FILE);

        try (SqliteDatabase database = open(file)) {
            database.setModuleDisabled("TestModule", true);
            assertEquals(Set.of("testmodule"), database.disabledModules());
        }
    }

    @Test
    @DisplayName("only the switched off modules are remembered")
    void remembersOnlyTheSwitchedOffModules() throws Exception {
        final Path file = directory.resolve(Center.DATABASE_DIRECTORY).resolve(Center.DATABASE_FILE);

        try (SqliteDatabase database = open(file)) {
            database.setModuleDisabled("first", true);
            database.setModuleDisabled("second", false);
            database.setModuleDisabled("third", true);

            assertEquals(Set.of("first", "third"), database.disabledModules());
        }
    }

    @Test
    @DisplayName("a closed database reports the failure instead of answering 'nothing'")
    void reportsTheFailureOfAClosedDatabase() throws Exception {
        final Path file = directory.resolve(Center.DATABASE_DIRECTORY).resolve(Center.DATABASE_FILE);
        final SqliteDatabase database = open(file);
        database.setModuleDisabled("test", true);
        database.close();

        // "nothing is switched off" would be a wrong and dangerous answer here.
        assertThrows(ModuleStateException.class, database::disabledModules);
        assertThrows(ModuleStateException.class, () -> database.setModuleDisabled("test", false));
    }

    private static SqliteDatabase open(final Path file) throws DatabaseException {
        final SqliteDatabase database = new SqliteDatabase(file);
        database.initialize();
        return database;
    }
}
