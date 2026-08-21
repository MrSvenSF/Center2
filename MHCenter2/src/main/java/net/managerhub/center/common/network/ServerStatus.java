package net.managerhub.center.common.network;

/**
 * State of one instance in the MHCenter2 network.
 *
 * <p>A server that was never verified is {@link #UNKNOWN}. That is not the same
 * as "MHCenter2 is missing there": it only means that MHCenter2 has not seen a
 * handshake from that server yet.</p>
 */
public enum ServerStatus {

    /** The server answers and MHCenter2 was verified there. */
    CONNECTED,

    /** The server does not answer at the moment. */
    UNREACHABLE,

    /** The server answers, but MHCenter2 was not verified there yet. */
    UNKNOWN;

    /**
     * @param name stored name of a state
     * @return the matching state, {@link #UNKNOWN} for anything unexpected
     */
    public static ServerStatus of(final String name) {
        for (final ServerStatus status : values()) {
            if (status.name().equals(name)) {
                return status;
            }
        }
        return UNKNOWN;
    }
}
