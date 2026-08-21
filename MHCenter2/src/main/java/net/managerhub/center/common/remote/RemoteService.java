package net.managerhub.center.common.remote;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import net.managerhub.center.Center;
import net.managerhub.center.api.ModuleActionListener;
import net.managerhub.center.api.ModuleActionMessage;
import net.managerhub.center.api.ModuleActionTarget;
import net.managerhub.center.api.ModuleLogger;
import net.managerhub.center.api.ModulePlatform;
import net.managerhub.center.common.language.Language;
import net.managerhub.center.common.language.MessageKey;

/**
 * The optional remote system of one MHCenter2 node.
 *
 * <p>MHCenter2 works completely without it. This class is what makes two nodes
 * able to coordinate <em>without a player</em>: it reports that this node is
 * alive, it looks for actions that are addressed to this node, and it hands the
 * short lived storage and the actions of the modules through to the database.</p>
 *
 * <p>Nothing here runs on the main server thread of Paper or on the event loop of
 * Velocity. The platform hands in an executor and a way to run a reload where it
 * belongs; everything else stays in this class.</p>
 *
 * <p>A database that is gone is a normal state, not a reason to stop: the service
 * goes offline, keeps trying with a growing pause between the attempts, writes
 * one line about it and one line when it is back. It never fills the log with the
 * same exception every second.</p>
 */
public final class RemoteService {

    /** What the remote system of this node is doing right now. */
    public enum State {

        /** Switched off in the configuration, or switched off because it was misconfigured. */
        OFF,

        /** Switched on, but the database did not answer yet. */
        CONNECTING,

        /** Switched on and working. */
        ONLINE,

        /** Switched on, was working, and the database is not answering right now. */
        OFFLINE,

        /** Switched off again because another node uses the same server-id. */
        CONFLICT
    }

    /** Runs one action of the core where that platform needs it to run. */
    @FunctionalInterface
    public interface CoreActionHandler {

        /**
         * @param action the action of the core, already claimed by this node
         * @throws Exception if the action could not be carried out
         */
        void run(RemoteAction action) throws Exception;
    }

    /** Shortest pause between two connection attempts. */
    static final long MIN_RETRY_MS = 1000L;

    /** Longest pause between two connection attempts. */
    static final long MAX_RETRY_MS = 60_000L;

    /** After this many failures in a row the same failure is written into the log again. */
    static final int REPEAT_LOG_EVERY = 20;

    /** Largest number of actions one poll takes. */
    static final int POLL_LIMIT = 50;

    /** Largest payload of one action. */
    public static final int MAX_ACTION_PAYLOAD = 1024 * 1024;

    /** Largest payload of one storage entry. */
    public static final int MAX_STORAGE_PAYLOAD = 8 * 1024 * 1024;

    /** Longest key of a storage entry. */
    public static final int MAX_STORAGE_KEY = 190;

    /** Allowed shape of an action type. */
    private static final Pattern ACTION_TYPE = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final ModulePlatform platform;
    private final String minecraftVersion;
    private final ModuleLogger log;
    private final Supplier<Language> language;
    private final Function<RemoteSettings.Database, RemoteStore> stores;
    private final CoreActionHandler coreActions;
    private final LongSupplier clock;

    /** New for every start of this node, so a duplicate server-id becomes visible. */
    private final String runtimeId = UUID.randomUUID().toString();

    private final Map<String, ModuleActionListener> moduleListeners = new ConcurrentHashMap<>();

    private volatile RemoteSettings settings = RemoteSettings.DISABLED;
    private volatile State state = State.OFF;
    private volatile RemoteStore store;
    private volatile List<String> onlineNodes = List.of();

    private long nextAttemptAt;
    private int failures;
    private String lastFailure = "";

    /**
     * @param platform         the platform this node runs on
     * @param minecraftVersion the Minecraft version on Paper, empty text on the proxy
     * @param log              where the remote system writes
     * @param language         the texts that are currently active
     * @param stores           builds the store for a database configuration
     * @param coreActions      runs an action of the core where the platform needs it
     * @param clock            the current time in milliseconds
     */
    public RemoteService(final ModulePlatform platform,
                         final String minecraftVersion,
                         final ModuleLogger log,
                         final Supplier<Language> language,
                         final Function<RemoteSettings.Database, RemoteStore> stores,
                         final CoreActionHandler coreActions,
                         final LongSupplier clock) {
        this.platform = platform;
        this.minecraftVersion = minecraftVersion == null ? "" : minecraftVersion;
        this.log = log;
        this.language = language;
        this.stores = stores;
        this.coreActions = coreActions;
        this.clock = clock;
    }

    /**
     * Applies a configuration.
     *
     * <p>This is both the start and the answer to a reload. A configuration that
     * only changed the polling interval keeps the open connection; a changed
     * server-id or a changed database closes the old connection first, so no
     * second heartbeat and no second poller can survive a reload.</p>
     *
     * @param next the settings of the configuration that is active now
     */
    public synchronized void apply(final RemoteSettings next) {
        final RemoteSettings previous = settings;
        final boolean rebuild = next.connectionDiffers(previous) || state == State.CONFLICT;
        settings = next;

        if (!next.enabled()) {
            if (previous.enabled()) {
                log.info(text(MessageKey.REMOTE_DISABLED,
                        "product", Center.PRODUCT_NAME, "file", Center.MAIN_CONFIG_FILE));
            }
            shutdownStore();
            state = State.OFF;
            return;
        }

        final List<String> problems = next.problems();
        if (!problems.isEmpty()) {
            shutdownStore();
            state = State.OFF;
            log.warn(text(MessageKey.REMOTE_INVALID,
                    "product", Center.PRODUCT_NAME, "reason", String.join(" ", problems)));
            return;
        }

        if (rebuild || store == null) {
            shutdownStore();
            state = State.CONNECTING;
            failures = 0;
            lastFailure = "";
            nextAttemptAt = 0L;
            store = stores.apply(next.database());
        }
    }

    /** @return what the remote system is doing right now. */
    public State state() {
        return state;
    }

    /** @return {@code true} if an operation on the remote database can work right now. */
    public boolean available() {
        return state == State.ONLINE;
    }

    /** @return the settings that are active right now. */
    public RemoteSettings settings() {
        return settings;
    }

    /** @return the id of this node in the network, empty text when the remote system is off. */
    public String serverId() {
        return settings.enabled() ? settings.serverId() : "";
    }

    /** @return the id of this one run of the node. */
    public String runtimeId() {
        return runtimeId;
    }

    /** @return the ids of every node that reported recently, from the last heartbeat cycle. */
    public List<String> onlineNodes() {
        return onlineNodes;
    }

    /**
     * Connects if needed and reports that this node is alive.
     *
     * <p>Runs on the worker of the platform. It is also the place where a
     * duplicate {@code server-id} is found: the row of this id is read before it
     * is written, so a second node with the same id sees the first one and stops
     * instead of overwriting it.</p>
     */
    public synchronized void heartbeatTick() {
        final RemoteStore open = store;
        if (open == null || state == State.OFF || state == State.CONFLICT) {
            return;
        }
        final long now = clock.getAsLong();
        if (now < nextAttemptAt) {
            return;
        }
        try {
            if (state == State.CONNECTING || state == State.OFFLINE) {
                open.initialize();
            }
            if (duplicateNode(open)) {
                return;
            }
            open.heartbeat(new RemoteNode(settings.serverId(), runtimeId, platform,
                    Center.VERSION, minecraftVersion, now));
            onlineNodes = open.onlineNodes(settings.heartbeat().offlineAfterSeconds()).stream()
                    .map(RemoteNode::serverId)
                    .sorted()
                    .toList();
            recovered();
        } catch (final RemoteException | RuntimeException failure) {
            failed(failure);
        }
    }

    /**
     * Looks for actions that are addressed to this node and runs them.
     *
     * <p>Every action is claimed first. Only the claim that really created the
     * receipt runs the action, so a node can never run the same action twice - not
     * after a second poll, not after a restart and not from a second thread.</p>
     */
    public void pollTick() {
        final RemoteStore open = store;
        if (open == null || state != State.ONLINE) {
            return;
        }
        final long now = clock.getAsLong();
        final List<RemoteAction> actions;
        try {
            actions = open.openActions(settings.serverId(), now, POLL_LIMIT);
        } catch (final RemoteException | RuntimeException failure) {
            synchronized (this) {
                failed(failure);
            }
            return;
        }
        for (final RemoteAction action : actions) {
            handle(open, action, now);
        }
    }

    /**
     * Removes everything that has expired.
     *
     * <p>Without this the remote storage would slowly turn into a permanent
     * database of half finished player transfers.</p>
     */
    public void purgeTick() {
        final RemoteStore open = store;
        if (open == null || state != State.ONLINE) {
            return;
        }
        try {
            final int removed = open.purgeExpired(clock.getAsLong());
            if (removed > 0) {
                log.info(text(MessageKey.REMOTE_PURGED, "rows", Integer.toString(removed)));
            }
        } catch (final RemoteException | RuntimeException failure) {
            synchronized (this) {
                failed(failure);
            }
        }
    }

    /**
     * Writes the network wide reload of the core into the database.
     *
     * @param requestId id of this reload, the same on every node
     * @return the action that was written
     * @throws RemoteException if the remote system is not available or the write failed
     */
    public RemoteAction publishReload(final UUID requestId) throws RemoteException {
        final long now = clock.getAsLong();
        final RemoteAction action = new RemoteAction(requestId,
                Center.CORE_NAMESPACE,
                RemoteAction.CENTER_RELOAD,
                settings.serverId(),
                ModuleActionTarget.ALL,
                now,
                now + settings.polling().actionTtlSeconds() * 1000L,
                new byte[0]);
        publish(action);
        return action;
    }

    /**
     * Writes one action of a module into the database.
     *
     * @param moduleId id of the module, which is also its namespace
     * @param type     name of the action the module chose
     * @param target   which nodes should run it
     * @param payload  data of the module
     * @param lifetime how long the action stays valid
     * @return the action that was written
     * @throws RemoteException if the remote system is not available or the write failed
     * @throws IllegalArgumentException if a value is outside the allowed range
     */
    public RemoteAction publishModuleAction(final String moduleId,
                                            final String type,
                                            final ModuleActionTarget target,
                                            final byte[] payload,
                                            final Duration lifetime) throws RemoteException {
        final RemoteAction action = createModuleAction(moduleId, type, target, payload, lifetime,
                settings.serverId());
        publish(action);
        return action;
    }

    /** Builds and validates a module action for a non-database transport. */
    public RemoteAction createModuleAction(final String moduleId,
                                           final String type,
                                           final ModuleActionTarget target,
                                           final byte[] payload,
                                           final Duration lifetime,
                                           final String originServerId) {
        if (target == null) {
            throw new IllegalArgumentException("An action needs a target.");
        }
        if (type == null || !ACTION_TYPE.matcher(type).matches()) {
            throw new IllegalArgumentException("An action type is up to 64 characters of a-z, A-Z, 0-9, "
                    + "'_', '-' and '.'; \"" + type + "\" is not.");
        }
        if (Center.CORE_NAMESPACE.equalsIgnoreCase(moduleId)) {
            throw new IllegalArgumentException("The namespace '" + Center.CORE_NAMESPACE + "' belongs to "
                    + Center.PRODUCT_NAME + " itself and cannot be used by a module.");
        }
        final byte[] data = payload == null ? new byte[0] : payload;
        if (data.length > MAX_ACTION_PAYLOAD) {
            throw new IllegalArgumentException("The payload of an action is at most "
                    + MAX_ACTION_PAYLOAD + " bytes, this one has " + data.length + ".");
        }
        if (lifetime == null || lifetime.toSeconds() < 1) {
            throw new IllegalArgumentException("An action needs a lifetime of at least one second.");
        }
        final long now = clock.getAsLong();
        return new RemoteAction(UUID.randomUUID(), moduleId, type,
                originServerId, target, now, now + lifetime.toMillis(), data);
    }

    /**
     * @param actionId id of an action
     * @return what every node reported about it
     * @throws RemoteException if the remote system is not available or the read failed
     */
    public List<RemoteReceipt> receipts(final UUID actionId) throws RemoteException {
        return required().receipts(actionId);
    }

    /**
     * Registers what one module does with the actions that are addressed to it.
     *
     * @param moduleId id of the module
     * @param listener what to do, {@code null} removes the listener
     */
    public void moduleListener(final String moduleId, final ModuleActionListener listener) {
        final String namespace = moduleId.toLowerCase(Locale.ROOT);
        if (listener == null) {
            moduleListeners.remove(namespace);
            return;
        }
        moduleListeners.put(namespace, listener);
    }

    /**
     * @param moduleId id of the module
     * @return the store, only for the namespace of that module
     * @throws RemoteException if the remote system is not available
     */
    public RemoteStore required() throws RemoteException {
        final RemoteStore open = store;
        if (open == null || state != State.ONLINE) {
            throw new RemoteException(reasonForUnavailable());
        }
        return open;
    }

    /** @return why the remote system cannot be used right now, written for an administrator. */
    public String reasonForUnavailable() {
        return switch (state) {
            case OFF -> settings.enabled()
                    ? "The remote system of " + Center.PRODUCT_NAME + " is switched on but not usable. "
                            + "Check the 'remote' section of " + Center.MAIN_CONFIG_FILE + "."
                    : "The remote system of " + Center.PRODUCT_NAME + " is switched off in "
                            + Center.MAIN_CONFIG_FILE + " ('remote.enabled: false').";
            case CONNECTING -> "The remote database of " + Center.PRODUCT_NAME + " is not connected yet.";
            case OFFLINE -> "The remote database of " + Center.PRODUCT_NAME + " is not reachable right now.";
            case CONFLICT -> "The remote system of " + Center.PRODUCT_NAME + " is switched off on this node, "
                    + "because another node uses the same 'remote.server-id'.";
            case ONLINE -> "";
        };
    }

    /** Removes this node from the network list and closes the connection. */
    public synchronized void stop() {
        final RemoteStore open = store;
        if (open != null && state == State.ONLINE) {
            try {
                open.removeNode(settings.serverId(), runtimeId);
            } catch (final RemoteException | RuntimeException ignored) {
                // The row expires on its own, so a failure here changes nothing.
            }
        }
        moduleListeners.clear();
        shutdownStore();
        state = settings.enabled() ? State.OFF : State.OFF;
    }

    private void publish(final RemoteAction action) throws RemoteException {
        required().publish(action);
    }

    /**
     * Runs one action of this node.
     *
     * <p>A failure of a module never stops the poll: the receipt is written as
     * failed and the next action is taken. Otherwise one broken module would
     * block the whole network for this node.</p>
     */
    private void handle(final RemoteStore open, final RemoteAction action, final long now) {
        if (!action.addresses(settings.serverId(), platform)) {
            return;
        }
        try {
            if (!open.claim(action.id(), settings.serverId())) {
                // Another thread or an earlier run of this node was faster.
                return;
            }
        } catch (final RemoteException | RuntimeException failure) {
            synchronized (this) {
                failed(failure);
            }
            return;
        }

        if (action.expired(now)) {
            finish(open, action, RemoteActionStatus.EXPIRED, "");
            return;
        }
        try {
            if (action.core()) {
                runCoreAction(action);
            } else {
                runModuleAction(action);
            }
            finish(open, action, RemoteActionStatus.DONE, "");
        } catch (final UnknownActionException unknown) {
            log.warn(text(MessageKey.REMOTE_ACTION_REJECTED,
                    "action", action.type(),
                    "namespace", action.namespace(),
                    "reason", unknown.getMessage()));
            finish(open, action, RemoteActionStatus.IGNORED, unknown.getMessage());
        } catch (final Throwable broken) {
            log.error(text(MessageKey.REMOTE_ACTION_FAILED,
                    "action", action.type(), "namespace", action.namespace()), broken);
            finish(open, action, RemoteActionStatus.FAILED, describe(broken));
        }
    }

    /**
     * Runs an action of MHCenter2 itself.
     *
     * <p>Only known types are run. An unknown type is written down and ignored -
     * the remote database is not a console and a row in it is never turned into a
     * command.</p>
     */
    private void runCoreAction(final RemoteAction action) throws Exception {
        if (!RemoteAction.CENTER_RELOAD.equals(action.type())) {
            throw new UnknownActionException("The network action '" + action.type() + "' is not a known "
                    + Center.PRODUCT_NAME + " action and was not carried out. Only '"
                    + RemoteAction.CENTER_RELOAD + "' exists.");
        }
        coreActions.run(action);
    }

    /** Hands an action to the module it belongs to. */
    private void runModuleAction(final RemoteAction action) throws Exception {
        final ModuleActionListener listener = moduleListeners.get(action.namespace());
        if (listener == null) {
            throw new UnknownActionException("The network action '" + action.type() + "' is for the module '"
                    + action.namespace() + "', which is not running here. It was not carried out.");
        }
        listener.onAction(new ModuleActionMessage(action.id(), action.type(),
                action.originServerId(), action.payload()));
    }

    /** Delivers a validated action received through the player-carried fallback. */
    public void deliverModuleAction(final RemoteAction action) throws Exception {
        runModuleAction(action);
    }

    private void finish(final RemoteStore open,
                        final RemoteAction action,
                        final RemoteActionStatus status,
                        final String error) {
        try {
            open.finish(action.id(), settings.serverId(), status, error);
        } catch (final RemoteException | RuntimeException failure) {
            synchronized (this) {
                failed(failure);
            }
        }
    }

    /**
     * @return {@code true} if another node already uses this server-id; the state
     *         was switched to {@link State#CONFLICT} in that case
     */
    private boolean duplicateNode(final RemoteStore open) throws RemoteException {
        final Optional<RemoteNode> known = open.node(settings.serverId());
        if (known.isEmpty() || known.get().runtimeId().equals(runtimeId)) {
            return false;
        }
        if (known.get().offline(clock.getAsLong(), settings.heartbeat().offlineAfterSeconds())) {
            // The row belongs to a run that is long gone, for example after a
            // crash. Taking the id over is exactly right.
            return false;
        }
        state = State.CONFLICT;
        onlineNodes = List.of();
        log.error(text(MessageKey.REMOTE_DUPLICATE_ID,
                "product", Center.PRODUCT_NAME,
                "server", settings.serverId(),
                "file", Center.MAIN_CONFIG_FILE), null);
        shutdownStore();
        return true;
    }

    /** One successful cycle. Only the first one after a failure says anything. */
    private void recovered() {
        if (state != State.ONLINE) {
            log.info(text(MessageKey.REMOTE_CONNECTED,
                    "database", settings.database().describe(), "server", settings.serverId()));
        }
        state = State.ONLINE;
        failures = 0;
        lastFailure = "";
        nextAttemptAt = 0L;
    }

    /**
     * One failed cycle.
     *
     * <p>The pause between two attempts doubles up to a minute, and the same
     * failure is only written again every {@link #REPEAT_LOG_EVERY} attempts. A
     * database that is down for an hour therefore costs a handful of log lines,
     * not thousands.</p>
     */
    private void failed(final Throwable failure) {
        final String reason = describe(failure);
        final boolean wasOnline = state == State.ONLINE;
        state = State.OFFLINE;
        failures++;
        nextAttemptAt = clock.getAsLong() + retryDelay();

        if (wasOnline || !reason.equals(lastFailure) || failures % REPEAT_LOG_EVERY == 0) {
            log.warn(text(MessageKey.REMOTE_UNREACHABLE,
                    "product", Center.PRODUCT_NAME,
                    "database", settings.database().describe(),
                    "reason", reason,
                    "seconds", Long.toString(retryDelay() / 1000L),
                    "attempt", Integer.toString(failures)));
        }
        lastFailure = reason;
    }

    private long retryDelay() {
        long delay = MIN_RETRY_MS;
        for (int attempt = 1; attempt < failures && delay < MAX_RETRY_MS; attempt++) {
            delay *= 2;
        }
        return Math.min(delay, MAX_RETRY_MS);
    }

    private void shutdownStore() {
        final RemoteStore open = store;
        store = null;
        onlineNodes = List.of();
        if (open != null) {
            open.close();
        }
    }

    /** @return one text of the active language, with its placeholders filled in. */
    private String text(final MessageKey key, final String... placeholders) {
        return language.get().get(key, placeholders);
    }

    private static String describe(final Throwable failure) {
        final String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return failure.getClass().getSimpleName();
        }
        return message;
    }

    /** An action nobody here knows. It is written down and never carried out. */
    private static final class UnknownActionException extends Exception {

        private static final long serialVersionUID = 1L;

        private UnknownActionException(final String message) {
            super(message);
        }
    }

    /** @return every node id, used for tests and for the status report. */
    List<String> snapshotNodes() {
        return new ArrayList<>(onlineNodes);
    }
}
