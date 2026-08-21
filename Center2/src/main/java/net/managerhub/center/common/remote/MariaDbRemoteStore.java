package net.managerhub.center.common.remote;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import net.managerhub.center.Center;
import net.managerhub.center.api.ModuleActionTarget;
import net.managerhub.center.api.ModulePlatform;
import org.mariadb.jdbc.MariaDbPoolDataSource;

/**
 * The remote database of Center2 on MariaDB.
 *
 * <p>The connection comes from the pool the MariaDB driver brings along, so a
 * poll that runs every second does not open a new TCP connection every time and
 * Center2 still does not carry a second connection library. There is no ORM and
 * no persistence layer: four tables, plain SQL, nothing generated.</p>
 *
 * <p><strong>Every value goes into a {@link PreparedStatement} as a parameter.</strong>
 * No module id, player name, server id, key or payload is ever pasted into an SQL
 * text. The only things this class builds by hand are the fixed table names.</p>
 *
 * <p>Nothing here writes a credential into a message. A failure names what was
 * attempted and what the database answered, never the user, the password or the
 * whole connection string.</p>
 */
public final class MariaDbRemoteStore implements RemoteStore {

    /** Table with one row per Center2 node. */
    public static final String NODES_TABLE = "center_nodes";

    /** Table with the actions of the network. */
    public static final String ACTIONS_TABLE = "center_actions";

    /** Table with one row per node and action. */
    public static final String RECEIPTS_TABLE = "center_action_receipts";

    /** Table with the short lived data of the modules. */
    public static final String STORAGE_TABLE = "center_module_storage";

    /** Table that only carries the schema version of the remote database. */
    public static final String META_TABLE = "center_remote_meta";

    /** Seconds a single statement may take before it is given up. */
    private static final int STATEMENT_TIMEOUT_SECONDS = 10;

    /** Seconds the pool waits for a free connection. */
    private static final int CONNECT_TIMEOUT_MS = 5000;

    /** Largest number of connections one Center2 node keeps open. */
    private static final int MAX_POOL_SIZE = 4;

    /**
     * The authentication plugins Center2 allows.
     *
     * <p>Password based, and nothing else. The Kerberos plugin of the driver is
     * deliberately not in this list: Center2 never authenticates that way, and
     * merely having it available makes the driver build a Kerberos login context
     * on the first connection, which writes a failed-login block into the server
     * console for something nobody asked for.</p>
     */
    private static final String ALLOWED_AUTH =
            "mysql_native_password,client_ed25519,caching_sha2_password,parsec,mysql_clear_password,dialog";

    /**
     * Where a connection comes from.
     *
     * <p>In a running server that is always the pool. The seam exists so the SQL
     * of this class - which statements it prepares and which values it binds -
     * can be checked without a database server; the pool itself is the one thing
     * that cannot be tested that way.</p>
     */
    @FunctionalInterface
    interface ConnectionSource {

        /**
         * @return an open connection
         * @throws SQLException if none can be handed out
         */
        Connection open() throws SQLException;
    }

    private final RemoteSettings.Database settings;
    private final ConnectionSource connections;

    private MariaDbPoolDataSource pool;

    /**
     * @param settings the validated {@code remote.database} section
     */
    public MariaDbRemoteStore(final RemoteSettings.Database settings) {
        this.settings = settings;
        this.connections = null;
    }

    /**
     * @param settings    the validated {@code remote.database} section
     * @param connections where a connection comes from instead of the pool
     */
    MariaDbRemoteStore(final RemoteSettings.Database settings, final ConnectionSource connections) {
        this.settings = settings;
        this.connections = connections;
    }

    /**
     * Builds the JDBC url of the pool.
     *
     * <p>The password is <em>not</em> part of it; it is handed to the pool
     * separately, so a url that ends up in a log or in an exception of the driver
     * can never carry it.</p>
     *
     * @param settings the database settings
     * @return the url the pool is opened with
     */
    static String url(final RemoteSettings.Database settings) {
        return "jdbc:mariadb://" + settings.host() + ":" + settings.port() + "/" + settings.database()
                + "?sslMode=" + (settings.ssl() ? "verify-full" : "disable")
                + "&connectTimeout=" + CONNECT_TIMEOUT_MS
                + "&maxPoolSize=" + MAX_POOL_SIZE
                + "&poolName=" + Center.PRODUCT_NAME.toLowerCase(Locale.ROOT)
                + "&useServerPrepStmts=true"
                + "&restrictedAuth=" + ALLOWED_AUTH;
    }

    @Override
    public void initialize() throws RemoteException {
        // A reconnect builds a fresh pool. The old one has to go first, otherwise
        // every failed attempt would leave its connections behind.
        close();
        try {
            // The order matters: the driver builds its pool the moment the url is
            // known, and a pool built before the credentials are set would open
            // its first connections without them and log an access denied that
            // nobody caused. User and password go in first, the url last.
            final MariaDbPoolDataSource created = new MariaDbPoolDataSource();
            created.setUser(settings.username());
            created.setPassword(settings.password());
            created.setUrl(url(settings));
            this.pool = created;
        } catch (final SQLException failure) {
            this.pool = null;
            throw new RemoteException("The remote database " + settings.describe()
                    + " could not be prepared: " + failure.getMessage(), failure);
        }
        createSchema();
    }

    @Override
    public void heartbeat(final RemoteNode node) throws RemoteException {
        update("the heartbeat of this node could not be written",
                "INSERT INTO " + NODES_TABLE
                        + " (server_id, runtime_id, platform, center_version, minecraft_version, last_seen) "
                        + "VALUES (?, ?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE runtime_id = VALUES(runtime_id), "
                        + "platform = VALUES(platform), center_version = VALUES(center_version), "
                        + "minecraft_version = VALUES(minecraft_version), last_seen = VALUES(last_seen)",
                statement -> {
                    statement.setString(1, node.serverId());
                    statement.setString(2, node.runtimeId());
                    statement.setString(3, node.platform().name());
                    statement.setString(4, node.centerVersion());
                    statement.setString(5, node.minecraftVersion());
                    statement.setLong(6, node.lastSeenMillis());
                });
    }

    @Override
    public List<RemoteNode> onlineNodes(final int offlineAfterSeconds) throws RemoteException {
        final long oldest = System.currentTimeMillis() - offlineAfterSeconds * 1000L;
        return query("the nodes of the network could not be read",
                "SELECT server_id, runtime_id, platform, center_version, minecraft_version, last_seen FROM "
                        + NODES_TABLE + " WHERE last_seen >= ?",
                statement -> statement.setLong(1, oldest),
                result -> {
                    final List<RemoteNode> nodes = new ArrayList<>();
                    while (result.next()) {
                        nodes.add(new RemoteNode(
                                result.getString(1),
                                result.getString(2),
                                platform(result.getString(3)),
                                result.getString(4),
                                text(result.getString(5)),
                                result.getLong(6)));
                    }
                    return List.copyOf(nodes);
                });
    }

    @Override
    public Optional<RemoteNode> node(final String serverId) throws RemoteException {
        return query("the node of the network could not be read",
                "SELECT server_id, runtime_id, platform, center_version, minecraft_version, last_seen FROM "
                        + NODES_TABLE + " WHERE server_id = ?",
                statement -> statement.setString(1, serverId),
                result -> result.next()
                        ? Optional.of(new RemoteNode(
                                result.getString(1),
                                result.getString(2),
                                platform(result.getString(3)),
                                result.getString(4),
                                text(result.getString(5)),
                                result.getLong(6)))
                        : Optional.empty());
    }

    @Override
    public void removeNode(final String serverId, final String runtimeId) throws RemoteException {
        update("this node could not be removed from the network list",
                "DELETE FROM " + NODES_TABLE + " WHERE server_id = ? AND runtime_id = ?",
                statement -> {
                    statement.setString(1, serverId);
                    statement.setString(2, runtimeId);
                });
    }

    @Override
    public void publish(final RemoteAction action) throws RemoteException {
        update("the action could not be written into the network",
                "INSERT INTO " + ACTIONS_TABLE
                        + " (id, namespace, action_type, origin_server_id, target_kind, target_server_id, "
                        + "created_at, expires_at, payload) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                statement -> {
                    statement.setString(1, action.id().toString());
                    statement.setString(2, action.namespace());
                    statement.setString(3, action.type());
                    statement.setString(4, action.originServerId());
                    statement.setString(5, action.target().kind().name());
                    statement.setString(6, action.target().serverId());
                    statement.setLong(7, action.createdAtMillis());
                    statement.setLong(8, action.expiresAtMillis());
                    statement.setBytes(9, action.payload());
                });
    }

    @Override
    public List<RemoteAction> openActions(final String serverId, final long nowMillis, final int limit)
            throws RemoteException {
        return query("the open actions could not be read",
                "SELECT a.id, a.namespace, a.action_type, a.origin_server_id, a.target_kind, "
                        + "a.target_server_id, a.created_at, a.expires_at, a.payload FROM " + ACTIONS_TABLE + " a "
                        + "LEFT JOIN " + RECEIPTS_TABLE + " r ON r.action_id = a.id AND r.server_id = ? "
                        + "WHERE r.action_id IS NULL AND a.expires_at > ? AND a.origin_server_id <> ? "
                        + "ORDER BY a.created_at ASC LIMIT ?",
                statement -> {
                    statement.setString(1, serverId);
                    statement.setLong(2, nowMillis);
                    statement.setString(3, serverId);
                    statement.setInt(4, limit);
                },
                result -> {
                    final List<RemoteAction> actions = new ArrayList<>();
                    while (result.next()) {
                        actions.add(new RemoteAction(
                                UUID.fromString(result.getString(1)),
                                result.getString(2),
                                result.getString(3),
                                result.getString(4),
                                target(result.getString(5), result.getString(6)),
                                result.getLong(7),
                                result.getLong(8),
                                result.getBytes(9)));
                    }
                    return List.copyOf(actions);
                });
    }

    @Override
    public boolean claim(final UUID actionId, final String serverId) throws RemoteException {
        // The primary key of the receipt table is exactly (action_id, server_id),
        // so the database itself decides who was first. Only the insert that
        // really created a row may run the action.
        final int inserted = update("the action could not be claimed",
                "INSERT IGNORE INTO " + RECEIPTS_TABLE
                        + " (action_id, server_id, status, processed_at, error) VALUES (?, ?, ?, ?, ?)",
                statement -> {
                    statement.setString(1, actionId.toString());
                    statement.setString(2, serverId);
                    statement.setString(3, RemoteActionStatus.CLAIMED.name());
                    statement.setLong(4, System.currentTimeMillis());
                    statement.setString(5, "");
                });
        return inserted > 0;
    }

    @Override
    public void finish(final UUID actionId,
                       final String serverId,
                       final RemoteActionStatus status,
                       final String error) throws RemoteException {
        update("the result of the action could not be written",
                "UPDATE " + RECEIPTS_TABLE + " SET status = ?, processed_at = ?, error = ? "
                        + "WHERE action_id = ? AND server_id = ?",
                statement -> {
                    statement.setString(1, status.name());
                    statement.setLong(2, System.currentTimeMillis());
                    statement.setString(3, shortened(error));
                    statement.setString(4, actionId.toString());
                    statement.setString(5, serverId);
                });
    }

    @Override
    public List<RemoteReceipt> receipts(final UUID actionId) throws RemoteException {
        return query("the results of the action could not be read",
                "SELECT action_id, server_id, status, processed_at, error FROM " + RECEIPTS_TABLE
                        + " WHERE action_id = ?",
                statement -> statement.setString(1, actionId.toString()),
                result -> {
                    final List<RemoteReceipt> receipts = new ArrayList<>();
                    while (result.next()) {
                        receipts.add(new RemoteReceipt(
                                UUID.fromString(result.getString(1)),
                                result.getString(2),
                                status(result.getString(3)),
                                result.getLong(4),
                                text(result.getString(5))));
                    }
                    return List.copyOf(receipts);
                });
    }

    @Override
    public void putData(final String namespace,
                        final String key,
                        final byte[] payload,
                        final long expiresAtMillis) throws RemoteException {
        update("the module data could not be written",
                "INSERT INTO " + STORAGE_TABLE
                        + " (namespace, storage_key, payload, created_at, expires_at, claimed_by, claimed_at) "
                        + "VALUES (?, ?, ?, ?, ?, NULL, NULL) "
                        + "ON DUPLICATE KEY UPDATE payload = VALUES(payload), created_at = VALUES(created_at), "
                        + "expires_at = VALUES(expires_at), claimed_by = NULL, claimed_at = NULL",
                statement -> {
                    statement.setString(1, namespace);
                    statement.setString(2, key);
                    statement.setBytes(3, payload);
                    statement.setLong(4, System.currentTimeMillis());
                    statement.setLong(5, expiresAtMillis);
                });
    }

    @Override
    public Optional<byte[]> readData(final String namespace, final String key, final long nowMillis)
            throws RemoteException {
        return query("the module data could not be read",
                "SELECT payload FROM " + STORAGE_TABLE
                        + " WHERE namespace = ? AND storage_key = ? AND expires_at > ? AND claimed_by IS NULL",
                statement -> {
                    statement.setString(1, namespace);
                    statement.setString(2, key);
                    statement.setLong(3, nowMillis);
                },
                result -> result.next() ? Optional.ofNullable(result.getBytes(1)) : Optional.empty());
    }

    @Override
    public Optional<byte[]> takeData(final String namespace,
                                     final String key,
                                     final String claimedBy,
                                     final long nowMillis) throws RemoteException {
        // One update decides the winner. Only the caller whose update really
        // changed the row may read the payload, so two target servers can ask at
        // the same moment and exactly one of them gets the data.
        final int claimed = update("the module data could not be taken",
                "UPDATE " + STORAGE_TABLE + " SET claimed_by = ?, claimed_at = ? "
                        + "WHERE namespace = ? AND storage_key = ? AND expires_at > ? AND claimed_by IS NULL",
                statement -> {
                    statement.setString(1, claimedBy);
                    statement.setLong(2, nowMillis);
                    statement.setString(3, namespace);
                    statement.setString(4, key);
                    statement.setLong(5, nowMillis);
                });
        if (claimed == 0) {
            return Optional.empty();
        }
        final Optional<byte[]> payload = query("the module data could not be read after it was taken",
                "SELECT payload FROM " + STORAGE_TABLE
                        + " WHERE namespace = ? AND storage_key = ? AND claimed_by = ?",
                statement -> {
                    statement.setString(1, namespace);
                    statement.setString(2, key);
                    statement.setString(3, claimedBy);
                },
                result -> result.next() ? Optional.ofNullable(result.getBytes(1)) : Optional.empty());
        deleteData(namespace, key);
        return payload;
    }

    @Override
    public boolean deleteData(final String namespace, final String key) throws RemoteException {
        return update("the module data could not be removed",
                "DELETE FROM " + STORAGE_TABLE + " WHERE namespace = ? AND storage_key = ?",
                statement -> {
                    statement.setString(1, namespace);
                    statement.setString(2, key);
                }) > 0;
    }

    @Override
    public int purgeExpired(final long nowMillis) throws RemoteException {
        int removed = update("expired module data could not be removed",
                "DELETE FROM " + STORAGE_TABLE + " WHERE expires_at <= ?",
                statement -> statement.setLong(1, nowMillis));
        // A receipt without its action would be a row nobody can read any more,
        // so the receipts go first and the actions follow.
        removed += update("expired actions could not be removed",
                "DELETE FROM " + RECEIPTS_TABLE + " WHERE action_id IN "
                        + "(SELECT id FROM " + ACTIONS_TABLE + " WHERE expires_at <= ?)",
                statement -> statement.setLong(1, nowMillis));
        removed += update("expired actions could not be removed",
                "DELETE FROM " + ACTIONS_TABLE + " WHERE expires_at <= ?",
                statement -> statement.setLong(1, nowMillis));
        return removed;
    }

    @Override
    public void close() {
        final MariaDbPoolDataSource open = pool;
        pool = null;
        if (open != null) {
            open.close();
        }
    }

    /** Binds the parameters of one statement. */
    @FunctionalInterface
    private interface Parameters {
        void bind(PreparedStatement statement) throws SQLException;
    }

    /** Reads one result set. */
    @FunctionalInterface
    private interface Rows<T> {
        T read(ResultSet result) throws SQLException;
    }

    private int update(final String what, final String sql, final Parameters parameters) throws RemoteException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(STATEMENT_TIMEOUT_SECONDS);
            parameters.bind(statement);
            return statement.executeUpdate();
        } catch (final SQLException failure) {
            throw new RemoteException(what + ": " + failure.getMessage(), failure);
        }
    }

    private <T> T query(final String what,
                        final String sql,
                        final Parameters parameters,
                        final Rows<T> rows) throws RemoteException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(STATEMENT_TIMEOUT_SECONDS);
            parameters.bind(statement);
            try (ResultSet result = statement.executeQuery()) {
                return rows.read(result);
            }
        } catch (final SQLException failure) {
            throw new RemoteException(what + ": " + failure.getMessage(), failure);
        }
    }

    private Connection connection() throws SQLException {
        if (connections != null) {
            return connections.open();
        }
        final DataSource open = pool;
        if (open == null) {
            throw new SQLException("the remote database is not open");
        }
        return open.getConnection();
    }

    /**
     * Creates the four tables and the meta row.
     *
     * <p>Only {@code CREATE TABLE IF NOT EXISTS}, so an existing database of a
     * newer Center2 is never changed and a Center2 that starts first simply
     * builds what is missing. The statements carry no value of any user, so they
     * are the only place without parameters.</p>
     */
    private void createSchema() throws RemoteException {
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(STATEMENT_TIMEOUT_SECONDS);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS center_remote_meta (
                        meta_key   VARCHAR(64)  NOT NULL PRIMARY KEY,
                        meta_value VARCHAR(255) NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS center_nodes (
                        server_id         VARCHAR(64)  NOT NULL PRIMARY KEY,
                        runtime_id        VARCHAR(36)  NOT NULL,
                        platform          VARCHAR(16)  NOT NULL,
                        center_version    VARCHAR(32)  NOT NULL,
                        minecraft_version VARCHAR(32)  NOT NULL,
                        last_seen         BIGINT       NOT NULL,
                        INDEX idx_center_nodes_last_seen (last_seen)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS center_actions (
                        id               CHAR(36)    NOT NULL PRIMARY KEY,
                        namespace        VARCHAR(64) NOT NULL,
                        action_type      VARCHAR(64) NOT NULL,
                        origin_server_id VARCHAR(64) NOT NULL,
                        target_kind      VARCHAR(16) NOT NULL,
                        target_server_id VARCHAR(64) NOT NULL,
                        created_at       BIGINT      NOT NULL,
                        expires_at       BIGINT      NOT NULL,
                        payload          MEDIUMBLOB  NULL,
                        INDEX idx_center_actions_expires (expires_at),
                        INDEX idx_center_actions_created (created_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS center_action_receipts (
                        action_id    CHAR(36)     NOT NULL,
                        server_id    VARCHAR(64)  NOT NULL,
                        status       VARCHAR(16)  NOT NULL,
                        processed_at BIGINT       NOT NULL,
                        error        VARCHAR(255) NOT NULL,
                        PRIMARY KEY (action_id, server_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS center_module_storage (
                        namespace   VARCHAR(64)  NOT NULL,
                        storage_key VARCHAR(190) NOT NULL,
                        payload     LONGBLOB     NOT NULL,
                        created_at  BIGINT       NOT NULL,
                        expires_at  BIGINT       NOT NULL,
                        claimed_by  VARCHAR(64)  NULL,
                        claimed_at  BIGINT       NULL,
                        PRIMARY KEY (namespace, storage_key),
                        INDEX idx_center_module_storage_expires (expires_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""");
        } catch (final SQLException failure) {
            throw new RemoteException("The tables of the remote database " + settings.describe()
                    + " could not be created: " + failure.getMessage(), failure);
        }
        update("the schema version of the remote database could not be written",
                "INSERT INTO " + META_TABLE + " (meta_key, meta_value) VALUES (?, ?) "
                        + "ON DUPLICATE KEY UPDATE meta_value = "
                        + "IF(CAST(meta_value AS UNSIGNED) < CAST(VALUES(meta_value) AS UNSIGNED), "
                        + "VALUES(meta_value), meta_value)",
                statement -> {
                    statement.setString(1, "schema_version");
                    statement.setString(2, Integer.toString(Center.REMOTE_SCHEMA_VERSION));
                });
    }

    private static ModulePlatform platform(final String raw) {
        try {
            return ModulePlatform.valueOf(String.valueOf(raw));
        } catch (final IllegalArgumentException unknown) {
            return ModulePlatform.BOTH;
        }
    }

    private static RemoteActionStatus status(final String raw) {
        try {
            return RemoteActionStatus.valueOf(String.valueOf(raw));
        } catch (final IllegalArgumentException unknown) {
            return RemoteActionStatus.IGNORED;
        }
    }

    private static ModuleActionTarget target(final String kind, final String serverId) {
        final ModuleActionTarget.Kind parsed;
        try {
            parsed = ModuleActionTarget.Kind.valueOf(String.valueOf(kind));
        } catch (final IllegalArgumentException unknown) {
            // A row Center2 does not understand must not reach every node.
            return ModuleActionTarget.server("unknown");
        }
        return switch (parsed) {
            case ALL -> ModuleActionTarget.ALL;
            case PAPER -> ModuleActionTarget.PAPER;
            case VELOCITY -> ModuleActionTarget.VELOCITY;
            case SERVER -> ModuleActionTarget.server(text(serverId).isEmpty() ? "unknown" : serverId);
        };
    }

    private static String text(final String raw) {
        return raw == null ? "" : raw;
    }

    /** Keeps an error message inside the column and out of the way. */
    private static String shortened(final String error) {
        final String message = text(error);
        return message.length() <= 250 ? message : message.substring(0, 250);
    }
}
