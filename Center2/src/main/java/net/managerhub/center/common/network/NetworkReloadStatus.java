package net.managerhub.center.common.network;

/**
 * What happened to a network wide reload on one Center2 node.
 *
 * <p>The states exist so an administrator is never told something that was not
 * confirmed. "The reload was sent" and "the reload happened" are two different
 * things, and with plugin messages the difference is real: a server nobody is
 * connected to cannot be reached at that moment.</p>
 */
public enum NetworkReloadStatus {

    /** The node reloaded and confirmed it. */
    SUCCESS,

    /** The node tried to reload and failed. The reason is in its own log. */
    FAILED,

    /** The request is waiting for a way to reach the node. */
    PENDING,

    /** There is no way to reach the node at the moment. */
    UNREACHABLE,

    /** The request was not delivered before its lifetime ran out. */
    EXPIRED
}
