package net.managerhub.center.common.network;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The request ids one MHCenter2 node has already answered.
 *
 * <p>This is the second half of the loop protection. {@link ReloadOrigin} decides
 * whether a request is handed on; this class decides whether it is carried out at
 * all. A node that already reloaded for request {@code X} never reloads for
 * {@code X} again, no matter how often and over how many ways the request arrives
 * - through the proxy, through the remote database, or through both.</p>
 *
 * <p>Nothing grows without a limit here. An entry is forgotten once it is older
 * than the lifetime of a request, and the oldest entries are dropped when too
 * many arrive at once, so a flood of requests cannot fill the memory of a
 * server.</p>
 */
public final class ProcessedRequests {

    /** Largest number of ids one node remembers. */
    public static final int MAX_ENTRIES = 512;

    private final long lifetimeMillis;

    /** Request id to the time it was claimed, oldest first. */
    private final Map<UUID, Long> claimed = new LinkedHashMap<>();

    /**
     * @param lifetimeMillis how long an id is remembered; a request that is older
     *                       than this cannot arrive any more anyway
     */
    public ProcessedRequests(final long lifetimeMillis) {
        this.lifetimeMillis = lifetimeMillis;
    }

    /**
     * Claims one request for this node.
     *
     * @param requestId id of the request
     * @param nowMillis the current time
     * @return {@code true} if this node may carry the request out now, {@code false}
     *         if it already did
     */
    public synchronized boolean claim(final UUID requestId, final long nowMillis) {
        forgetOld(nowMillis);
        if (claimed.containsKey(requestId)) {
            return false;
        }
        claimed.put(requestId, nowMillis);
        while (claimed.size() > MAX_ENTRIES) {
            final UUID oldest = claimed.keySet().iterator().next();
            claimed.remove(oldest);
        }
        return true;
    }

    /**
     * @param requestId id of the request
     * @param nowMillis the current time
     * @return {@code true} if this node already answered that request
     */
    public synchronized boolean known(final UUID requestId, final long nowMillis) {
        forgetOld(nowMillis);
        return claimed.containsKey(requestId);
    }

    /** @return how many ids are remembered right now. */
    public synchronized int size() {
        return claimed.size();
    }

    private void forgetOld(final long nowMillis) {
        claimed.values().removeIf(claimedAt -> nowMillis - claimedAt > lifetimeMillis);
    }
}
