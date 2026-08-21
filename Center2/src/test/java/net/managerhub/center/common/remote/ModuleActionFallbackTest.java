package net.managerhub.center.common.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import net.managerhub.center.api.ModuleActionTarget;
import net.managerhub.center.api.ModuleLogger;
import net.managerhub.center.api.ModulePlatform;
import net.managerhub.center.common.language.TestLanguages;
import org.junit.jupiter.api.Test;

class ModuleActionFallbackTest {

    @Test
    void usesPluginMessagingWhenMariaDbIsOffline() throws Exception {
        final InMemoryRemoteStore store = new InMemoryRemoteStore();
        final RemoteService remote = new RemoteService(ModulePlatform.PAPER, "1.21.11", new QuietLogger(),
                TestLanguages::complete, ignored -> store, action -> { }, System::currentTimeMillis);
        remote.apply(RemoteActionsTest.settings("lobby"));
        remote.heartbeatTick();
        store.failWith("offline");
        remote.heartbeatTick();

        final AtomicReference<RemoteAction> sent = new AtomicReference<>();
        final ModuleActionFallback fallback = new ModuleActionFallback() {
            @Override public boolean available() { return true; }
            @Override public String serverId() { return "lobby"; }
            @Override public List<String> onlineNodes() { return List.of("lobby", "survival"); }
            @Override public void send(final RemoteAction action) { sent.set(action); }
        };
        final ModuleRemoteAccess access = new ModuleRemoteAccess("homes", remote, fallback);

        access.send("SYNC", ModuleActionTarget.server("survival"), new byte[] {7}, Duration.ofSeconds(5));

        assertTrue(access.available());
        org.junit.jupiter.api.Assertions.assertFalse(access.storage().available());
        assertEquals("homes", sent.get().namespace());
        assertEquals("survival", sent.get().target().serverId());
    }

    private static final class QuietLogger implements ModuleLogger {
        @Override public void info(final String message) { }
        @Override public void warn(final String message) { }
        @Override public void error(final String message, final Throwable failure) { }
    }
}
