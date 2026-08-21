package net.managerhub.center.common.remote;

import net.managerhub.center.api.ModulePlatform;

/**
 * One Center2 node as the remote database knows it.
 *
 * <p>A node writes this row every heartbeat interval. It is deliberately small:
 * who am I, what am I, when was I last alive. Center2 is not a monitoring
 * system.</p>
 *
 * <p>The {@code runtimeId} is new for every start of a node. It is what makes a
 * duplicate {@code server-id} visible: two rows can never exist for one id, so
 * a second node with the same id would overwrite the first one - and the first
 * one sees its own runtime id disappear on the next heartbeat.</p>
 *
 * @param serverId         configured id of the node
 * @param runtimeId        id of this one run of the node
 * @param platform         {@link ModulePlatform#PAPER} or {@link ModulePlatform#VELOCITY}
 * @param centerVersion    the Center2 version running there
 * @param minecraftVersion the Minecraft version on Paper, empty text on the proxy
 * @param lastSeenMillis   when the node last reported, in milliseconds since the epoch
 */
public record RemoteNode(String serverId,
                         String runtimeId,
                         ModulePlatform platform,
                         String centerVersion,
                         String minecraftVersion,
                         long lastSeenMillis) {

    /**
     * @param nowMillis     the current time
     * @param offlineAfterSeconds age above which a node counts as offline
     * @return {@code true} if the last heartbeat is too old
     */
    public boolean offline(final long nowMillis, final int offlineAfterSeconds) {
        return nowMillis - lastSeenMillis > offlineAfterSeconds * 1000L;
    }
}
