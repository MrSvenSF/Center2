package net.managerhub.center.common.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the {@code remote} section of {@code MainConfig.yml} has to look like
 * before MHCenter2 opens a connection with it.
 */
class RemoteSettingsTest {

    @Test
    @DisplayName("switched off is always fine, whatever else the section says")
    void disabledIsNeverAProblem() {
        final RemoteSettings settings = new RemoteSettings(false, "",
                new RemoteSettings.Database("", 0, "", "", "", true),
                new RemoteSettings.Polling(0, 0),
                new RemoteSettings.Heartbeat(0));

        assertTrue(settings.problems().isEmpty(),
                "a section nobody uses must not stop the server from starting");
        assertFalse(settings.usable());
    }

    @Test
    @DisplayName("a complete section is usable")
    void completeSettingsAreUsable() {
        assertTrue(RemoteActionsTest.settings("lobby").usable());
        assertTrue(RemoteActionsTest.settings("lobby").problems().isEmpty());
    }

    @Test
    @DisplayName("every missing value is named, not just the first one")
    void namesEveryProblem() {
        final RemoteSettings settings = new RemoteSettings(true, "",
                new RemoteSettings.Database("", 3306, "", "", "", true),
                new RemoteSettings.Polling(1000, 60),
                new RemoteSettings.Heartbeat(10));

        assertEquals(4, settings.problems().size(), settings.problems().toString());
        assertFalse(settings.usable());
    }

    @Test
    @DisplayName("a server-id is normalized and checked")
    void serverIdIsChecked() {
        assertEquals("lobby", RemoteSettings.normalizeServerId("  Lobby  "));
        assertEquals("", RemoteSettings.normalizeServerId(null));
        assertTrue(withServerId("lobby one").problems().stream()
                .anyMatch(problem -> problem.contains("remote.server-id")));
        assertTrue(withServerId("lobby").problems().isEmpty());
    }

    @Test
    @DisplayName("an interval outside the allowed range is refused")
    void intervalsAreChecked() {
        final RemoteSettings tooFast = new RemoteSettings(true, "lobby",
                new RemoteSettings.Database("db", 3306, "mhcenter2", "mhcenter2", "", true),
                new RemoteSettings.Polling(1, 60),
                new RemoteSettings.Heartbeat(10));
        final RemoteSettings tooLong = new RemoteSettings(true, "lobby",
                new RemoteSettings.Database("db", 3306, "mhcenter2", "mhcenter2", "", true),
                new RemoteSettings.Polling(1000, 999999),
                new RemoteSettings.Heartbeat(10));

        assertTrue(tooFast.problems().getFirst().contains("interval-ms"));
        assertTrue(tooLong.problems().getFirst().contains("action-ttl-seconds"));
    }

    @Test
    @DisplayName("a node counts as offline after three missed heartbeats")
    void offlineWindow() {
        assertEquals(30, new RemoteSettings.Heartbeat(10).offlineAfterSeconds());
    }

    @Test
    @DisplayName("only a change of the connection itself rebuilds it")
    void connectionDiffers() {
        final RemoteSettings base = RemoteActionsTest.settings("lobby");
        final RemoteSettings otherInterval = new RemoteSettings(true, "lobby", base.database(),
                new RemoteSettings.Polling(2000, 60), base.heartbeat());
        final RemoteSettings otherId = RemoteActionsTest.settings("survival");

        assertFalse(base.connectionDiffers(otherInterval),
                "a changed interval only restarts the tasks, it does not reconnect");
        assertTrue(base.connectionDiffers(otherId));
        assertTrue(base.connectionDiffers(RemoteSettings.DISABLED));
        assertTrue(base.connectionDiffers(null));
    }

    private static RemoteSettings withServerId(final String serverId) {
        return new RemoteSettings(true, serverId,
                new RemoteSettings.Database("db", 3306, "mhcenter2", "mhcenter2", "", true),
                new RemoteSettings.Polling(1000, 60),
                new RemoteSettings.Heartbeat(10));
    }
}
