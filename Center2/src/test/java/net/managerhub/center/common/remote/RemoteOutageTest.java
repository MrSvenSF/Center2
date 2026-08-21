package net.managerhub.center.common.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import net.managerhub.center.api.ModuleLogger;
import net.managerhub.center.api.ModulePlatform;
import net.managerhub.center.common.language.TestLanguages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What happens when the remote database is not there.
 *
 * <p>The answer has to be: nothing dramatic. Center2 keeps running, the failure
 * is written down a few times instead of thousands of times, the pause between
 * two attempts grows, and everything comes back on its own when the database
 * does.</p>
 */
class RemoteOutageTest {

    private InMemoryRemoteStore store;
    private AtomicLong clock;
    private RecordingLogger log;
    private RemoteService service;

    @BeforeEach
    void prepare() {
        store = new InMemoryRemoteStore();
        clock = new AtomicLong(System.currentTimeMillis());
        log = new RecordingLogger();
        service = new RemoteService(ModulePlatform.PAPER, "1.21.11", log,
                TestLanguages::complete, database -> store, action -> { }, clock::get);
        service.apply(RemoteActionsTest.settings("lobby"));
        service.heartbeatTick();
    }

    @Test
    @DisplayName("a database that goes away puts the node offline, nothing else")
    void outageOnlyGoesOffline() {
        assertEquals(RemoteService.State.ONLINE, service.state());

        store.failWith("connection refused");
        service.heartbeatTick();

        assertEquals(RemoteService.State.OFFLINE, service.state());
        assertFalse(service.available());
        assertTrue(service.reasonForUnavailable().contains("not reachable"));
    }

    @Test
    @DisplayName("the same failure is not written into the log every single time")
    void logIsNotFlooded() {
        store.failWith("connection refused");

        for (int attempt = 0; attempt < 30; attempt++) {
            // Every attempt is allowed to run: the clock is moved past the pause.
            clock.addAndGet(RemoteService.MAX_RETRY_MS + 1);
            service.heartbeatTick();
        }

        assertTrue(log.warnings.size() <= 4,
                "30 failed attempts wrote " + log.warnings.size() + " lines: " + log.warnings);
        assertTrue(log.warnings.getFirst().contains("remote.unreachable"));
    }

    @Test
    @DisplayName("the pause between two attempts grows instead of hammering the database")
    void backoffGrows() {
        store.failWith("connection refused");
        service.heartbeatTick();
        final int afterFirst = store.initializeCount();

        // Right after a failure the next tick does nothing at all.
        service.heartbeatTick();
        assertEquals(afterFirst, store.initializeCount(), "a tick inside the pause does not try again");

        clock.addAndGet(RemoteService.MIN_RETRY_MS + 1);
        service.heartbeatTick();
        assertTrue(store.initializeCount() > afterFirst, "once the pause is over it tries again");
    }

    @Test
    @DisplayName("when the database comes back the node comes back with it")
    void reconnects() {
        store.failWith("connection refused");
        service.heartbeatTick();
        assertEquals(RemoteService.State.OFFLINE, service.state());

        store.failWith(null);
        clock.addAndGet(RemoteService.MAX_RETRY_MS + 1);
        service.heartbeatTick();

        assertEquals(RemoteService.State.ONLINE, service.state());
        assertTrue(log.infos.stream().anyMatch(line -> line.contains("remote.connected")));
    }

    @Test
    @DisplayName("an action cannot be written while the database is gone, and it is not lost locally either")
    void publishFailsHonestly() {
        store.failWith("connection refused");
        service.heartbeatTick();

        assertThrows(RemoteException.class, () -> service.publishReload(UUID.randomUUID()));
    }

    @Test
    @DisplayName("a poll during the outage does nothing and does not throw")
    void pollDuringOutageIsQuiet() {
        store.failWith("connection refused");
        service.heartbeatTick();
        final int warnings = log.warnings.size();

        service.pollTick();
        service.purgeTick();

        assertEquals(warnings, log.warnings.size(), "a poll while offline says nothing new");
    }

    /** Remembers what was logged, so the test can count the lines. */
    private static final class RecordingLogger implements ModuleLogger {

        private final List<String> infos = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();

        @Override
        public void info(final String message) {
            infos.add(message);
        }

        @Override
        public void warn(final String message) {
            warnings.add(message);
        }

        @Override
        public void error(final String message, final Throwable failure) {
            errors.add(message);
        }
    }
}
