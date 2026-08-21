package net.managerhub.center.common.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import net.managerhub.center.Center;
import net.managerhub.center.common.module.ModuleStateException;
import net.managerhub.center.common.module.ModuleStateStore;
import org.sqlite.SQLiteDataSource;

/**
 * The local SQLite database of one platform, stored as {@code DB/Center.db}
 * inside the plugin data folder.
 *
 * <p>Paper and Velocity each use their own instance and their own file. The
 * class keeps exactly one connection open and guards it with the monitor of this
 * object, so it can be used from the server thread and from the proxy without
 * further synchronization.</p>
 *
 * <p>{@link SQLiteDataSource} is used on purpose instead of
 * {@code DriverManager}, because a plugin class loader must not depend on the
 * global JDBC driver registry.</p>
 */
public final class SqliteDatabase implements AutoCloseable, ModuleStateStore {

    /** Relative name used in every message, never an absolute path. */
    public static final String RELATIVE_NAME = Center.DATABASE_DIRECTORY + "/" + Center.DATABASE_FILE;

    private static final String META_TABLE = "center_meta";
    private static final String MODULE_STATE_TABLE = "center_module_state";
    private static final String SCHEMA_VERSION_KEY = "schema_version";

    private final Path file;
    private Connection connection;

    /**
     * @param file absolute location of {@code DB/Center.db}
     */
    public SqliteDatabase(final Path file) {
        this.file = file;
    }

    /**
     * Creates the directory, opens the connection, applies the pragmas and
     * creates the schema.
     *
     * @throws DatabaseException if the database cannot be prepared
     */
    public synchronized void initialize() throws DatabaseException {
        if (connection != null) {
            return;
        }
        final Path parent = file.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (final IOException failure) {
                throw new DatabaseException("The database folder '" + Center.DATABASE_DIRECTORY
                        + "' could not be created: " + failure.getMessage(), failure);
            }
        }

        try {
            final SQLiteDataSource source = new SQLiteDataSource();
            source.setUrl("jdbc:sqlite:" + file.toAbsolutePath());
            connection = source.getConnection();
            applyPragmas();
            createSchema();
        } catch (final SQLException failure) {
            closeQuietly();
            throw new DatabaseException("The SQLite database '" + RELATIVE_NAME
                    + "' could not be initialized: " + failure.getMessage(), failure);
        }
    }

    /**
     * @return {@code true} if the connection is open and answers a test query
     */
    public synchronized boolean isHealthy() {
        if (connection == null) {
            return false;
        }
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT 1")) {
            return result.next();
        } catch (final SQLException failure) {
            return false;
        }
    }

    /**
     * Reads one value from {@code center_meta}.
     *
     * @param key primary key of the row
     * @return the stored value, or empty if the row or the connection is missing
     */
    public synchronized Optional<String> meta(final String key) {
        if (connection == null) {
            return Optional.empty();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT value FROM " + META_TABLE + " WHERE key = ?")) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.ofNullable(result.getString(1)) : Optional.empty();
            }
        } catch (final SQLException failure) {
            return Optional.empty();
        }
    }

    /** @return the schema version stored in the database. */
    public Optional<String> schemaVersion() {
        return meta(SCHEMA_VERSION_KEY);
    }

    /**
     * {@inheritDoc}
     *
     * <p>A failure is never answered with an empty set. "No module is switched
     * off" and "the stored state could not be read" would otherwise look the
     * same, and Center2 would start modules an administrator had switched off.</p>
     */
    @Override
    public synchronized Set<String> disabledModules() throws ModuleStateException {
        if (connection == null) {
            throw new ModuleStateException("The database '" + RELATIVE_NAME + "' is not open.", null);
        }
        final Set<String> disabled = new LinkedHashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT module_id FROM " + MODULE_STATE_TABLE + " WHERE enabled = 0")) {
            while (result.next()) {
                disabled.add(result.getString(1));
            }
        } catch (final SQLException failure) {
            throw new ModuleStateException("The switched off modules could not be read from '"
                    + RELATIVE_NAME + "': " + failure.getMessage(), failure);
        }
        return disabled;
    }

    @Override
    public synchronized void setModuleDisabled(final String moduleId, final boolean disabled)
            throws ModuleStateException {
        if (connection == null) {
            throw new ModuleStateException("The database '" + RELATIVE_NAME + "' is not open.", null);
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + MODULE_STATE_TABLE + " (module_id, enabled) VALUES (?, ?) "
                        + "ON CONFLICT(module_id) DO UPDATE SET enabled = excluded.enabled")) {
            statement.setString(1, moduleId.toLowerCase(Locale.ROOT));
            statement.setInt(2, disabled ? 0 : 1);
            statement.executeUpdate();
        } catch (final SQLException failure) {
            throw new ModuleStateException("The state of module '" + moduleId + "' could not be stored in '"
                    + RELATIVE_NAME + "': " + failure.getMessage(), failure);
        }
    }

    /** Writes the remaining WAL content back and closes the connection. */
    @Override
    public synchronized void close() {
        if (connection == null) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
        } catch (final SQLException ignored) {
            // A failed checkpoint is not a reason to keep the connection open.
        }
        closeQuietly();
    }

    private void applyPragmas() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            // Referential integrity has to be enabled per connection.
            statement.execute("PRAGMA foreign_keys = ON");
            // Wait instead of failing immediately while the file is locked.
            statement.execute("PRAGMA busy_timeout = 5000");
            // Write ahead logging keeps readers and the writer out of each other's way.
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA synchronous = NORMAL");
        }
    }

    private void createSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS center_meta (
                        key   TEXT PRIMARY KEY,
                        value TEXT NOT NULL
                    )""");
            // Which installed modules an administrator switched off. Every schema
            // change so far only adds a table, so an older database keeps working.
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS center_module_state (
                        module_id TEXT PRIMARY KEY,
                        enabled   INTEGER NOT NULL
                    )""");
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + META_TABLE + " (key, value) VALUES (?, ?) ON CONFLICT(key) DO NOTHING")) {
            statement.setString(1, SCHEMA_VERSION_KEY);
            statement.setString(2, Integer.toString(Center.SCHEMA_VERSION));
            statement.executeUpdate();
        }
        // An older database is raised to the current version, a newer one is never
        // pushed back down by an older Center2.
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE " + META_TABLE + " SET value = ? WHERE key = ? AND CAST(value AS INTEGER) < ?")) {
            statement.setString(1, Integer.toString(Center.SCHEMA_VERSION));
            statement.setString(2, SCHEMA_VERSION_KEY);
            statement.setInt(3, Center.SCHEMA_VERSION);
            statement.executeUpdate();
        }
    }

    private void closeQuietly() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (final SQLException ignored) {
            // Nothing left to do, the connection is dropped either way.
        } finally {
            connection = null;
        }
    }
}
