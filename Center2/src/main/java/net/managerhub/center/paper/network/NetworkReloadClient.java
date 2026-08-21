package net.managerhub.center.paper.network;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import net.managerhub.center.Center;
import net.managerhub.center.common.language.Language;
import net.managerhub.center.common.language.MessageKey;
import net.managerhub.center.common.network.NetworkMessages;
import net.managerhub.center.common.network.NetworkReloadStatus;
import net.managerhub.center.common.network.ProcessedRequests;
import net.managerhub.center.common.network.ReloadMessage;
import net.managerhub.center.common.network.ReloadOrigin;
import net.managerhub.center.common.remote.RemoteAction;
import net.managerhub.center.common.remote.RemoteActionStatus;
import net.managerhub.center.common.remote.RemoteException;
import net.managerhub.center.common.remote.RemoteReceipt;
import net.managerhub.center.common.remote.RemoteService;
import net.managerhub.center.common.remote.RemoteWorker;
import net.managerhub.center.paper.text.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

/**
 * Turns {@code /center reload} on this server into a reload of the whole Center2
 * network.
 *
 * <p>There are two ways to reach the other nodes and they are used in this
 * order:</p>
 *
 * <ol>
 *   <li>the optional remote database, which also reaches a server nobody is
 *       connected to, and</li>
 *   <li>the plugin message through the proxy, which needs a player to travel
 *       through and is used when the remote database is switched off or not
 *       reachable.</li>
 * </ol>
 *
 * <p>Nothing is claimed that was not confirmed. Every node that answered is
 * reported with what it really did; a node that did not answer is reported as
 * {@link NetworkReloadStatus#PENDING} and a node nothing can reach right now as
 * {@link NetworkReloadStatus#UNREACHABLE}.</p>
 *
 * <p>A reload that arrived from the network is carried out here and is never sent
 * on again. Together with the request id, which every node claims exactly once,
 * that is what keeps two servers and a proxy from reloading each other
 * forever.</p>
 */
public final class NetworkReloadClient {

    /** What this server does when it has to reload its own Center2. */
    @FunctionalInterface
    public interface LocalReload {

        /**
         * @param origin why the reload happens
         * @param sender who should see the answer, may be {@code null} for a
         *               reload that came from the network
         * @return {@code true} if the reload really worked
         */
        boolean run(ReloadOrigin origin, CommandSender sender);
    }

    /** How long a request stays valid, in milliseconds. */
    private static final long REQUEST_LIFETIME_MILLIS = 60_000L;

    /** How long request ids are remembered. */
    private static final long MEMORY_MILLIS = 10L * 60L * 1000L;

    /** Shortest wait before the receipts of the remote database are read, in ticks. */
    private static final long MIN_REPORT_DELAY_TICKS = 40L;

    /** Longest wait before the receipts of the remote database are read, in ticks. */
    private static final long MAX_REPORT_DELAY_TICKS = 200L;

    private final Plugin plugin;
    private final NetworkStatusClient messaging;
    private final RemoteService remote;
    private final RemoteWorker worker;
    private final Supplier<Language> language;
    private final ProcessedRequests processed = new ProcessedRequests(MEMORY_MILLIS);

    private LocalReload localReload = (origin, sender) -> false;

    /**
     * @param plugin    the Center2 plugin
     * @param messaging the plugin message client of this server
     * @param remote    the optional remote system of this node
     * @param worker    the background thread of the remote system
     * @param language  the texts of the currently active configuration
     */
    public NetworkReloadClient(final Plugin plugin,
                               final NetworkStatusClient messaging,
                               final RemoteService remote,
                               final RemoteWorker worker,
                               final Supplier<Language> language) {
        this.plugin = plugin;
        this.messaging = messaging;
        this.remote = remote;
        this.worker = worker;
        this.language = language;
    }

    /**
     * Connects this client with the reload of the local configuration.
     *
     * @param localReload what reloads Center2 on this server
     */
    public void onLocalReload(final LocalReload localReload) {
        this.localReload = localReload;
    }

    /** Registers the two incoming plugin messages this client answers. */
    public void register() {
        messaging.onReloadOrder(this::onOrder);
        messaging.onReloadReport(this::onReport);
    }

    /**
     * Hands the reload of this administrator on to the rest of the network.
     *
     * <p>Called after the local reload worked. A local reload that failed is
     * never spread: the administrator has to fix this server first, and the
     * other nodes are not helped by a request whose origin is broken.</p>
     *
     * @param sender who asked for the reload
     */
    public void spread(final CommandSender sender) {
        final Language texts = language.get();
        final UUID requestId = UUID.randomUUID();
        final long now = System.currentTimeMillis();
        // This node is done already. Claiming the id here is what makes an echo
        // through the proxy or through the database harmless.
        processed.claim(requestId, now);

        if (remote.available()) {
            sender.sendMessage(Text.of(texts.get(MessageKey.RELOAD_NETWORK_TRANSPORT_REMOTE)));
            publishRemote(sender, requestId);
            return;
        }
        if (remote.settings().enabled()) {
            // Switched on, but not usable right now. The administrator has to know
            // that the second way is being used, because it needs players.
            sender.sendMessage(Text.of(texts.get(MessageKey.RELOAD_NETWORK_REMOTE_DOWN)));
        }
        if (!messaging.canReachProxy()) {
            sender.sendMessage(Text.of(texts.get(MessageKey.RELOAD_NETWORK_NO_TRANSPORT,
                    "product", Center.PRODUCT_NAME)));
            return;
        }
        final ReloadMessage request = new ReloadMessage(requestId, remote.serverId(),
                now + REQUEST_LIFETIME_MILLIS);
        if (!messaging.sendReloadRequest(request)) {
            sender.sendMessage(Text.of(texts.get(MessageKey.RELOAD_NETWORK_NO_TRANSPORT,
                    "product", Center.PRODUCT_NAME)));
            return;
        }
        sender.sendMessage(Text.of(texts.get(MessageKey.RELOAD_NETWORK_TRANSPORT_MESSAGING)));
    }

    /**
     * Carries out a reload the proxy asked for.
     *
     * <p>Runs on the main thread. Nothing is sent on from here.</p>
     */
    private void onOrder(final ReloadMessage order) {
        final long now = System.currentTimeMillis();
        if (!order.valid(now) || !processed.claim(order.requestId(), now)) {
            return;
        }
        plugin.getLogger().info(language.get().get(MessageKey.RELOAD_NETWORK_RECEIVED,
                "origin", order.origin().isEmpty() ? "?" : order.origin()));
        final boolean success = localReload.run(ReloadOrigin.REMOTE_REQUEST, null);
        messaging.sendReloadResult(order.requestId(),
                success ? NetworkReloadStatus.SUCCESS : NetworkReloadStatus.FAILED);
    }

    /** Shows the administrator what the proxy reported. */
    private void onReport(final NetworkMessages.ReloadReport report) {
        // Nothing is stored per request here on purpose: the report is written
        // into the log, so it is visible no matter who asked and whether that
        // player is still online.
        final Language texts = language.get();
        final StringBuilder line = new StringBuilder();
        report.results().forEach((node, status) -> line.append(node).append('=').append(status).append(' '));
        plugin.getLogger().info(texts.get(MessageKey.RELOAD_NETWORK_LOG_RESULT,
                "product", Center.PRODUCT_NAME, "result", line.toString().trim()));
    }

    /**
     * Carries out a reload that arrived through the remote database.
     *
     * <p>Called on the remote thread. The reload itself has to happen on the main
     * server thread, so the work is handed over and this thread waits for it - the
     * main thread is never blocked by the database, only the other way round.</p>
     *
     * @param action the action of the core
     * @throws Exception if the reload failed, so the receipt says so
     */
    public void applyRemoteReload(final RemoteAction action) throws Exception {
        final long now = System.currentTimeMillis();
        if (!processed.claim(action.id(), now)) {
            return;
        }
        final java.util.concurrent.CompletableFuture<Boolean> done = new java.util.concurrent.CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                plugin.getLogger().info(language.get().get(MessageKey.RELOAD_NETWORK_RECEIVED,
                        "origin", action.originServerId().isEmpty() ? "?" : action.originServerId()));
                done.complete(localReload.run(ReloadOrigin.REMOTE_REQUEST, null));
            } catch (final Throwable broken) {
                done.completeExceptionally(broken);
            }
        });
        if (!done.get(30L, java.util.concurrent.TimeUnit.SECONDS)) {
            throw new IllegalStateException("The local reload of " + Center.PRODUCT_NAME + " failed.");
        }
    }

    /** Writes the action and reports what the other nodes did with it. */
    private void publishRemote(final CommandSender sender, final UUID requestId) {
        worker.submit("reload-publish", () -> {
            try {
                remote.publishReload(requestId);
            } catch (final RemoteException failure) {
                reply(sender, texts -> texts.get(MessageKey.RELOAD_NETWORK_FAILED,
                        "reason", Text.escape(String.valueOf(failure.getMessage()))));
                return;
            }
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> worker.submit("reload-report", () -> collect(sender, requestId)), reportDelayTicks());
        });
    }

    /** Reads the receipts and answers with one line per node. */
    private void collect(final CommandSender sender, final UUID requestId) {
        final Map<String, NetworkReloadStatus> results = new LinkedHashMap<>();
        final List<String> nodes = remote.onlineNodes();
        try {
            for (final RemoteReceipt receipt : remote.receipts(requestId)) {
                results.put(receipt.serverId(), translate(receipt.status()));
            }
        } catch (final RemoteException failure) {
            reply(sender, texts -> texts.get(MessageKey.RELOAD_NETWORK_FAILED,
                    "reason", Text.escape(String.valueOf(failure.getMessage()))));
            return;
        }
        for (final String node : nodes) {
            if (node.equalsIgnoreCase(remote.serverId())) {
                results.putIfAbsent(node, NetworkReloadStatus.SUCCESS);
                continue;
            }
            // A node that reported a heartbeat but no receipt has not answered
            // yet. Saying "done" here would be exactly the wrong answer.
            results.putIfAbsent(node, NetworkReloadStatus.PENDING);
        }
        send(sender, results);
    }

    private void send(final CommandSender sender, final Map<String, NetworkReloadStatus> results) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            final Language texts = language.get();
            sender.sendMessage(Text.of(texts.get(MessageKey.RELOAD_NETWORK_HEADER,
                    "product", Center.PRODUCT_NAME)));
            results.forEach((node, status) -> sender.sendMessage(Text.of(
                    texts.get(MessageKey.RELOAD_NETWORK_ENTRY,
                            "node", Text.escape(node),
                            "status", texts.get(statusKey(status))))));
        });
    }

    private void reply(final CommandSender sender, final java.util.function.Function<Language, String> message) {
        plugin.getServer().getScheduler().runTask(plugin,
                () -> sender.sendMessage(Text.of(message.apply(language.get()))));
    }

    /**
     * @return how long to wait for the other nodes, in ticks; two polling
     *         intervals plus a moment, so a node that just missed a poll is still
     *         counted
     */
    private long reportDelayTicks() {
        final long millis = remote.settings().polling().intervalMs() * 2L + 1500L;
        return Math.clamp(millis / 50L, MIN_REPORT_DELAY_TICKS, MAX_REPORT_DELAY_TICKS);
    }

    private static NetworkReloadStatus translate(final RemoteActionStatus status) {
        return switch (status) {
            case DONE -> NetworkReloadStatus.SUCCESS;
            case FAILED, IGNORED -> NetworkReloadStatus.FAILED;
            case EXPIRED -> NetworkReloadStatus.EXPIRED;
            case CLAIMED -> NetworkReloadStatus.PENDING;
        };
    }

    private static MessageKey statusKey(final NetworkReloadStatus status) {
        return switch (status) {
            case SUCCESS -> MessageKey.RELOAD_NETWORK_STATUS_SUCCESS;
            case FAILED -> MessageKey.RELOAD_NETWORK_STATUS_FAILED;
            case PENDING -> MessageKey.RELOAD_NETWORK_STATUS_PENDING;
            case UNREACHABLE -> MessageKey.RELOAD_NETWORK_STATUS_UNREACHABLE;
            case EXPIRED -> MessageKey.RELOAD_NETWORK_STATUS_EXPIRED;
        };
    }

    /** @return {@code true} if this node already answered that request. */
    public boolean alreadyProcessed(final UUID requestId) {
        return processed.known(requestId, System.currentTimeMillis());
    }
}
