package net.managerhub.center.common.remote;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import net.managerhub.center.Center;

/**
 * The validated {@code remote:} section of {@code MainConfig.yml}.
 *
 * <p>The remote database is optional. {@code enabled: false} is the default and
 * means Center2 never opens a connection at all - it does not even try, so a
 * server without a database sees no error, no timeout and no log noise.</p>
 *
 * <p>With {@code enabled: true} every value has to be usable. A missing
 * {@code server-id}, host, database or user is refused here, and the caller then
 * keeps Center2 running locally with the remote system switched off. That is on
 * purpose: half a connection would be worse than none, because the node would
 * appear in the network without being able to answer.</p>
 *
 * @param enabled   whether the remote system should be used at all
 * @param serverId  the id of this node in the network, lower case
 * @param database  how to reach the database
 * @param polling   how often new actions are looked for
 * @param heartbeat how often this node reports that it is alive
 */
public record RemoteSettings(boolean enabled,
                             String serverId,
                             Database database,
                             Polling polling,
                             Heartbeat heartbeat) {

    /** The remote system as it is when it was never switched on. */
    public static final RemoteSettings DISABLED = new RemoteSettings(false, "",
            new Database("", 3306, "", "", "", true),
            new Polling(1000, 60),
            new Heartbeat(10));

    /** Shortest polling interval, so a wrong value cannot hammer the database. */
    public static final int MIN_POLL_INTERVAL_MS = 250;

    /** Longest polling interval that still makes a network reload feel immediate. */
    public static final int MAX_POLL_INTERVAL_MS = 60_000;

    /** Shortest lifetime of an action. */
    public static final int MIN_ACTION_TTL_SECONDS = 5;

    /** Longest lifetime of an action. */
    public static final int MAX_ACTION_TTL_SECONDS = 3600;

    /** Shortest heartbeat interval. */
    public static final int MIN_HEARTBEAT_SECONDS = 1;

    /** Longest heartbeat interval. */
    public static final int MAX_HEARTBEAT_SECONDS = 300;

    /**
     * How many heartbeat intervals a node may miss before it counts as offline.
     *
     * <p>Three is a compromise: a single lost heartbeat during a lag spike must
     * not mark a healthy server as gone, and a node that really died must not
     * stay "online" for minutes.</p>
     */
    public static final int OFFLINE_AFTER_MISSED_HEARTBEATS = 3;

    /** Allowed shape of a server id. */
    private static final Pattern SERVER_ID = Pattern.compile("[a-z0-9_-]{1,64}");

    /**
     * How to reach the remote database.
     *
     * @param host     host name or address of the database
     * @param port     port of the database
     * @param database name of the schema
     * @param username database user
     * @param password password of that user; never written into a log
     * @param ssl      whether the connection has to be encrypted
     */
    public record Database(String host, int port, String database, String username, String password, boolean ssl) {

        /**
         * @return the connection without user, password and any other credential,
         *         safe to write into a log
         */
        public String describe() {
            return host + ":" + port + "/" + database + (ssl ? " (SSL)" : " (no SSL)");
        }
    }

    /**
     * How often this node looks for new actions.
     *
     * @param intervalMs        time between two looks, in milliseconds
     * @param actionTtlSeconds  how long an action of the core stays valid
     */
    public record Polling(int intervalMs, int actionTtlSeconds) {
    }

    /**
     * How often this node reports that it is alive.
     *
     * @param intervalSeconds time between two heartbeats
     */
    public record Heartbeat(int intervalSeconds) {

        /** @return the age above which a node counts as offline, in seconds. */
        public int offlineAfterSeconds() {
            return intervalSeconds * OFFLINE_AFTER_MISSED_HEARTBEATS;
        }
    }

    public RemoteSettings {
        serverId = serverId == null ? "" : serverId.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Normalizes a configured server id.
     *
     * @param raw the raw value of {@code remote.server-id}
     * @return the id in lower case, empty text if nothing was configured
     */
    public static String normalizeServerId(final String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Checks whether these settings can really be used.
     *
     * <p>Settings that are switched off are always fine - there is nothing to
     * check. Settings that are switched on have to be complete, because a node
     * that joins the network with half a configuration is worse than a node that
     * stays local.</p>
     *
     * @return every problem, in the order they were found; empty means usable
     */
    public List<String> problems() {
        if (!enabled) {
            return List.of();
        }
        final List<String> problems = new ArrayList<>();
        if (serverId.isEmpty()) {
            problems.add("'remote.server-id' is empty. Every " + Center.PRODUCT_NAME
                    + " node needs its own id in the network, for example \"lobby\" or \"velocity\".");
        } else if (!SERVER_ID.matcher(serverId).matches()) {
            problems.add("'remote.server-id' is \"" + serverId
                    + "\". Allowed are up to 64 characters of a-z, 0-9, '_' and '-'.");
        }
        if (database.host().isBlank()) {
            problems.add("'remote.database.host' is empty.");
        }
        if (database.port() < 1 || database.port() > 65535) {
            problems.add("'remote.database.port' is " + database.port() + ", allowed is 1 to 65535.");
        }
        if (database.database().isBlank()) {
            problems.add("'remote.database.database' is empty.");
        }
        if (database.username().isBlank()) {
            problems.add("'remote.database.username' is empty.");
        }
        if (polling.intervalMs() < MIN_POLL_INTERVAL_MS || polling.intervalMs() > MAX_POLL_INTERVAL_MS) {
            problems.add("'remote.polling.interval-ms' is " + polling.intervalMs()
                    + ", allowed is " + MIN_POLL_INTERVAL_MS + " to " + MAX_POLL_INTERVAL_MS + ".");
        }
        if (polling.actionTtlSeconds() < MIN_ACTION_TTL_SECONDS
                || polling.actionTtlSeconds() > MAX_ACTION_TTL_SECONDS) {
            problems.add("'remote.polling.action-ttl-seconds' is " + polling.actionTtlSeconds()
                    + ", allowed is " + MIN_ACTION_TTL_SECONDS + " to " + MAX_ACTION_TTL_SECONDS + ".");
        }
        if (heartbeat.intervalSeconds() < MIN_HEARTBEAT_SECONDS
                || heartbeat.intervalSeconds() > MAX_HEARTBEAT_SECONDS) {
            problems.add("'remote.heartbeat.interval-seconds' is " + heartbeat.intervalSeconds()
                    + ", allowed is " + MIN_HEARTBEAT_SECONDS + " to " + MAX_HEARTBEAT_SECONDS + ".");
        }
        return List.copyOf(problems);
    }

    /** @return {@code true} if the remote system is switched on and completely configured. */
    public boolean usable() {
        return enabled && problems().isEmpty();
    }

    /**
     * Whether a reconnect has to build a completely new connection.
     *
     * <p>Only the values that really decide the connection count. A changed
     * polling interval restarts the tasks but keeps the pool.</p>
     *
     * @param other the settings that were active before
     * @return {@code true} if the connection has to be built again
     */
    public boolean connectionDiffers(final RemoteSettings other) {
        return other == null
                || enabled != other.enabled
                || !serverId.equals(other.serverId)
                || !database.equals(other.database);
    }
}
