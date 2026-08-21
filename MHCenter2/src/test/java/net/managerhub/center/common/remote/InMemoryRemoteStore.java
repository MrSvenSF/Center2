package net.managerhub.center.common.remote;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link RemoteStore} that keeps everything in memory.
 *
 * <p>It exists so the whole behaviour of the remote system can be tested without
 * a database server: who runs an action, what a receipt prevents, when data
 * expires and what an atomic take really guarantees. Every rule that matters is
 * implemented the same way the MariaDB store implements it with SQL - the claim
 * of an action only works once, and so does the claim of a storage entry.</p>
 *
 * <p>The store can also be told to fail, which is how "MariaDB is down" is tested
 * without stopping a database.</p>
 */
final class InMemoryRemoteStore implements RemoteStore {

    /** One entry of the module storage. */
    private record Data(byte[] payload, long expiresAtMillis, String claimedBy) {
    }

    private final Map<String, RemoteNode> nodes = new LinkedHashMap<>();
    private final Map<UUID, RemoteAction> actions = new LinkedHashMap<>();
    private final Map<String, RemoteReceipt> receipts = new LinkedHashMap<>();
    private final Map<String, Data> storage = new LinkedHashMap<>();

    private final AtomicInteger initializes = new AtomicInteger();
    private boolean closed;

    /** When this is set, every operation fails with it. */
    private String failure;

    /** @return how often opening the store was attempted, so a reconnect can be seen. */
    int initializeCount() {
        return initializes.get();
    }

    /** @return {@code true} once the store was closed. */
    boolean closed() {
        return closed;
    }

    /**
     * Makes every following operation fail.
     *
     * @param reason what the failure says, {@code null} makes the store work again
     */
    void failWith(final String reason) {
        this.failure = reason;
    }

    /** @return every action that was written, in the order they were written. */
    List<RemoteAction> published() {
        return List.copyOf(actions.values());
    }

    @Override
    public void initialize() throws RemoteException {
        // Counted before the failure, because an attempt that fails is still an
        // attempt - that is what the backoff test has to see.
        initializes.incrementAndGet();
        check();
        closed = false;
    }

    @Override
    public void heartbeat(final RemoteNode node) throws RemoteException {
        check();
        nodes.put(key(node.serverId()), node);
    }

    @Override
    public List<RemoteNode> onlineNodes(final int offlineAfterSeconds) throws RemoteException {
        check();
        final long now = System.currentTimeMillis();
        return nodes.values().stream()
                .filter(node -> !node.offline(now, offlineAfterSeconds))
                .toList();
    }

    @Override
    public Optional<RemoteNode> node(final String serverId) throws RemoteException {
        check();
        return Optional.ofNullable(nodes.get(key(serverId)));
    }

    @Override
    public void removeNode(final String serverId, final String runtimeId) throws RemoteException {
        check();
        final RemoteNode known = nodes.get(key(serverId));
        if (known != null && known.runtimeId().equals(runtimeId)) {
            nodes.remove(key(serverId));
        }
    }

    @Override
    public void publish(final RemoteAction action) throws RemoteException {
        check();
        actions.put(action.id(), action);
    }

    @Override
    public List<RemoteAction> openActions(final String serverId, final long nowMillis, final int limit)
            throws RemoteException {
        check();
        final List<RemoteAction> open = new ArrayList<>();
        for (final RemoteAction action : actions.values()) {
            if (open.size() >= limit) {
                break;
            }
            if (action.expired(nowMillis)
                    || action.originServerId().equalsIgnoreCase(serverId)
                    || receipts.containsKey(receiptKey(action.id(), serverId))) {
                continue;
            }
            open.add(action);
        }
        return List.copyOf(open);
    }

    @Override
    public boolean claim(final UUID actionId, final String serverId) throws RemoteException {
        check();
        // Exactly like the primary key of the receipt table: only the first
        // caller for this pair of action and node creates the row.
        return receipts.putIfAbsent(receiptKey(actionId, serverId),
                new RemoteReceipt(actionId, serverId, RemoteActionStatus.CLAIMED,
                        System.currentTimeMillis(), "")) == null;
    }

    @Override
    public void finish(final UUID actionId,
                       final String serverId,
                       final RemoteActionStatus status,
                       final String error) throws RemoteException {
        check();
        receipts.computeIfPresent(receiptKey(actionId, serverId), (key, receipt) ->
                new RemoteReceipt(actionId, serverId, status, System.currentTimeMillis(), error));
    }

    @Override
    public List<RemoteReceipt> receipts(final UUID actionId) throws RemoteException {
        check();
        return receipts.values().stream().filter(receipt -> receipt.actionId().equals(actionId)).toList();
    }

    @Override
    public void putData(final String namespace,
                        final String key,
                        final byte[] payload,
                        final long expiresAtMillis) throws RemoteException {
        check();
        storage.put(dataKey(namespace, key), new Data(payload.clone(), expiresAtMillis, null));
    }

    @Override
    public Optional<byte[]> readData(final String namespace, final String key, final long nowMillis)
            throws RemoteException {
        check();
        final Data data = storage.get(dataKey(namespace, key));
        if (data == null || data.claimedBy() != null || nowMillis >= data.expiresAtMillis()) {
            return Optional.empty();
        }
        return Optional.of(data.payload().clone());
    }

    @Override
    public Optional<byte[]> takeData(final String namespace,
                                     final String key,
                                     final String claimedBy,
                                     final long nowMillis) throws RemoteException {
        check();
        final String id = dataKey(namespace, key);
        final Data data = storage.get(id);
        // The same rule the UPDATE of the MariaDB store has: only an entry that is
        // still unclaimed and not expired can be taken, and only once.
        if (data == null || data.claimedBy() != null || nowMillis >= data.expiresAtMillis()) {
            return Optional.empty();
        }
        storage.remove(id);
        return Optional.of(data.payload().clone());
    }

    @Override
    public boolean deleteData(final String namespace, final String key) throws RemoteException {
        check();
        return storage.remove(dataKey(namespace, key)) != null;
    }

    @Override
    public int purgeExpired(final long nowMillis) throws RemoteException {
        check();
        final int before = storage.size() + actions.size();
        storage.values().removeIf(data -> nowMillis >= data.expiresAtMillis());
        final List<UUID> expired = actions.values().stream()
                .filter(action -> action.expired(nowMillis))
                .map(RemoteAction::id)
                .toList();
        expired.forEach(actions::remove);
        receipts.values().removeIf(receipt -> expired.contains(receipt.actionId()));
        return before - storage.size() - actions.size();
    }

    @Override
    public void close() {
        closed = true;
    }

    private void check() throws RemoteException {
        if (failure != null) {
            throw new RemoteException(failure);
        }
    }

    private static String key(final String serverId) {
        return serverId.toLowerCase(Locale.ROOT);
    }

    private static String receiptKey(final UUID actionId, final String serverId) {
        return actionId + "|" + key(serverId);
    }

    private static String dataKey(final String namespace, final String key) {
        return namespace.toLowerCase(Locale.ROOT) + "|" + key;
    }
}
