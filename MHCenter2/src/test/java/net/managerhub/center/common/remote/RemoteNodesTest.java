package net.managerhub.center.common.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import net.managerhub.center.api.ModuleLogger;
import net.managerhub.center.api.ModulePlatform;
import net.managerhub.center.common.language.TestLanguages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The node list of the remote database: heartbeats, who is online, and what
 * happens when two nodes were given the same id.
 */
class RemoteNodesTest {

    private InMemoryRemoteStore store;
    private AtomicLong clock;

    @BeforeEach
    void prepare() {
        store = new InMemoryRemoteStore();
        clock = new AtomicLong(System.currentTimeMillis());
    }

    @Test
    @DisplayName("a Paper node reports itself with its Minecraft version")
    void paperHeartbeat() throws Exception {
        final RemoteService paper = service("lobby", ModulePlatform.PAPER, "1.21.11");

        paper.heartbeatTick();

        final RemoteNode node = store.node("lobby").orElseThrow();
        assertEquals(ModulePlatform.PAPER, node.platform());
        assertEquals("1.21.11", node.minecraftVersion());
        assertEquals(RemoteService.State.ONLINE, paper.state());
    }

    @Test
    @DisplayName("the proxy reports itself without a Minecraft version")
    void velocityHeartbeat() throws Exception {
        final RemoteService proxy = service("velocity", ModulePlatform.VELOCITY, "");

        proxy.heartbeatTick();

        final RemoteNode node = store.node("velocity").orElseThrow();
        assertEquals(ModulePlatform.VELOCITY, node.platform());
        assertEquals("", node.minecraftVersion(), "the proxy has no single Minecraft version and invents none");
    }

    @Test
    @DisplayName("every node with its own id shows up in the list")
    void differentIdsAreAllOnline() {
        final RemoteService lobby = service("lobby", ModulePlatform.PAPER, "1.21.11");
        final RemoteService survival = service("survival", ModulePlatform.PAPER, "1.21.11");
        final RemoteService proxy = service("velocity", ModulePlatform.VELOCITY, "");

        lobby.heartbeatTick();
        survival.heartbeatTick();
        proxy.heartbeatTick();
        lobby.heartbeatTick();

        assertEquals(List.of("lobby", "survival", "velocity"), lobby.onlineNodes());
        assertEquals(RemoteService.State.ONLINE, survival.state());
    }

    @Test
    @DisplayName("a second node with the same server-id switches its remote system off")
    void duplicateServerIdIsRefused() {
        final RemoteService first = service("lobby", ModulePlatform.PAPER, "1.21.11");
        first.heartbeatTick();

        // Same id, different run: this is the situation that makes the network
        // unable to tell the two nodes apart.
        final RemoteService second = service("lobby", ModulePlatform.PAPER, "1.21.11");
        second.heartbeatTick();

        assertEquals(RemoteService.State.CONFLICT, second.state());
        assertFalse(second.available());
        assertTrue(second.onlineNodes().isEmpty());
        assertEquals(RemoteService.State.ONLINE, first.state(), "the node that was there first keeps working");
    }

    @Test
    @DisplayName("a node in conflict does not quietly come back on the next tick")
    void conflictStays() {
        final RemoteService first = service("lobby", ModulePlatform.PAPER, "1.21.11");
        first.heartbeatTick();
        final RemoteService second = service("lobby", ModulePlatform.PAPER, "1.21.11");
        second.heartbeatTick();

        second.heartbeatTick();
        second.pollTick();

        assertEquals(RemoteService.State.CONFLICT, second.state());
    }

    @Test
    @DisplayName("an id whose old node is long gone may be taken over")
    void staleRowIsNotAConflict() throws Exception {
        final RemoteService first = service("lobby", ModulePlatform.PAPER, "1.21.11");
        first.heartbeatTick();
        // The row of a run that stopped without saying goodbye, for example after
        // a crash. It is older than three heartbeat intervals.
        final RemoteNode stale = store.node("lobby").orElseThrow();
        store.heartbeat(new RemoteNode(stale.serverId(), stale.runtimeId(), stale.platform(),
                stale.centerVersion(), stale.minecraftVersion(), System.currentTimeMillis() - 120_000L));

        final RemoteService replacement = service("lobby", ModulePlatform.PAPER, "1.21.11");
        replacement.heartbeatTick();

        assertEquals(RemoteService.State.ONLINE, replacement.state());
    }

    @Test
    @DisplayName("a node that stopped reporting counts as offline")
    void oldHeartbeatCountsAsOffline() throws Exception {
        final RemoteService lobby = service("lobby", ModulePlatform.PAPER, "1.21.11");
        lobby.heartbeatTick();
        store.heartbeat(new RemoteNode("survival", "run-2", ModulePlatform.PAPER, "0.4.0", "1.21.11",
                System.currentTimeMillis() - 120_000L));

        lobby.heartbeatTick();

        assertEquals(List.of("lobby"), lobby.onlineNodes());
    }

    @Test
    @DisplayName("a clean shutdown takes the node out of the list right away")
    void stopRemovesTheNode() throws Exception {
        final RemoteService lobby = service("lobby", ModulePlatform.PAPER, "1.21.11");
        lobby.heartbeatTick();

        lobby.stop();

        assertTrue(store.node("lobby").isEmpty());
        assertTrue(store.closed());
    }

    @Test
    @DisplayName("without a server-id the remote system does not start at all")
    void missingServerIdIsRefused() {
        final RemoteSettings withoutId = new RemoteSettings(true, "",
                new RemoteSettings.Database("db.example", 3306, "mhcenter2", "mhcenter2", "", true),
                new RemoteSettings.Polling(1000, 60),
                new RemoteSettings.Heartbeat(10));

        assertFalse(withoutId.usable());
        assertTrue(withoutId.problems().getFirst().contains("remote.server-id"));

        final RemoteService service = new RemoteService(ModulePlatform.PAPER, "1.21.11",
                RemoteActionsTest.silentLogger(), TestLanguages::complete,
                database -> store, action -> { }, clock::get);
        service.apply(withoutId);

        assertEquals(RemoteService.State.OFF, service.state());
        assertEquals(0, store.initializeCount(), "a broken configuration never opens a connection");
    }

    @Test
    @DisplayName("switched off means no connection attempt at all")
    void disabledNeverConnects() {
        final RemoteService service = new RemoteService(ModulePlatform.PAPER, "1.21.11",
                RemoteActionsTest.silentLogger(), TestLanguages::complete,
                database -> store, action -> { }, clock::get);

        service.apply(RemoteSettings.DISABLED);
        service.heartbeatTick();
        service.pollTick();

        assertEquals(RemoteService.State.OFF, service.state());
        assertEquals(0, store.initializeCount());
    }

    private RemoteService service(final String serverId,
                                  final ModulePlatform platform,
                                  final String minecraftVersion) {
        final ModuleLogger logger = RemoteActionsTest.silentLogger();
        final RemoteService service = new RemoteService(platform, minecraftVersion, logger,
                TestLanguages::complete, database -> store, action -> { }, clock::get);
        service.apply(RemoteActionsTest.settings(serverId));
        return service;
    }
}
