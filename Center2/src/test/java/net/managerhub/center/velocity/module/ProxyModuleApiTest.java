package net.managerhub.center.velocity.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.Scheduler;
import com.velocitypowered.api.scheduler.TaskStatus;
import net.managerhub.center.api.velocity.VelocityModuleApi;
import net.managerhub.center.common.module.ModuleCleanup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Velocity part of the module API.
 *
 * <p>The point of every test in here is the same: whatever a module registers
 * through Center2 is removed again when the module stops. A proxy module must not
 * be able to leave an event handler or a scheduled task behind.</p>
 */
class ProxyModuleApiTest {

    /** Stands for the Center2 plugin instance Velocity wants for a registration. */
    private static final Object PLUGIN = new Object();

    private ModuleCleanup cleanup;
    private List<String> events;
    private List<String> cancelled;
    private ProxyServer proxy;

    @BeforeEach
    void prepare() {
        cleanup = new ModuleCleanup();
        events = new ArrayList<>();
        cancelled = new ArrayList<>();
        proxy = proxy(server("lobby"), server("survival"));
    }

    @Test
    @DisplayName("a module can subscribe to a Velocity event")
    void subscribeToAnEvent() {
        final VelocityModuleApi api = api();

        api.subscribe(ServerPostConnectEvent.class, event -> { });

        assertEquals(List.of("register:" + ServerPostConnectEvent.class.getName() + ":NORMAL"), events);
    }

    @Test
    @DisplayName("the subscription is removed again when the module is stopped")
    void subscriptionIsCleanedUp() {
        final VelocityModuleApi api = api();
        api.subscribe(ServerPostConnectEvent.class, event -> { });

        cleanup.runAll();

        assertEquals(List.of("register:" + ServerPostConnectEvent.class.getName() + ":NORMAL",
                "unregister-handler"), events);
    }

    @Test
    @DisplayName("a module can choose when its handler runs")
    void subscribeWithAnOrder() {
        final VelocityModuleApi api = api();

        api.subscribe(ProxyPingEvent.class, PostOrder.LATE, event -> { });

        assertEquals(List.of("register:" + ProxyPingEvent.class.getName() + ":LATE"), events);
    }

    @Test
    @DisplayName("the server list ping is a normal event, so a module can change the MOTD")
    void proxyPingIsReachable() {
        final VelocityModuleApi api = api();

        api.subscribe(ProxyPingEvent.class, event -> { });

        assertTrue(events.getFirst().contains(ProxyPingEvent.class.getName()),
                "the ping event of Velocity is what a MOTD module subscribes to");
    }

    @Test
    @DisplayName("a listener object is registered and unregistered again")
    void listenerObject() {
        final VelocityModuleApi api = api();
        final Object listener = new Object();

        api.subscribe(listener);
        cleanup.runAll();

        assertEquals(List.of("register-listener", "unregister-listener"), events);
    }

    @Test
    @DisplayName("a scheduled task is cancelled when the module is stopped")
    void scheduledTaskIsCancelled() {
        final VelocityModuleApi api = api();

        final VelocityModuleApi.ProxyTask task =
                api.schedule(() -> { }, Duration.ofSeconds(1), Duration.ofSeconds(5));

        assertTrue(task.active());
        cleanup.runAll();
        assertEquals(1, cancelled.size());
        assertFalse(task.active());
    }

    @Test
    @DisplayName("a module can cancel its own task earlier")
    void taskCanBeCancelledByTheModule() {
        final VelocityModuleApi api = api();
        final VelocityModuleApi.ProxyTask task = api.schedule(() -> { }, Duration.ZERO, Duration.ZERO);

        task.cancel();

        assertFalse(task.active());
    }

    @Test
    @DisplayName("a module sees the backend servers of the proxy")
    void sees() {
        final VelocityModuleApi api = api();

        assertEquals(2, api.servers().size());
        assertTrue(api.server("lobby").isPresent());
        assertTrue(api.server("unknown").isEmpty());
        assertTrue(api.server(null).isEmpty());
    }

    @Test
    @DisplayName("the proxy itself is reachable for everything else")
    void proxyIsReachable() {
        assertSame(proxy, api().proxy());
    }

    private VelocityModuleApi api() {
        return new ProxyModuleApi(proxy, PLUGIN, cleanup);
    }

    private ProxyServer proxy(final RegisteredServer... servers) {
        final List<RegisteredServer> all = List.of(servers);
        final EventManager eventManager = eventManager();
        final Scheduler scheduler = scheduler();
        return (ProxyServer) stub(ProxyServer.class, (method, arguments) -> switch (method.getName()) {
            case "getAllServers" -> all;
            case "getEventManager" -> eventManager;
            case "getScheduler" -> scheduler;
            case "getPlayerCount" -> 3;
            case "getServer" -> all.stream()
                    .filter(server -> server.getServerInfo().getName().equals(arguments[0]))
                    .findFirst();
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private EventManager eventManager() {
        return (EventManager) stub(EventManager.class, (method, arguments) -> switch (method.getName()) {
            case "register" -> {
                if (arguments.length == 2) {
                    events.add("register-listener");
                } else {
                    events.add("register:" + ((Class<?>) arguments[1]).getName() + ":" + arguments[2]);
                }
                yield null;
            }
            case "unregister" -> {
                events.add("unregister-handler");
                yield null;
            }
            case "unregisterListener" -> {
                events.add("unregister-listener");
                yield null;
            }
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private Scheduler scheduler() {
        return (Scheduler) stub(Scheduler.class, (method, arguments) -> {
            if (!"buildTask".equals(method.getName())) {
                throw new UnsupportedOperationException(method.getName());
            }
            final AtomicReference<Object> builder = new AtomicReference<>();
            builder.set(stub(Scheduler.TaskBuilder.class, (builderMethod, builderArguments) ->
                    switch (builderMethod.getName()) {
                        case "delay", "repeat", "clearDelay", "clearRepeat" -> builder.get();
                        case "schedule" -> task();
                        default -> throw new UnsupportedOperationException(builderMethod.getName());
                    }));
            return builder.get();
        });
    }

    private ScheduledTask task() {
        final AtomicBoolean running = new AtomicBoolean(true);
        return (ScheduledTask) stub(ScheduledTask.class, (method, arguments) -> switch (method.getName()) {
            case "cancel" -> {
                cancelled.add("cancel");
                running.set(false);
                yield null;
            }
            case "status" -> running.get() ? TaskStatus.SCHEDULED : TaskStatus.CANCELLED;
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private static RegisteredServer server(final String name) {
        final ServerInfo info = new ServerInfo(name, new InetSocketAddress("127.0.0.1", 25566));
        return (RegisteredServer) stub(RegisteredServer.class, (method, arguments) -> switch (method.getName()) {
            case "getServerInfo" -> info;
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    /** Answers only the few methods the API really uses. */
    private static Object stub(final Class<?> type, final Answer answer) {
        return Proxy.newProxyInstance(
                ProxyModuleApiTest.class.getClassLoader(),
                new Class<?>[] {type},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "toString" -> type.getSimpleName();
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> answer.answer(method, arguments);
                });
    }

    @FunctionalInterface
    private interface Answer {
        Object answer(Method method, Object[] arguments);
    }

}
