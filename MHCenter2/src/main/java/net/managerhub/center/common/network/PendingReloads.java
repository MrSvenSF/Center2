package net.managerhub.center.common.network;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Reload requests the proxy could not deliver yet.
 *
 * <p>A plugin message needs a player to travel through. A Paper server nobody is
 * connected to therefore cannot be reached right now - and pretending otherwise
 * would be the worst possible answer. The request is kept here instead and is
 * delivered as soon as somebody connects to that server.</p>
 *
 * <p>Everything about this is bounded on purpose:</p>
 * <ul>
 *   <li>one waiting request per server, a newer one replaces the older one,</li>
 *   <li>a request that ran out of time is dropped and never delivered,</li>
 *   <li>at most {@link #MAX_SERVERS} servers are remembered at all.</li>
 * </ul>
 *
 * <p>This lives in memory only. If the proxy restarts, the waiting requests are
 * gone - which is correct, because a server that starts afterwards reads the
 * current configuration anyway. The remote database is the way to reach a node
 * without a player at all.</p>
 */
public final class PendingReloads {

    /** Largest number of servers with a waiting request. */
    public static final int MAX_SERVERS = 128;

    /** One waiting request. */
    private record Waiting(UUID requestId, String origin, long expiresAtMillis) {
    }

    private final Map<String, Waiting> waiting = new LinkedHashMap<>();

    /**
     * Remembers that one server still has to be told.
     *
     * @param server          name of the backend server
     * @param requestId       id of the reload request
     * @param origin          name of the server the reload started on
     * @param expiresAtMillis when the request stops being valid
     */
    public synchronized void remember(final String server,
                                      final UUID requestId,
                                      final String origin,
                                      final long expiresAtMillis) {
        // A newer request replaces an older one: reloading twice in a row changes
        // nothing, and the newer request is the one an administrator is waiting for.
        waiting.remove(server);
        waiting.put(server, new Waiting(requestId, origin, expiresAtMillis));
        while (waiting.size() > MAX_SERVERS) {
            final String oldest = waiting.keySet().iterator().next();
            waiting.remove(oldest);
        }
    }

    /**
     * Takes the waiting request of one server, if there is a valid one.
     *
     * <p>The request is removed either way: a request that ran out of time is
     * dropped and is not delivered late.</p>
     *
     * @param server    name of the backend server
     * @param nowMillis the current time
     * @return the request id and the origin, or empty if there is nothing to deliver
     */
    public synchronized Optional<Delivery> take(final String server, final long nowMillis) {
        final Waiting entry = waiting.remove(server);
        if (entry == null || nowMillis >= entry.expiresAtMillis()) {
            return Optional.empty();
        }
        return Optional.of(new Delivery(entry.requestId(), entry.origin(), entry.expiresAtMillis()));
    }

    /**
     * Drops everything that ran out of time.
     *
     * @param nowMillis the current time
     * @return the servers whose request expired
     */
    public synchronized List<String> dropExpired(final long nowMillis) {
        final List<String> expired = new ArrayList<>();
        waiting.entrySet().removeIf(entry -> {
            if (nowMillis < entry.getValue().expiresAtMillis()) {
                return false;
            }
            expired.add(entry.getKey());
            return true;
        });
        return List.copyOf(expired);
    }

    /**
     * @param nowMillis the current time
     * @return the servers that are still waiting for a valid request
     */
    public synchronized List<String> servers(final long nowMillis) {
        return waiting.entrySet().stream()
                .filter(entry -> nowMillis < entry.getValue().expiresAtMillis())
                .map(Map.Entry::getKey)
                .toList();
    }

    /** @return how many servers are waiting right now, expired ones included. */
    public synchronized int size() {
        return waiting.size();
    }

    /**
     * One request that is ready to be delivered.
     *
     * @param requestId       id of the reload request
     * @param origin          name of the server the reload started on
     * @param expiresAtMillis when the request stops being valid
     */
    public record Delivery(UUID requestId, String origin, long expiresAtMillis) {
    }
}
