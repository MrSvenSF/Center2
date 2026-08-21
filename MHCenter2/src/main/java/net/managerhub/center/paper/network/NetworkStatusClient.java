package net.managerhub.center.paper.network;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import net.managerhub.center.common.network.NetworkMessages;
import net.managerhub.center.common.network.NetworkReloadStatus;
import net.managerhub.center.common.network.ProcessedRequests;
import net.managerhub.center.common.network.ReloadMessage;
import net.managerhub.center.common.network.ServerStatus;
import net.managerhub.center.common.remote.ModuleActionFallback;
import net.managerhub.center.common.remote.RemoteAction;
import net.managerhub.center.common.remote.RemoteException;
import net.managerhub.center.common.remote.RemoteService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

/**
 * The Paper side of the small MHCenter2 network protocol.
 *
 * <p>A plugin message needs a player to travel through, so MHCenter2 announces
 * itself to the proxy as soon as a player has joined. The proxy answers with the
 * state of every server it knows, and that answer is kept in memory only - the
 * network status is never written into the database.</p>
 *
 * <p>The same way carries the network wide reload: this server asks the proxy to
 * reload the whole network, the proxy passes the request on, and this server
 * carries out a request that arrives from the proxy. Because the message needs a
 * player, {@link #sendReloadRequest} says honestly whether it could be sent at
 * all - a server nobody is connected to cannot reach the proxy this way, and the
 * optional remote database exists exactly for that case.</p>
 *
 * <p>Without a proxy nothing breaks: the message simply goes nowhere and the
 * server status stays unknown.</p>
 */
public final class NetworkStatusClient implements Listener, PluginMessageListener, ModuleActionFallback {

    /** Ticks MHCenter2 waits after a join before it announces itself. */
    private static final long ANNOUNCE_DELAY_TICKS = 20L;

    private final Plugin plugin;
    private final RemoteService remote;
    private final ProcessedRequests processedModuleActions =
            new ProcessedRequests(Duration.ofMinutes(10).toMillis());

    private volatile Map<String, ServerStatus> servers = Map.of();
    private volatile boolean proxyAnswered;
    private Runnable onUpdate = () -> { };
    private Consumer<ReloadMessage> onReloadOrder = order -> { };
    private Consumer<NetworkMessages.ReloadReport> onReloadReport = report -> { };

    public NetworkStatusClient(final Plugin plugin, final RemoteService remote) {
        this.plugin = plugin;
        this.remote = remote;
    }

    /**
     * @param onUpdate called on the main thread after a new answer arrived
     */
    public void onUpdate(final Runnable onUpdate) {
        this.onUpdate = onUpdate;
    }

    /**
     * @param onReloadOrder called on the main thread when the proxy tells this
     *                      server to reload its own MHCenter2
     */
    public void onReloadOrder(final Consumer<ReloadMessage> onReloadOrder) {
        this.onReloadOrder = onReloadOrder;
    }

    /**
     * @param onReloadReport called on the main thread when the proxy reports what
     *                       happened with a reload request of this server
     */
    public void onReloadReport(final Consumer<NetworkMessages.ReloadReport> onReloadReport) {
        this.onReloadReport = onReloadReport;
    }

    /** Registers the channel and the join listener. */
    public void register() {
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, NetworkMessages.CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, NetworkMessages.CHANNEL, this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /** @return the servers the proxy reported, in the order the proxy sent them. */
    public Map<String, ServerStatus> servers() {
        return servers;
    }

    /** @return {@code true} once the proxy has answered at least one time. */
    public boolean proxyAnswered() {
        return proxyAnswered;
    }

    /**
     * Asks the proxy for the current network status.
     *
     * @param player player the message travels through
     */
    public void requestUpdate(final Player player) {
        send(player, NetworkMessages.simple(NetworkMessages.REQUEST));
    }

    /**
     * Asks the proxy to reload the whole MHCenter2 network.
     *
     * <p>The message needs a player of this server to travel through. If nobody
     * is online here, the proxy cannot be reached this way at all - and that is
     * reported instead of being hidden.</p>
     *
     * @param request the reload request
     * @return {@code true} if the request really left this server
     */
    public boolean sendReloadRequest(final ReloadMessage request) {
        final Optional<? extends Player> carrier = plugin.getServer().getOnlinePlayers().stream().findFirst();
        if (carrier.isEmpty()) {
            return false;
        }
        send(carrier.get(), NetworkMessages.reload(NetworkMessages.RELOAD_REQUEST, request));
        return true;
    }

    /**
     * Tells the proxy how the reload of this server ended.
     *
     * <p>This is what turns a "the message was delivered" into a real answer.
     * Without it the proxy could only say that it tried.</p>
     *
     * @param requestId id of the request
     * @param status    what happened on this server
     */
    public void sendReloadResult(final UUID requestId, final NetworkReloadStatus status) {
        plugin.getServer().getOnlinePlayers().stream().findFirst()
                .ifPresent(carrier -> send(carrier, NetworkMessages.reloadResult(requestId, status)));
    }

    /** @return {@code true} if a player is online who could carry a plugin message. */
    public boolean canReachProxy() {
        return !plugin.getServer().getOnlinePlayers().isEmpty();
    }

    @EventHandler
    public void onPlayerJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                send(player, NetworkMessages.simple(NetworkMessages.HELLO));
            }
        }, ANNOUNCE_DELAY_TICKS);
    }

    @Override
    public void onPluginMessageReceived(final String channel, final Player player, final byte[] message) {
        if (!NetworkMessages.CHANNEL.equals(channel)) {
            return;
        }
        final String type = NetworkMessages.typeOf(message);
        if (NetworkMessages.STATUS.equals(type)) {
            servers = NetworkMessages.readStatus(message);
            proxyAnswered = true;
            onMainThread(onUpdate);
            return;
        }
        if (NetworkMessages.RELOAD_EXECUTE.equals(type)) {
            NetworkMessages.readReload(NetworkMessages.RELOAD_EXECUTE, message)
                    .ifPresent(order -> onMainThread(() -> onReloadOrder.accept(order)));
            return;
        }
        if (NetworkMessages.RELOAD_REPORT.equals(type)) {
            NetworkMessages.readReloadReport(message)
                    .ifPresent(report -> onMainThread(() -> onReloadReport.accept(report)));
            return;
        }
        if (NetworkMessages.MODULE_ACTION_EXECUTE.equals(type)) {
            NetworkMessages.readModuleAction(NetworkMessages.MODULE_ACTION_EXECUTE, message)
                    .filter(action -> !action.expired(System.currentTimeMillis()))
                    .filter(action -> processedModuleActions.claim(action.id(), System.currentTimeMillis()))
                    .ifPresent(this::deliverModuleAction);
        }
    }

    @Override
    public boolean available() {
        return canReachProxy() && proxyAnswered;
    }

    @Override
    public String serverId() {
        return remote.serverId();
    }

    @Override
    public List<String> onlineNodes() {
        return List.copyOf(servers.keySet());
    }

    @Override
    public void send(final RemoteAction action) throws RemoteException {
        final Optional<? extends Player> carrier = plugin.getServer().getOnlinePlayers().stream().findFirst();
        if (carrier.isEmpty() || !proxyAnswered) {
            throw new RemoteException("Velocity is not reachable through a verified player-carried channel.");
        }
        send(carrier.get(), NetworkMessages.moduleAction(NetworkMessages.MODULE_ACTION_REQUEST, action));
    }

    private void deliverModuleAction(final RemoteAction action) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                remote.deliverModuleAction(action);
            } catch (final Exception failure) {
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                        "A module action received through Plugin Messaging failed.", failure);
            }
        });
    }

    private void onMainThread(final Runnable work) {
        if (Bukkit.isPrimaryThread()) {
            work.run();
        } else {
            plugin.getServer().getScheduler().runTask(plugin, work);
        }
    }

    private void send(final Player player, final byte[] message) {
        player.sendPluginMessage(plugin, NetworkMessages.CHANNEL, message);
    }
}
