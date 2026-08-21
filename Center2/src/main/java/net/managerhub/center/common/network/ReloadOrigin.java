package net.managerhub.center.common.network;

/**
 * Where a reload came from.
 *
 * <p>This is the difference that keeps the network from reloading itself forever.
 * A reload an administrator asked for goes out into the network; a reload that
 * arrived from the network is only carried out here and is never sent on. Without
 * that difference Paper A would tell the proxy, the proxy would tell Paper B,
 * Paper B would tell the proxy again, and it would never stop.</p>
 */
public enum ReloadOrigin {

    /**
     * An administrator used {@code /center reload} on this node.
     *
     * <p>The local configuration is reloaded and the request is sent into the
     * network.</p>
     */
    LOCAL_USER_REQUEST,

    /**
     * The request arrived from another Center2 node.
     *
     * <p>Only the local configuration is reloaded. Nothing is sent on.</p>
     */
    REMOTE_REQUEST;

    /** @return {@code true} if this reload may be handed on to the other nodes. */
    public boolean spreads() {
        return this == LOCAL_USER_REQUEST;
    }
}
