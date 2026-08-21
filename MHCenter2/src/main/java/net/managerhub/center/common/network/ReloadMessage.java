package net.managerhub.center.common.network;

import java.util.UUID;

/**
 * One reload request as it travels through a plugin message.
 *
 * <p>Three values are enough and none of them may be missing: the id makes sure
 * every node runs the request exactly once, the origin says where it started so a
 * node never answers back to itself, and the expiry makes sure a request that was
 * waiting too long is dropped instead of arriving hours late.</p>
 *
 * @param requestId       id of the request, the same on every node
 * @param origin          name of the server the reload started on
 * @param expiresAtMillis when the request stops being valid
 */
public record ReloadMessage(UUID requestId, String origin, long expiresAtMillis) {

    public ReloadMessage {
        origin = origin == null ? "" : origin;
    }

    /**
     * @param nowMillis the current time
     * @return {@code true} if the request may still be carried out
     */
    public boolean valid(final long nowMillis) {
        return nowMillis < expiresAtMillis;
    }
}
