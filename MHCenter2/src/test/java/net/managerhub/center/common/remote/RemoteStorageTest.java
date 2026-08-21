package net.managerhub.center.common.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import net.managerhub.center.api.ModulePlatform;
import net.managerhub.center.api.ModuleRemoteException;
import net.managerhub.center.api.ModuleStorage;
import net.managerhub.center.common.language.TestLanguages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The remote-only storage a module gets.
 *
 * <p>The most important test in here is the one that is not about a feature at
 * all: when the remote database is gone, nothing is written into the local
 * SQLite database. A player transfer that silently landed on one server would
 * look successful and would still lose the data.</p>
 */
class RemoteStorageTest {

    private static final Duration MINUTE = Duration.ofMinutes(1);

    private InMemoryRemoteStore store;
    private AtomicLong clock;
    private RemoteService service;

    @BeforeEach
    void prepare() {
        store = new InMemoryRemoteStore();
        clock = new AtomicLong(System.currentTimeMillis());
        service = new RemoteService(ModulePlatform.PAPER, "1.21.11", RemoteActionsTest.silentLogger(),
                TestLanguages::complete, database -> store, action -> { }, clock::get);
        service.apply(RemoteActionsTest.settings("lobby"));
        service.heartbeatTick();
    }

    @Test
    @DisplayName("what a module writes it can read again")
    void writeAndRead() throws Exception {
        final ModuleStorage storage = storage("inventorysync");

        storage.put("transfer:steve", bytes("inventory"), MINUTE);

        assertEquals("inventory", text(storage.get("transfer:steve")));
    }

    @Test
    @DisplayName("a deleted entry is gone")
    void delete() throws Exception {
        final ModuleStorage storage = storage("inventorysync");
        storage.put("transfer:steve", bytes("inventory"), MINUTE);

        assertTrue(storage.delete("transfer:steve"));

        assertTrue(storage.get("transfer:steve").isEmpty());
        assertFalse(storage.delete("transfer:steve"), "a second delete has nothing left to remove");
    }

    @Test
    @DisplayName("take gives the data exactly once")
    void takeConsumesTheEntry() throws Exception {
        final ModuleStorage storage = storage("inventorysync");
        storage.put("transfer:steve", bytes("inventory"), MINUTE);

        assertEquals("inventory", text(storage.take("transfer:steve")));

        // This is what keeps two target servers from applying one inventory twice.
        assertTrue(storage.take("transfer:steve").isEmpty(), "the second take gets nothing");
        assertTrue(storage.get("transfer:steve").isEmpty());
    }

    @Test
    @DisplayName("two servers asking at the same moment: only one gets the data")
    void onlyOneServerWinsTheTake() throws Exception {
        final ModuleStorage first = storage("inventorysync");
        final ModuleStorage second = storageOfAnotherNode("inventorysync", "survival");
        first.put("transfer:steve", bytes("inventory"), MINUTE);

        final Optional<byte[]> won = first.take("transfer:steve");
        final Optional<byte[]> lost = second.take("transfer:steve");

        assertTrue(won.isPresent());
        assertTrue(lost.isEmpty());
    }

    @Test
    @DisplayName("an entry that ran out of time is not returned any more")
    void expiredEntryIsNotReturned() throws Exception {
        final ModuleStorage storage = storage("inventorysync");
        storage.put("transfer:steve", bytes("inventory"), Duration.ofSeconds(30));

        clock.addAndGet(31_000L);
        // The storage uses the real clock for the expiry of an entry, so the test
        // moves the entry into the past instead of the clock into the future.
        store.putData("inventorysync", "transfer:steve", bytes("inventory"),
                System.currentTimeMillis() - 1);

        assertTrue(storage.get("transfer:steve").isEmpty());
        assertTrue(storage.take("transfer:steve").isEmpty());
    }

    @Test
    @DisplayName("expired entries are really removed, so nothing piles up")
    void expiredEntriesArePurged() throws Exception {
        store.putData("inventorysync", "old", bytes("inventory"), System.currentTimeMillis() - 1);
        store.putData("inventorysync", "fresh", bytes("inventory"), System.currentTimeMillis() + 60_000L);

        service.purgeTick();

        assertTrue(store.readData("inventorysync", "old", System.currentTimeMillis()).isEmpty());
        assertTrue(store.readData("inventorysync", "fresh", System.currentTimeMillis()).isPresent());
    }

    @Test
    @DisplayName("one module cannot read the data of another module")
    void namespacesAreSeparated() throws Exception {
        storage("inventorysync").put("transfer:steve", bytes("inventory"), MINUTE);

        assertTrue(storage("othermodule").get("transfer:steve").isEmpty());
    }

    @Test
    @DisplayName("with the remote database gone nothing is written locally, the call fails")
    void noLocalFallbackWhenTheDatabaseIsGone() throws Exception {
        final ModuleStorage storage = storage("inventorysync");
        storage.put("transfer:steve", bytes("inventory"), MINUTE);

        store.failWith("connection refused");
        service.pollTick();
        service.heartbeatTick();

        // Every operation says so instead of quietly doing something else.
        assertThrows(ModuleRemoteException.class, () -> storage.put("transfer:steve", bytes("x"), MINUTE));
        assertThrows(ModuleRemoteException.class, () -> storage.get("transfer:steve"));
        assertThrows(ModuleRemoteException.class, () -> storage.take("transfer:steve"));
        assertThrows(ModuleRemoteException.class, () -> storage.delete("transfer:steve"));
        assertFalse(storage.available());
    }

    @Test
    @DisplayName("with the remote system switched off the storage says so at once")
    void switchedOffStorageFails() {
        final RemoteService off = new RemoteService(ModulePlatform.PAPER, "1.21.11",
                RemoteActionsTest.silentLogger(), TestLanguages::complete,
                database -> store, action -> { }, clock::get);
        off.apply(RemoteSettings.DISABLED);
        final ModuleStorage storage = new ModuleRemoteAccess("inventorysync", off);

        assertFalse(storage.available());
        assertThrows(ModuleRemoteException.class, () -> storage.get("transfer:steve"));
    }

    @Test
    @DisplayName("a key and a payload have limits, and a lifetime is required")
    void limitsAreChecked() {
        final ModuleStorage storage = storage("inventorysync");

        assertThrows(IllegalArgumentException.class, () -> storage.put("", bytes("x"), MINUTE));
        assertThrows(IllegalArgumentException.class,
                () -> storage.put("k".repeat(RemoteService.MAX_STORAGE_KEY + 1), bytes("x"), MINUTE));
        assertThrows(IllegalArgumentException.class,
                () -> storage.put("k", new byte[RemoteService.MAX_STORAGE_PAYLOAD + 1], MINUTE));
        assertThrows(IllegalArgumentException.class, () -> storage.put("k", bytes("x"), Duration.ZERO));
    }

    private ModuleStorage storage(final String moduleId) {
        return new ModuleRemoteAccess(moduleId, service);
    }

    private ModuleStorage storageOfAnotherNode(final String moduleId, final String serverId) {
        final RemoteService other = new RemoteService(ModulePlatform.PAPER, "1.21.11",
                RemoteActionsTest.silentLogger(), TestLanguages::complete,
                database -> store, action -> { }, clock::get);
        other.apply(RemoteActionsTest.settings(serverId));
        other.heartbeatTick();
        return new ModuleRemoteAccess(moduleId, other);
    }

    private static byte[] bytes(final String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(final Optional<byte[]> payload) {
        return new String(payload.orElseThrow(), StandardCharsets.UTF_8);
    }
}
