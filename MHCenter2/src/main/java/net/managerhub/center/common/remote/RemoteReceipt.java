package net.managerhub.center.common.remote;

import java.util.UUID;

/**
 * What one node reported about one action.
 *
 * <p>One row per node and action. That is the whole point: an action for the
 * whole network needs an answer from every node, and the first node that is
 * finished must not look like all of them.</p>
 *
 * @param actionId         the action
 * @param serverId         the node that answered
 * @param status           what happened there
 * @param processedAtMillis when the node answered
 * @param error            short reason if it failed, empty otherwise
 */
public record RemoteReceipt(UUID actionId,
                            String serverId,
                            RemoteActionStatus status,
                            long processedAtMillis,
                            String error) {

    public RemoteReceipt {
        error = error == null ? "" : error;
    }
}
