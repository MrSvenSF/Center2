package net.managerhub.center.velocity.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.ChannelMessageSink;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import net.managerhub.center.common.network.NetworkMessages;
import net.managerhub.center.common.network.ServerStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NetworkStatusServiceTest {

    private static final ChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.create(NetworkMessages.CHANNEL_NAMESPACE, NetworkMessages.CHANNEL_NAME);

    /** Stands for the Center2 plugin instance Velocity wants for a registration. */
    private static final Object PLUGIN = new Object();

    private final AtomicReference<byte[]> answer = new AtomicReference<>();

    @Test
    @DisplayName("a server that never said hello stays unknown instead of counting as missing")
    void unverifiedServerIsUnknown() {
        final NetworkStatusService service = new NetworkStatusService(proxy(reachable("lobby")), PLUGIN);

        service.onPluginMessage(message("lobby", NetworkMessages.REQUEST));

        assertEquals(Map.of("lobby", ServerStatus.UNKNOWN), received());
    }

    @Test
    @DisplayName("a server that said hello and answers is connected")
    void verifiedServerIsConnected() {
        final NetworkStatusService service = new NetworkStatusService(proxy(reachable("lobby")), PLUGIN);

        service.onPluginMessage(message("lobby", NetworkMessages.HELLO));

        assertEquals(Map.of("lobby", ServerStatus.CONNECTED), received());
    }

    @Test
    @DisplayName("a server that does not answer is unreachable, even after a hello")
    void silentServerIsUnreachable() {
        final NetworkStatusService service = new NetworkStatusService(proxy(unreachable("lobby")), PLUGIN);

        service.onPluginMessage(message("lobby", NetworkMessages.HELLO));

        assertEquals(Map.of("lobby", ServerStatus.UNREACHABLE), received());
    }

    @Test
    @DisplayName("every server of the proxy is reported, in configuration order")
    void reportsEveryServer() {
        final NetworkStatusService service =
                new NetworkStatusService(proxy(reachable("lobby"), unreachable("survival"), reachable("citybuild")), PLUGIN);

        service.onPluginMessage(message("lobby", NetworkMessages.HELLO));

        final Map<String, ServerStatus> status = received();
        assertEquals(List.of("lobby", "survival", "citybuild"), List.copyOf(status.keySet()));
        assertEquals(ServerStatus.CONNECTED, status.get("lobby"));
        assertEquals(ServerStatus.UNREACHABLE, status.get("survival"));
        assertEquals(ServerStatus.UNKNOWN, status.get("citybuild"));
    }

    @Test
    @DisplayName("a Center2 message never reaches a client")
    void ownMessageIsNotForwarded() {
        final NetworkStatusService service = new NetworkStatusService(proxy(reachable("lobby")), PLUGIN);
        final PluginMessageEvent event = message("lobby", NetworkMessages.HELLO);

        service.onPluginMessage(event);

        assertSame(PluginMessageEvent.ForwardResult.handled(), event.getResult());
    }

    @Test
    @DisplayName("a message of another plugin is left alone")
    void foreignChannelIsIgnored() {
        final NetworkStatusService service = new NetworkStatusService(proxy(reachable("lobby")), PLUGIN);
        final ChannelIdentifier foreign = MinecraftChannelIdentifier.create("other", "channel");
        final PluginMessageEvent event = new PluginMessageEvent(
                connection("lobby"), sink(), foreign, NetworkMessages.simple(NetworkMessages.HELLO));

        service.onPluginMessage(event);

        assertSame(PluginMessageEvent.ForwardResult.forward(), event.getResult());
        assertEquals(null, answer.get());
    }

    private Map<String, ServerStatus> received() {
        final byte[] message = answer.get();
        assertNotNull(message, "the proxy did not answer");
        assertEquals(NetworkMessages.STATUS, NetworkMessages.typeOf(message));
        return NetworkMessages.readStatus(message);
    }

    private PluginMessageEvent message(final String server, final String type) {
        return new PluginMessageEvent(connection(server), sink(), CHANNEL, NetworkMessages.simple(type));
    }

    private ServerConnection connection(final String server) {
        final ServerInfo info = serverInfo(server);
        return (ServerConnection) stub(ServerConnection.class, (method, arguments) -> switch (method.getName()) {
            case "getServerInfo" -> info;
            case "sendPluginMessage" -> {
                answer.set((byte[]) arguments[1]);
                yield true;
            }
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private static ProxyServer proxy(final RegisteredServer... servers) {
        final List<RegisteredServer> all = List.of(servers);
        return (ProxyServer) stub(ProxyServer.class, (method, arguments) -> switch (method.getName()) {
            case "getAllServers" -> all;
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private static RegisteredServer reachable(final String name) {
        return server(name, CompletableFuture.completedFuture(null));
    }

    private static RegisteredServer unreachable(final String name) {
        return server(name, CompletableFuture.failedFuture(new IOException("no route")));
    }

    private static RegisteredServer server(final String name, final CompletableFuture<?> ping) {
        final ServerInfo info = serverInfo(name);
        return (RegisteredServer) stub(RegisteredServer.class, (method, arguments) -> switch (method.getName()) {
            case "getServerInfo" -> info;
            case "ping" -> ping;
            default -> throw new UnsupportedOperationException(method.getName());
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
                NetworkStatusServiceTest.class.getClassLoader(),
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
        Object answer(java.lang.reflect.Method method, Object[] arguments);
    }
}
