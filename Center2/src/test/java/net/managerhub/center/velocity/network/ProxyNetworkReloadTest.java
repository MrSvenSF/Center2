package net.managerhub.center.velocity.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.ChannelMessageSink;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.api.scheduler.Scheduler;
import net.managerhub.center.common.network.NetworkMessages;
import net.managerhub.center.common.network.NetworkReloadStatus;
import net.managerhub.center.common.network.ReloadMessage;
import net.managerhub.center.common.network.ReloadOrigin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The proxy half of the network wide reload over plugin messages.
 *
 * <p>Everything here is the case an administrator really runs into: a request
 * arrives from one Paper server, the proxy reloads itself once, hands the request
 * on to the other servers, and says honestly which of them it could not reach.</p>
 */
class ProxyNetworkReloadTest {

    private static final ChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.create(NetworkMessages.CHANNEL_NAMESPACE, NetworkMessages.CHANNEL_NAME);

    /** Stands for the Center2 plugin instance Velocity wants for a registration. */
    private static final Object PLUGIN = new Object();

    /** What the origin server received back. */
    private final AtomicReference<byte[]> answer = new AtomicReference<>();

    /** What every backend server received, in order. */
    private final Map<String, List<byte[]>> delivered = new LinkedHashMap<>();

    /** What the proxy scheduled, so the test can run it when it wants to. */
    private final List<Runnable> scheduled = new ArrayList<>();

    /** How often the proxy reloaded its own Center2. */
    private final List<ReloadOrigin> reloads = new ArrayList<>();

    @Test
    @DisplayName("the proxy reloads itself once and tells every other server")
    void reloadReachesEveryServer() {
        final NetworkStatusService service = service(reachable("lobby"), reachable("survival"));
        service.onReload(origin -> {
            reloads.add(origin);
            return true;
        });

        service.onPluginMessage(reloadRequest("lobby", UUID.randomUUID()));

        assertEquals(List.of(ReloadOrigin.REMOTE_REQUEST), reloads,
                "the proxy reloads once, and never as a request of its own");
        assertEquals(1, deliveries("survival"), "the other server is told");
        assertEquals(0, deliveries("lobby"), "the server that asked already reloaded itself");
    }

    @Test
    @DisplayName("the same request twice does not reload the proxy a second time")
    void sameRequestIsIgnoredTheSecondTime() {
        final NetworkStatusService service = service(reachable("lobby"), reachable("survival"));
        service.onReload(origin -> {
            reloads.add(origin);
            return true;
        });
        final UUID request = UUID.randomUUID();

        service.onPluginMessage(reloadRequest("lobby", request));
        service.onPluginMessage(reloadRequest("lobby", request));
        service.onPluginMessage(reloadRequest("survival", request));

        assertEquals(1, reloads.size(), "one request id, one reload of the proxy");
        assertTrue(service.alreadyProcessed(request));
    }

    @Test
    @DisplayName("a server nobody is connected to gets the request as soon as somebody connects")
    void unreachableServerBecomesPending() {
        final NetworkStatusService service = service(reachable("lobby"), empty("citybuild"));
        service.onReload(origin -> true);
        final UUID request = UUID.randomUUID();

        service.onPluginMessage(reloadRequest("lobby", request));

        assertEquals(List.of("citybuild"), service.pendingServers(),
                "a plugin message needs a player, so this one has to wait");
        assertEquals(0, deliveries("citybuild"));
    }

    @Test
    @DisplayName("the proxy answers the origin with one state per server")
    void reportGoesBackToTheOrigin() {
        final NetworkStatusService service = service(reachable("lobby"), reachable("survival"), empty("citybuild"));
        service.onReload(origin -> true);
        final UUID request = UUID.randomUUID();

        service.onPluginMessage(reloadRequest("lobby", request));
        // The report is scheduled, so the servers get a moment to answer first.
        runScheduled();

        final NetworkMessages.ReloadReport report =
                NetworkMessages.readReloadReport(answer.get()).orElseThrow();
        assertEquals(request, report.requestId());
        assertEquals(NetworkReloadStatus.SUCCESS, report.results().get(NetworkStatusService.PROXY_LABEL));
        assertEquals(NetworkReloadStatus.PENDING, report.results().get("survival"),
                "a server that was told but did not answer is still open");
        assertEquals(NetworkReloadStatus.PENDING, report.results().get("citybuild"));
    }

    @Test
    @DisplayName("a server that answers is reported with what it really did")
    void answeredServerIsReported() {
        final NetworkStatusService service = service(reachable("lobby"), reachable("survival"));
        service.onReload(origin -> true);
        final UUID request = UUID.randomUUID();
        service.onPluginMessage(reloadRequest("lobby", request));

        service.onPluginMessage(new PluginMessageEvent(connection("survival"), sink(), CHANNEL,
                NetworkMessages.reloadResult(request, NetworkReloadStatus.SUCCESS)));
        runScheduled();

        final NetworkMessages.ReloadReport report =
                NetworkMessages.readReloadReport(answer.get()).orElseThrow();
        assertEquals(NetworkReloadStatus.SUCCESS, report.results().get("survival"));
    }

    @Test
    @DisplayName("a failed reload on a Paper server is reported as failed, not as done")
    void failedServerIsReported() {
        final NetworkStatusService service = service(reachable("lobby"), reachable("survival"));
        service.onReload(origin -> true);
        final UUID request = UUID.randomUUID();
        service.onPluginMessage(reloadRequest("lobby", request));

        service.onPluginMessage(new PluginMessageEvent(connection("survival"), sink(), CHANNEL,
                NetworkMessages.reloadResult(request, NetworkReloadStatus.FAILED)));
        runScheduled();

        assertEquals(NetworkReloadStatus.FAILED,
                NetworkMessages.readReloadReport(answer.get()).orElseThrow().results().get("survival"));
    }

    @Test
    @DisplayName("a failed reload of the proxy itself is reported as failed")
    void failedProxyIsReported() {
        final NetworkStatusService service = service(reachable("lobby"));
        service.onReload(origin -> false);

        service.onPluginMessage(reloadRequest("lobby", UUID.randomUUID()));
        runScheduled();

        assertEquals(NetworkReloadStatus.FAILED,
                NetworkMessages.readReloadReport(answer.get()).orElseThrow()
                        .results().get(NetworkStatusService.PROXY_LABEL));
    }

    @Test
    @DisplayName("a request that already ran out of time is refused, not carried out")
    void expiredRequestIsRefused() {
        final NetworkStatusService service = service(reachable("lobby"), reachable("survival"));
        service.onReload(origin -> {
            reloads.add(origin);
            return true;
        });
        final ReloadMessage expired = new ReloadMessage(UUID.randomUUID(), "lobby",
                System.currentTimeMillis() - 1);

        service.onPluginMessage(new PluginMessageEvent(connection("lobby"), sink(), CHANNEL,
                NetworkMessages.reload(NetworkMessages.RELOAD_REQUEST, expired)));

        assertTrue(reloads.isEmpty());
        assertEquals(NetworkReloadStatus.EXPIRED,
                NetworkMessages.readReloadReport(answer.get()).orElseThrow()
                        .results().get(NetworkStatusService.PROXY_LABEL));
    }

    @Test
    @DisplayName("a server that reported a heartbeat counts as verified without a player")
    void heartbeatVerifiesAServer() {
        final NetworkStatusService service = service(reachable("survival"));
        service.remoteNodes(() -> List.of("survival"));

        service.onPluginMessage(new PluginMessageEvent(connection("survival"), sink(), CHANNEL,
                NetworkMessages.simple(NetworkMessages.REQUEST)));

        assertEquals(net.managerhub.center.common.network.ServerStatus.CONNECTED,
                NetworkMessages.readStatus(answer.get()).get("survival"));
    }

    @Test
    @DisplayName("without a heartbeat and without a hello a server stays unknown")
    void withoutHeartbeatTheServerStaysUnknown() {
        final NetworkStatusService service = service(reachable("survival"));
        service.remoteNodes(List::of);

        service.onPluginMessage(new PluginMessageEvent(connection("survival"), sink(), CHANNEL,
                NetworkMessages.simple(NetworkMessages.REQUEST)));

        assertEquals(net.managerhub.center.common.network.ServerStatus.UNKNOWN,
                NetworkMessages.readStatus(answer.get()).get("survival"));
        assertFalse(service.pendingServers().contains("survival"));
    }

    private void runScheduled() {
        final List<Runnable> tasks = List.copyOf(scheduled);
        scheduled.clear();
        tasks.forEach(Runnable::run);
    }

    private int deliveries(final String server) {
        return delivered.getOrDefault(server, List.of()).size();
    }

    private PluginMessageEvent reloadRequest(final String origin, final UUID request) {
        final ReloadMessage message =
                new ReloadMessage(request, origin, System.currentTimeMillis() + 60_000L);
        return new PluginMessageEvent(connection(origin), sink(), CHANNEL,
                NetworkMessages.reload(NetworkMessages.RELOAD_REQUEST, message));
    }

    private NetworkStatusService service(final RegisteredServer... servers) {
        return new NetworkStatusService(proxy(servers), PLUGIN);
    }

    /** The connection of one backend server; what it receives is remembered. */
    private ServerConnection connection(final String server) {
        final ServerInfo info = serverInfo(server);
        final RegisteredServer registered = registered(server);
        return (ServerConnection) stub(ServerConnection.class, (method, arguments) -> switch (method.getName()) {
            case "getServerInfo" -> info;
            case "getServer" -> registered;
            case "sendPluginMessage" -> {
                answer.set((byte[]) arguments[1]);
                yield true;
            }
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private RegisteredServer registered(final String name) {
        return server(name, true);
    }

    private RegisteredServer reachable(final String name) {
        return server(name, true);
    }

    /** A server nobody is connected to: a plugin message cannot travel there. */
    private RegisteredServer empty(final String name) {
        return server(name, false);
    }

    private RegisteredServer server(final String name, final boolean hasPlayers) {
        final ServerInfo info = serverInfo(name);
        return (RegisteredServer) stub(RegisteredServer.class, (method, arguments) -> switch (method.getName()) {
            case "getServerInfo" -> info;
            case "ping" -> java.util.concurrent.CompletableFuture.completedFuture(null);
            case "sendPluginMessage" -> {
                if (!hasPlayers) {
                    yield false;
                }
                delivered.computeIfAbsent(name, key -> new ArrayList<>()).add((byte[]) arguments[1]);
                yield true;
            }
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private ProxyServer proxy(final RegisteredServer... servers) {
        final List<RegisteredServer> all = List.of(servers);
        final Scheduler scheduler = scheduler();
        return (ProxyServer) stub(ProxyServer.class, (method, arguments) -> switch (method.getName()) {
            case "getAllServers" -> all;
            case "getScheduler" -> scheduler;
            case "getServer" -> all.stream()
                    .filter(server -> server.getServerInfo().getName().equals(arguments[0]))
                    .findFirst();
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    /** A scheduler that only collects the task, so the test decides when it runs. */
    private Scheduler scheduler() {
        return (Scheduler) stub(Scheduler.class, (method, arguments) -> {
            if (!"buildTask".equals(method.getName())) {
                throw new UnsupportedOperationException(method.getName());
            }
            final Runnable task = (Runnable) arguments[1];
            // The builder of Velocity returns itself, so the stub has to as well.
            final AtomicReference<Object> builder = new AtomicReference<>();
            builder.set(stub(Scheduler.TaskBuilder.class, (builderMethod, builderArguments) ->
                    switch (builderMethod.getName()) {
                        case "delay", "repeat", "clearDelay", "clearRepeat" -> builder.get();
                        case "schedule" -> {
                            scheduled.add(task);
                            yield null;
                        }
                        default -> throw new UnsupportedOperationException(builderMethod.getName());
                    }));
            return builder.get();
        });
    }

    private static ServerInfo serverInfo(final String name) {
        return new ServerInfo(name, new InetSocketAddress("127.0.0.1", 25566));
    }

    private static ChannelMessageSink sink() {
        return (ChannelMessageSink) stub(ChannelMessageSink.class,
                (method, arguments) -> { throw new UnsupportedOperationException(method.getName()); });
    }

    /** Answers only the few methods the service really uses. */
    private static Object stub(final Class<?> type, final Answer answer) {
        return Proxy.newProxyInstance(
                ProxyNetworkReloadTest.class.getClassLoader(),
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
