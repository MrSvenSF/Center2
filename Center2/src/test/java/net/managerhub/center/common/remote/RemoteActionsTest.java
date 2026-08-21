package net.managerhub.center.common.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import net.managerhub.center.Center;
import net.managerhub.center.api.ModuleActionMessage;
import net.managerhub.center.api.ModuleActionTarget;
import net.managerhub.center.api.ModuleLogger;
import net.managerhub.center.api.ModulePlatform;
import net.managerhub.center.common.language.TestLanguages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The rules of the network actions, tested against a store that behaves like the
 * real one but needs no database server.
 */
class RemoteActionsTest {

    private static final Duration MINUTE = Duration.ofMinutes(1);

    private InMemoryRemoteStore store;
    private AtomicLong clock;

    @BeforeEach
    void prepare() {
        store = new InMemoryRemoteStore();
        clock = new AtomicLong(System.currentTimeMillis());
    }

    @Test
    @DisplayName("every action carries an id, and a reload keeps the id it was given")
    void actionsCarryAnId() throws Exception {
        final Node origin = node("lobby", ModulePlatform.PAPER);
        final UUID requestId = UUID.randomUUID();

        final RemoteAction reload = origin.service.publishReload(requestId);
        final RemoteAction moduleAction =
                origin.service.publishModuleAction("mymodule", "TRANSFER", ModuleActionTarget.ALL, payload(), MINUTE);

        assertEquals(requestId, reload.id(), "the reload keeps the request id of the administrator");
        assertEquals(Center.CORE_NAMESPACE, reload.namespace());
        assertEquals(RemoteAction.CENTER_RELOAD, reload.type());
        assertEquals("mymodule", moduleAction.namespace());
        assertFalse(requestId.equals(moduleAction.id()), "every action gets its own id");
        assertEquals(2, store.published().size());
    }

    @Test
    @DisplayName("an ALL action is carried out by every other node, each of them once")
    void allTargetReachesEveryNode() throws Exception {
        final Node origin = node("lobby", ModulePlatform.PAPER);
        final Node second = node("survival", ModulePlatform.PAPER);
        final Node proxy = node("velocity", ModulePlatform.VELOCITY);

        origin.service.publishReload(UUID.randomUUID());

        second.poll();
        proxy.poll();
        origin.poll();

        assertEquals(1, second.coreActions.size());
        assertEquals(1, proxy.coreActions.size(), "the second node runs the same ALL action as well");
        assertEquals(0, origin.coreActions.size(), "the node that asked for it already did it itself");
    }

    @Test
    @DisplayName("a second poll does not run the same action again")
    void receiptPreventsDoubleExecution() throws Exception {
        final Node origin = node("lobby", ModulePlatform.PAPER);
        final Node second = node("survival", ModulePlatform.PAPER);

        origin.service.publishReload(UUID.randomUUID());

        second.poll();
        second.poll();
        second.poll();

        assertEquals(1, second.coreActions.size(), "the receipt is what makes it exactly once");
    }

    @Test
    @DisplayName("a node that restarts still does not run an action it already answered")
    void receiptSurvivesARestartOfTheNode() throws Exception {
        final Node origin = node("lobby", ModulePlatform.PAPER);
        final Node second = node("survival", ModulePlatform.PAPER);
        origin.service.publishReload(UUID.randomUUID());
        second.poll();

        // A new service with the same server-id: that is what a restart looks like.
        final Node restarted = node("survival", ModulePlatform.PAPER);
        restarted.poll();

        assertEquals(0, restarted.coreActions.size());
    }

    @Test
    @DisplayName("a PAPER action reaches the Paper nodes and not the proxy")
    void paperTarget() throws Exception {
        final Node origin = node("lobby", ModulePlatform.PAPER);
        final Node paper = node("survival", ModulePlatform.PAPER);
        final Node proxy = node("velocity", ModulePlatform.VELOCITY);
        paper.listen();
        proxy.listen();

        origin.service.publishModuleAction("mymodule", "PAPER_ONLY",
                ModuleActionTarget.PAPER, payload(), MINUTE);

        paper.poll();
        proxy.poll();

        assertEquals(List.of("PAPER_ONLY"), paper.moduleActions);
        assertEquals(List.of(), proxy.moduleActions);
    }

    @Test
    @DisplayName("a VELOCITY action reaches the proxy and not the Paper nodes")
    void velocityTarget() throws Exception {
        final Node origin = node("lobby", ModulePlatform.PAPER);
        final Node paper = node("survival", ModulePlatform.PAPER);
        final Node proxy = node("velocity", ModulePlatform.VELOCITY);
        paper.listen();
        proxy.listen();

        origin.service.publishModuleAction("mymodule", "PROXY_ONLY",
                ModuleActionTarget.VELOCITY, payload(), MINUTE);

        paper.poll();
        proxy.poll();

        assertEquals(List.of(), paper.moduleActions);
        assertEquals(List.of("PROXY_ONLY"), proxy.moduleActions);
    }

    @Test
    @DisplayName("an action for one server reaches only that server")
    void serverTarget() throws Exception {
        final Node origin = node("lobby", ModulePlatform.PAPER);
        final Node wanted = node("survival", ModulePlatform.PAPER);
        final Node other = node("citybuild", ModulePlatform.PAPER);
        wanted.listen();
        other.listen();

        origin.service.publishModuleAction("mymodule", "HANDOVER",
                ModuleActionTarget.server("survival"), payload(), MINUTE);

        wanted.poll();
        other.poll();

        assertEquals(List.of("HANDOVER"), wanted.moduleActions);
        assertEquals(List.of(), other.moduleActions);
    }

    @Test
    @DisplayName("an action that ran out of time is not carried out any more")
    void expiredActionIsNotExecuted() throws Exception {
        final Node origin = node("lobby", ModulePlatform.PAPER);
        final Node second = node("survival", ModulePlatform.PAPER);

        final RemoteAction action = origin.service.publishReload(UUID.randomUUID());
        clock.set(action.expiresAtMillis() + 1);

        second.poll();

        assertEquals(0, second.coreActions.size());
    }

    @Test
    @DisplayName("an unknown action type of the core is written down and never carried out")
    void unknownCoreActionIsNotExecuted() throws Exception {
        final Node second = node("survival", ModulePlatform.PAPER);
        // Exactly the case the security rule is about: a row that says something
        // Center2 does not know must not turn into anything.
        final UUID id = UUID.randomUUID();
        store.publish(new RemoteAction(id, Center.CORE_NAMESPACE, "op Player", "lobby",
                ModuleActionTarget.ALL, clock.get(), clock.get() + 60_000L, new byte[0]));

        second.poll();

        assertEquals(0, second.coreActions.size());
        assertEquals(RemoteActionStatus.IGNORED, statusOf(id, "survival"));
    }

    @Test
    @DisplayName("an action for a module that is not running here is written down, not carried out")
    void unknownModuleActionIsNotExecuted() throws Exception {
        final Node second = node("survival", ModulePlatform.PAPER);
        final UUID id = UUID.randomUUID();
        store.publish(new RemoteAction(id, "othermodule", "TRANSFER", "lobby",
                ModuleActionTarget.ALL, clock.get(), clock.get() + 60_000L, new byte[0]));

        second.poll();

        assertEquals(List.of(), second.moduleActions);
        assertEquals(RemoteActionStatus.IGNORED, statusOf(id, "survival"));
    }

    @Test
    @DisplayName("a module never gets the namespace of the core")
    void moduleCannotUseTheCoreNamespace() {
        final Node origin = node("lobby", ModulePlatform.PAPER);

        assertThrows(IllegalArgumentException.class, () -> origin.service.publishModuleAction(
                Center.CORE_NAMESPACE, RemoteAction.CENTER_RELOAD, ModuleActionTarget.ALL, payload(), MINUTE));
    }

    @Test
    @DisplayName("an action type has to be a name, not a command line")
    void actionTypeIsValidated() {
        final Node origin = node("lobby", ModulePlatform.PAPER);

        assertThrows(IllegalArgumentException.class, () -> origin.service.publishModuleAction(
                "mymodule", "op Player", ModuleActionTarget.ALL, payload(), MINUTE));
        assertThrows(IllegalArgumentException.class, () -> origin.service.publishModuleAction(
                "mymodule", "", ModuleActionTarget.ALL, payload(), MINUTE));
    }

    @Test
    @DisplayName("the payload of a module arrives unchanged")
    void payloadArrives() throws Exception {
        final Node origin = node("lobby", ModulePlatform.PAPER);
        final Node second = node("survival", ModulePlatform.PAPER);
        final List<byte[]> received = new ArrayList<>();
        second.service.moduleListener("mymodule", action -> received.add(action.payload()));

        origin.service.publishModuleAction("mymodule", "TRANSFER",
                ModuleActionTarget.ALL, payload(), MINUTE);
        second.poll();

        assertEquals(1, received.size());
        assertEquals("inventory-blob", new String(received.getFirst(), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("a module that throws does not stop the other actions of the poll")
    void aFailingModuleIsIsolated() throws Exception {
        final Node origin = node("lobby", ModulePlatform.PAPER);
        final Node second = node("survival", ModulePlatform.PAPER);
        second.service.moduleListener("broken", action -> {
            throw new IllegalStateException("the module is broken");
        });

        final RemoteAction broken = origin.service.publishModuleAction("broken", "BOOM",
                ModuleActionTarget.ALL, payload(), MINUTE);
        origin.service.publishReload(UUID.randomUUID());

        second.poll();

        assertEquals(RemoteActionStatus.FAILED, statusOf(broken.id(), "survival"));
        assertEquals(1, second.coreActions.size(), "the reload behind the broken action still ran");
    }

    private RemoteActionStatus statusOf(final UUID actionId, final String serverId) throws RemoteException {
        return store.receipts(actionId).stream()
                .filter(receipt -> receipt.serverId().equalsIgnoreCase(serverId))
                .findFirst()
                .orElseThrow()
                .status();
    }

    private static byte[] payload() {
        return "inventory-blob".getBytes(StandardCharsets.UTF_8);
    }

    private Node node(final String serverId, final ModulePlatform platform) {
        return new Node(serverId, platform, store, clock);
    }

    /** One Center2 node in the test network. */
    private static final class Node {

        private final RemoteService service;
        private final List<RemoteAction> coreActions = new ArrayList<>();
        private final List<String> moduleActions = new ArrayList<>();

        private Node(final String serverId,
                     final ModulePlatform platform,
                     final InMemoryRemoteStore store,
                     final AtomicLong clock) {
            this.service = new RemoteService(platform, "1.21.11", silentLogger(),
                    TestLanguages::complete,
                    database -> store,
                    coreActions::add,
                    clock::get);
            this.service.apply(settings(serverId));
            // One heartbeat brings the node online, exactly like at startup.
            this.service.heartbeatTick();
        }

        private void listen() {
            service.moduleListener("mymodule", this::record);
        }

        private void record(final ModuleActionMessage action) {
            moduleActions.add(action.type());
        }

        private void poll() {
            service.pollTick();
        }
    }

    static RemoteSettings settings(final String serverId) {
        return new RemoteSettings(true, serverId,
                new RemoteSettings.Database("db.example", 3306, "center2", "center2", "", true),
                new RemoteSettings.Polling(1000, 60),
                new RemoteSettings.Heartbeat(10));
    }

    static ModuleLogger silentLogger() {
        return new ModuleLogger() {

            @Override
            public void info(final String message) {
                // The tests check behaviour, not the log.
            }

            @Override
            public void warn(final String message) {
                // The tests check behaviour, not the log.
            }

            @Override
            public void error(final String message, final Throwable failure) {
                // The tests check behaviour, not the log.
            }
        };
    }

}
