package net.managerhub.center.velocity.network;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.managerhub.center.common.network.NetworkMessages;
import net.managerhub.center.common.network.NetworkReloadStatus;
import net.managerhub.center.common.network.PendingReloads;
import net.managerhub.center.common.network.ProcessedRequests;
import net.managerhub.center.common.network.ReloadMessage;
import net.managerhub.center.common.network.ReloadOrigin;
import net.managerhub.center.common.network.ServerStatus;
import net.managerhub.center.api.ModulePlatform;
import net.managerhub.center.common.remote.ModuleActionFallback;
import net.managerhub.center.common.remote.RemoteAction;
import net.managerhub.center.common.remote.RemoteException;
import net.managerhub.center.common.remote.RemoteService;

/**
 * The Velocity side of the small MHCenter2 network protocol.
 *
 * <p>The proxy already knows every Paper server of the network, so no server is
 * written into a MHCenter2 configuration file. A server counts as verified once a
 * MHCenter2 Paper instance has announced itself from there, or once that server
 * reported a heartbeat in the optional remote database; whether it answers right
 * now is asked with the normal server ping of Velocity.</p>
 *
 * <p>The same channel carries the network wide reload. A Paper server asks the
 * proxy, the proxy reloads its own MHCenter2 exactly once and hands the request on
 * to every other backend server. A server nobody is connected to cannot be
 * reached by a plugin message at all - that request is kept as
 * {@link NetworkReloadStatus#PENDING} and is delivered as soon as somebody
 * connects there, and it is dropped when its lifetime ran out.</p>
 *
 * <p>Everything is kept in memory. A server that was never verified stays
 * {@link ServerStatus#UNKNOWN} - that is not the same as "MHCenter2 is missing".</p>
 */
public final class NetworkStatusService implements ModuleActionFallback {

    /** Seconds a server ping may take before the server counts as unreachable. */
    private static final long PING_TIMEOUT_SECONDS = 3L;

    /** How long the proxy waits for the answers of the servers before it reports. */
    private static final long REPORT_WINDOW_MILLIS = 2500L;

    /** Name the report uses for the proxy itself. */
    public static final String PROXY_LABEL = "proxy";

    private static final ChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.create(NetworkMessages.CHANNEL_NAMESPACE, NetworkMessages.CHANNEL_NAME);

    /** What the proxy does with a reload request that arrived from a server. */
    @FunctionalInterface
    public interface LocalReload {

        /**
         * @param origin why the reload happens
         * @return {@code true} if the proxy really reloaded its own MHCenter2
         */
        boolean run(ReloadOrigin origin);
    }

    /** One reload request the proxy is still collecting answers for. */
    private record Running(UUID requestId,
                           String origin,
                           Map<String, NetworkReloadStatus> results) {
    }

    private final ProxyServer proxy;
    private final Object plugin;
    private final RemoteService remote;
    private final Set<String> verified = ConcurrentHashMap.newKeySet();
    private final ProcessedRequests processed;
    private final ProcessedRequests processedModuleActions;
    private final PendingReloads pending = new PendingReloads();
    private final Map<UUID, Running> running = new ConcurrentHashMap<>();
    private final Map<String, List<RemoteAction>> pendingModuleActions = new ConcurrentHashMap<>();

    private LocalReload localReload = origin -> false;
    private Supplier<List<String>> remoteNodes = List::of;

    /**
     * @param proxy  the running proxy
     * @param plugin the MHCenter2 plugin instance, needed to schedule the report
     */
    public NetworkStatusService(final ProxyServer proxy, final Object plugin, final RemoteService remote) {
        this.proxy = proxy;
        this.plugin = plugin;
        this.remote = remote;
        this.processed = new ProcessedRequests(Duration.ofMinutes(10).toMillis());
        this.processedModuleActions = new ProcessedRequests(Duration.ofMinutes(10).toMillis());
    }

    /** Constructor kept for status/reload-only integrations and tests. */
    public NetworkStatusService(final ProxyServer proxy, final Object plugin) {
        this(proxy, plugin, null);
    }

    /**
     * @param localReload what the proxy does when it has to reload its own MHCenter2
     */
    public void onReload(final LocalReload localReload) {
        this.localReload = localReload;
    }

    /**
     * Connects the server status with the optional remote database.
     *
     * <p>A heartbeat is the only way to see a MHCenter2 server that has no player
     * on it. The id a node reports is its {@code remote.server-id}, so that id
     * should be the name the server has in {@code velocity.toml} - then the
     * status uses it, otherwise the entry simply does not match and nothing
     * breaks.</p>
     *
     * @param remoteNodes the ids of the nodes that reported recently
     */
    public void remoteNodes(final Supplier<List<String>> remoteNodes) {
        this.remoteNodes = remoteNodes;
    }

    /** Registers the plugin message channel on the proxy. */
    public void register() {
        proxy.getChannelRegistrar().register(CHANNEL);
    }

    /** Removes the plugin message channel from the proxy. */
    public void unregister() {
        proxy.getChannelRegistrar().unregister(CHANNEL);
    }

    @Subscribe
    public void onPluginMessage(final PluginMessageEvent event) {
        if (!CHANNEL.getId().equals(event.getIdentifier().getId())) {
            return;
        }
        // The message belongs to MHCenter2 and must never reach a client.
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (!(event.getSource() instanceof ServerConnection connection)) {
            return;
        }

        final String type = NetworkMessages.typeOf(event.getData());
        if (NetworkMessages.HELLO.equals(type) || NetworkMessages.REQUEST.equals(type)) {
            if (NetworkMessages.HELLO.equals(type)) {
                verified.add(connection.getServerInfo().getName());
            }
            snapshot().thenAccept(status -> connection.sendPluginMessage(CHANNEL, NetworkMessages.status(status)));
            return;
        }
        if (NetworkMessages.RELOAD_REQUEST.equals(type)) {
            NetworkMessages.readReload(NetworkMessages.RELOAD_REQUEST, event.getData())
                    .ifPresent(request -> onReloadRequest(connection, request));
            return;
        }
        if (NetworkMessages.RELOAD_RESULT.equals(type)) {
            NetworkMessages.readReloadResult(event.getData())
                    .ifPresent(result -> onReloadResult(connection.getServerInfo().getName(), result));
            return;
        }
        if (NetworkMessages.MODULE_ACTION_REQUEST.equals(type)) {
            NetworkMessages.readModuleAction(NetworkMessages.MODULE_ACTION_REQUEST, event.getData())
                    .ifPresent(action -> onModuleAction(connection, action));
        }
    }

    private void onModuleAction(final ServerConnection connection, final RemoteAction received) {
        final long now = System.currentTimeMillis();
        if (received.expired(now) || !processedModuleActions.claim(received.id(), now)) {
            return;
        }
        final String origin = connection.getServerInfo().getName();
        final RemoteAction action = new RemoteAction(received.id(), received.namespace(), received.type(), origin,
                received.target(), received.createdAtMillis(), received.expiresAtMillis(), received.payload());
        routeModuleAction(action);
    }

    private int routeModuleAction(final RemoteAction action) {
        int deliveries = 0;
        if (action.addresses(serverId(), ModulePlatform.VELOCITY)) {
            if (remote == null) {
                return deliveries;
            }
            deliveries++;
            proxy.getScheduler().buildTask(plugin, () -> {
                try {
                    remote.deliverModuleAction(action);
                } catch (final Exception ignored) {
                    // The module owns its failure; the proxy event thread must remain healthy.
                }
            }).schedule();
        }
        final byte[] message = NetworkMessages.moduleAction(NetworkMessages.MODULE_ACTION_EXECUTE, action);
        for (final RegisteredServer server : proxy.getAllServers()) {
            final String name = server.getServerInfo().getName();
            if (action.addresses(name, ModulePlatform.PAPER)) {
                if (!server.sendPluginMessage(CHANNEL, message)) {
                    rememberModuleAction(name, action);
                }
                deliveries++;
            }
        }
        return deliveries;
    }

    @Override
    public boolean available() {
        return proxy.getPlayerCount() > 0;
    }

    @Override
    public String serverId() {
        return remote == null || remote.serverId().isEmpty() ? PROXY_LABEL : remote.serverId();
    }

    @Override
    public List<String> onlineNodes() {
        final List<String> nodes = new ArrayList<>();
        nodes.add(PROXY_LABEL);
        proxy.getAllServers().forEach(server -> nodes.add(server.getServerInfo().getName()));
        return List.copyOf(nodes);
    }

    @Override
    public void send(final RemoteAction action) throws RemoteException {
        if (!available()) {
            throw new RemoteException("No player is online to carry the module action to a Paper server.");
        }
        if (!processedModuleActions.claim(action.id(), System.currentTimeMillis()) || routeModuleAction(action) == 0) {
            throw new RemoteException("No addressed MHCenter2 node could receive the module action.");
        }
    }

    private void rememberModuleAction(final String server, final RemoteAction action) {
        pendingModuleActions.compute(server.toLowerCase(Locale.ROOT), (ignored, existing) -> {
            final List<RemoteAction> actions = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
            actions.removeIf(old -> old.expired(System.currentTimeMillis()));
            if (actions.size() >= 256) {
                actions.removeFirst();
            }
            actions.add(action);
            return List.copyOf(actions);
        });
    }

    private List<RemoteAction> takePendingModuleActions(final String server, final long now) {
        final List<RemoteAction> actions = pendingModuleActions.remove(server.toLowerCase(Locale.ROOT));
        if (actions == null) {
            return List.of();
        }
        return actions.stream().filter(action -> !action.expired(now)).toList();
    }

    /**
     * Delivers a waiting reload request as soon as somebody connects to that
     * server.
     *
     * <p>This is what makes {@link NetworkReloadStatus#PENDING} more than a
     * label: a server that could not be reached when the administrator asked is
     * still told, the first moment a plugin message can travel there.</p>
     */
    @Subscribe
    public void onServerPostConnect(final ServerPostConnectEvent event) {
        final Optional<ServerConnection> connection = event.getPlayer().getCurrentServer();
        if (connection.isEmpty()) {
            return;
        }
        final String server = connection.get().getServerInfo().getName();
        pending.take(server, System.currentTimeMillis()).ifPresent(delivery -> {
            final ReloadMessage order = new ReloadMessage(
                    delivery.requestId(), delivery.origin(), delivery.expiresAtMillis());
            connection.get().getServer()
                    .sendPluginMessage(CHANNEL, NetworkMessages.reload(NetworkMessages.RELOAD_EXECUTE, order));
        });
        takePendingModuleActions(server, System.currentTimeMillis()).forEach(action ->
                connection.get().getServer().sendPluginMessage(CHANNEL,
                        NetworkMessages.moduleAction(NetworkMessages.MODULE_ACTION_EXECUTE, action)));
    }

    /**
     * Answers one reload request of a backend server.
     *
     * <p>The request is never sent back to where it came from and it is never
     * handed on a second time: the id is claimed once here, so the proxy cannot
     * become the middle of an endless circle between two Paper servers.</p>
     */
    private void onReloadRequest(final ServerConnection connection, final ReloadMessage request) {
        final String origin = connection.getServerInfo().getName();
        final long now = System.currentTimeMillis();
        if (!request.valid(now)) {
            connection.sendPluginMessage(CHANNEL, NetworkMessages.reloadReport(request.requestId(),
                    Map.of(PROXY_LABEL, NetworkReloadStatus.EXPIRED)));
            return;
        }
        if (!processed.claim(request.requestId(), now)) {
            return;
        }

        final Map<String, NetworkReloadStatus> results = new LinkedHashMap<>();
        results.put(PROXY_LABEL, localReload.run(ReloadOrigin.REMOTE_REQUEST)
                ? NetworkReloadStatus.SUCCESS : NetworkReloadStatus.FAILED);

        final ReloadMessage order = new ReloadMessage(request.requestId(), origin, request.expiresAtMillis());
        final byte[] message = NetworkMessages.reload(NetworkMessages.RELOAD_EXECUTE, order);
        for (final RegisteredServer server : proxy.getAllServers()) {
            final String name = server.getServerInfo().getName();
            if (name.equals(origin)) {
                // The origin already reloaded itself before it asked.
                continue;
            }
            if (server.sendPluginMessage(CHANNEL, message)) {
                // Delivered. Whether MHCenter2 there really reloaded is only known
                // when that server answers, so it stays open until then.
                results.put(name, NetworkReloadStatus.PENDING);
                continue;
            }
            // Nobody is connected to that server, so a plugin message cannot
            // reach it right now. It is told as soon as somebody connects.
            pending.remember(name, request.requestId(), origin, request.expiresAtMillis());
            results.put(name, NetworkReloadStatus.PENDING);
        }

        running.put(request.requestId(), new Running(request.requestId(), origin, results));
        proxy.getScheduler().buildTask(plugin, () -> report(request.requestId(), connection))
                .delay(REPORT_WINDOW_MILLIS, TimeUnit.MILLISECONDS)
                .schedule();
    }

    /** Remembers what one backend server answered. */
    private void onReloadResult(final String server, final NetworkMessages.ReloadResult result) {
        final Running open = running.get(result.requestId());
        if (open == null) {
            return;
        }
        open.results().put(server, result.status());
    }

    /** Sends the collected answers back to the server the request came from. */
    private void report(final UUID requestId, final ServerConnection origin) {
        final Running open = running.remove(requestId);
        if (open == null) {
            return;
        }
        origin.sendPluginMessage(CHANNEL, NetworkMessages.reloadReport(requestId, open.results()));
        pending.dropExpired(System.currentTimeMillis());
    }

    /** @return the state of every server the proxy knows, in configuration order. */
    private CompletableFuture<Map<String, ServerStatus>> snapshot() {
        final Map<String, ServerStatus> states = new ConcurrentHashMap<>();
        final List<RegisteredServer> servers = new ArrayList<>(proxy.getAllServers());
        final List<CompletableFuture<?>> pings = new ArrayList<>(servers.size());

        for (final RegisteredServer server : servers) {
            final String name = server.getServerInfo().getName();
            pings.add(server.ping()
                    .orTimeout(PING_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .handle((ping, failure) -> states.put(name, state(name, failure == null))));
        }

        return CompletableFuture.allOf(pings.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> ordered(servers, states));
    }

    private ServerStatus state(final String name, final boolean reachable) {
        if (!reachable) {
            return ServerStatus.UNREACHABLE;
        }
        return verified.contains(name) || heartbeatSeen(name) ? ServerStatus.CONNECTED : ServerStatus.UNKNOWN;
    }

    /**
     * @return {@code true} if a node of that name reported a heartbeat in the
     *         remote database; that is the only way to confirm MHCenter2 on a server
     *         nobody is connected to
     */
    private boolean heartbeatSeen(final String name) {
        final String wanted = name.toLowerCase(Locale.ROOT);
        return remoteNodes.get().stream().anyMatch(node -> node.equalsIgnoreCase(wanted));
    }

    private static Map<String, ServerStatus> ordered(final List<RegisteredServer> servers,
                                                     final Map<String, ServerStatus> states) {
        final Map<String, ServerStatus> result = new LinkedHashMap<>();
        for (final RegisteredServer server : servers) {
            final String name = server.getServerInfo().getName();
            result.put(name, states.getOrDefault(name, ServerStatus.UNKNOWN));
        }
        return result;
    }

    /** @return the reload requests that are waiting for a way to reach their server. */
    public List<String> pendingServers() {
        return pending.servers(System.currentTimeMillis());
    }

    /**
     * @param requestId id of a request
     * @return {@code true} if the proxy already answered that request
     */
    public boolean alreadyProcessed(final UUID requestId) {
        return processed.known(requestId, System.currentTimeMillis());
    }
}
