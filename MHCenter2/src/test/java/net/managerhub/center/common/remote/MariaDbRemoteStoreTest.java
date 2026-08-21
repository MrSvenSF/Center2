package net.managerhub.center.common.remote;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The parts of the MariaDB store that can be checked without a database server.
 *
 * <p>What matters here is the SQL itself: every value has to travel as a
 * parameter, and no credential may end up in a text that can be logged. Whether
 * MariaDB answers correctly is not something a unit test can decide - that needs
 * a real database, and this test does not pretend otherwise.</p>
 */
class MariaDbRemoteStoreTest {

    private static final RemoteSettings.Database SETTINGS =
            new RemoteSettings.Database("db.example", 3306, "mhcenter2", "mhcenter2", "very-secret", true);

    @Test
    @DisplayName("the connection url carries no password")
    void urlHasNoPassword() {
        final String url = MariaDbRemoteStore.url(SETTINGS);

        assertFalse(url.contains("very-secret"), url);
        // The names of the allowed authentication plugins contain the word, so
        // what really matters is that there is no password parameter at all.
        assertFalse(url.toLowerCase(java.util.Locale.ROOT).contains("password="), url);
        assertFalse(url.contains("mhcenter2:"), url);
        assertTrue(url.startsWith("jdbc:mariadb://db.example:3306/mhcenter2"), url);
    }

    @Test
    @DisplayName("SSL is really switched on and off by the configuration")
    void sslIsPartOfTheUrl() {
        assertTrue(MariaDbRemoteStore.url(SETTINGS).contains("sslMode=verify-full"));
        assertTrue(MariaDbRemoteStore.url(new RemoteSettings.Database(
                "db.example", 3306, "mhcenter2", "mhcenter2", "", false)).contains("sslMode=disable"));
    }

    @Test
    @DisplayName("the description of a connection names no user and no password")
    void descriptionIsSafeToLog() {
        final String described = SETTINGS.describe();

        assertFalse(described.contains("very-secret"), described);
        assertFalse(described.contains("mhcenter2@"), described);
        assertTrue(described.contains("db.example:3306/mhcenter2"), described);
    }

    @Test
    @DisplayName("the pool reuses connections instead of opening one per poll")
    void poolIsConfigured() {
        final String url = MariaDbRemoteStore.url(SETTINGS);

        assertTrue(url.contains("maxPoolSize="), url);
        assertTrue(url.contains("connectTimeout="), url);
    }

    @Test
    @DisplayName("only password based authentication is allowed")
    void authenticationIsRestricted() {
        final String url = MariaDbRemoteStore.url(SETTINGS);

        assertTrue(url.contains("restrictedAuth="), url);
        // Kerberos is not in the list on purpose: MHCenter2 never uses it, and
        // having it available makes the driver build a login context nobody
        // asked for.
        assertFalse(url.contains("gssapi"), url);
    }

    @Test
    @DisplayName("no value of a module, a player or a server is ever pasted into an SQL text")
    void everyValueIsAParameter() throws Exception {
        final RecordingConnection recorder = new RecordingConnection();
        final RemoteStore store = new MariaDbRemoteStore(SETTINGS, recorder::connection);

        store.putData("inventorysync", "transfer:Steve'; DROP TABLE center_actions; --",
                "inventory".getBytes(StandardCharsets.UTF_8), System.currentTimeMillis() + 1000L);

        // The dangerous text arrived as a bound parameter, not inside the SQL.
        final String sql = recorder.statements().getFirst();
        assertFalse(sql.contains("DROP TABLE center_actions"), sql);
        assertFalse(sql.contains("inventorysync"), sql);
        assertTrue(sql.contains("?"), sql);
        assertTrue(recorder.parameters().contains("transfer:Steve'; DROP TABLE center_actions; --"),
                recorder.parameters().toString());
    }

    @Test
    @DisplayName("every statement of the store is prepared, none is a plain statement with values")
    void everyStatementIsPrepared() throws Exception {
        final RecordingConnection recorder = new RecordingConnection();
        final RemoteStore store = new MariaDbRemoteStore(SETTINGS, recorder::connection);

        store.heartbeat(new RemoteNode("lobby", "run-1",
                net.managerhub.center.api.ModulePlatform.PAPER, "0.4.0", "1.21.11", 1L));
        store.claim(java.util.UUID.randomUUID(), "lobby");
        store.deleteData("inventorysync", "transfer:steve");

        assertTrue(recorder.plainStatements().isEmpty(),
                "a value must never travel in a plain statement: " + recorder.plainStatements());
        assertFalse(recorder.statements().isEmpty());
        for (final String sql : recorder.statements()) {
            assertTrue(sql.contains("?"), "a statement without a parameter: " + sql);
        }
    }

    /** Every SQL text and every bound value of one test run. */
    private static final class RecordingConnection {

        private final List<String> statements = new java.util.ArrayList<>();
        private final List<String> plainStatements = new java.util.ArrayList<>();
        private final List<Object> parameters = new java.util.ArrayList<>();

        List<String> statements() {
            return statements;
        }

        List<String> plainStatements() {
            return plainStatements;
        }

        List<Object> parameters() {
            return parameters;
        }

        java.sql.Connection connection() {
            return (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[] {java.sql.Connection.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "prepareStatement" -> {
                            statements.add((String) arguments[0]);
                            yield preparedStatement();
                        }
                        case "createStatement" -> plainStatement();
                        case "close" -> null;
                        case "toString" -> "Connection";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == arguments[0];
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }

        private Object preparedStatement() {
            return java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[] {java.sql.PreparedStatement.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "setString", "setLong", "setInt", "setBytes" -> {
                            parameters.add(arguments[1]);
                            yield null;
                        }
                        case "executeUpdate" -> 1;
                        case "setQueryTimeout", "close" -> null;
                        case "toString" -> "PreparedStatement";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == arguments[0];
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }

        private Object plainStatement() {
            return java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[] {java.sql.Statement.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "executeUpdate", "execute" -> {
                            plainStatements.add((String) arguments[0]);
                            yield 0;
                        }
                        case "setQueryTimeout", "close" -> null;
                        case "toString" -> "Statement";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == arguments[0];
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }

}
