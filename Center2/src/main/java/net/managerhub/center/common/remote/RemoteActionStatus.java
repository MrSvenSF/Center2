package net.managerhub.center.common.remote;

/**
 * What one node did with one action.
 *
 * <p>The status is stored per node, not per action. An action for the whole
 * network is only finished when every node wrote its own row, so the first
 * server that is done can never mark it as done for everybody.</p>
 */
public enum RemoteActionStatus {

    /** The node claimed the action and is running it right now. */
    CLAIMED,

    /** The node ran the action successfully. */
    DONE,

    /** The node tried and failed. The reason is stored with the row and in the log. */
    FAILED,

    /** The node saw the action only after it had expired and did not run it. */
    EXPIRED,

    /** The node knows the action but nothing there wants it, for example an unknown type. */
    IGNORED
}
