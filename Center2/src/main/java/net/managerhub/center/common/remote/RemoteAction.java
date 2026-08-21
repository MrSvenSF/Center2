package net.managerhub.center.common.remote;

import java.util.Locale;
import java.util.UUID;

import net.managerhub.center.Center;
import net.managerhub.center.api.ModuleActionTarget;
import net.managerhub.center.api.ModulePlatform;

/**
 * One action in the remote database.
 *
 * <p>An action is not a command. It carries a namespace and a type, and both are
 * only names: the core knows exactly one type of its own,
 * {@link #CENTER_RELOAD}, and every other namespace belongs to the module of that
 * id. Nothing that comes out of the database is ever handed to a console.</p>
 *
 * <p>Every node checks {@link #addresses} for itself and then writes a receipt,
 * so an action that is meant for everybody is really run by everybody - and by
 * each of them exactly once.</p>
 *
 * @param id             id of the action, unique in the whole network
 * @param namespace      {@code center} for the core, otherwise the id of a module
 * @param type           what should happen
 * @param originServerId the node that created the action
 * @param target         which nodes should run it
 * @param createdAtMillis when it was created
 * @param expiresAtMillis when it stops being valid
 * @param payload        data of the module, empty for the core
 */
public record RemoteAction(UUID id,
                           String namespace,
                           String type,
                           String originServerId,
                           ModuleActionTarget target,
                           long createdAtMillis,
                           long expiresAtMillis,
                           byte[] payload) {

    /** The only action type the core itself knows: reload the Center2 configuration. */
    public static final String CENTER_RELOAD = "CENTER_RELOAD";

    public RemoteAction {
        namespace = namespace == null ? "" : namespace.trim().toLowerCase(Locale.ROOT);
        originServerId = originServerId == null ? "" : originServerId.trim().toLowerCase(Locale.ROOT);
        payload = payload == null ? new byte[0] : payload.clone();
    }

    /** @return a copy of the payload. */
    @Override
    public byte[] payload() {
        return payload.clone();
    }

    /** @return {@code true} if this action belongs to Center2 itself. */
    public boolean core() {
        return Center.CORE_NAMESPACE.equals(namespace);
    }

    /**
     * @param nowMillis the current time
     * @return {@code true} if the action is not valid any more
     */
    public boolean expired(final long nowMillis) {
        return nowMillis >= expiresAtMillis;
    }

    /**
     * Whether one node has to run this action.
     *
     * <p>The node that created the action is never addressed by it. It ran the
     * action itself, right when the administrator asked for it, and a second run
     * through the database would only duplicate it.</p>
     *
     * @param serverId the id of the node that asks
     * @param platform the platform of that node
     * @return {@code true} if this node has to run the action
     */
    public boolean addresses(final String serverId, final ModulePlatform platform) {
        final String node = serverId == null ? "" : serverId.trim().toLowerCase(Locale.ROOT);
        if (node.isEmpty() || node.equals(originServerId)) {
            return false;
        }
        return switch (target.kind()) {
            case ALL -> true;
            case PAPER -> platform == ModulePlatform.PAPER;
            case VELOCITY -> platform == ModulePlatform.VELOCITY;
            case SERVER -> node.equals(target.serverId());
        };
    }
}
