package net.managerhub.center.api;

import java.util.Locale;
import java.util.Objects;

/**
 * Which MHCenter2 nodes a module action is meant for.
 *
 * <p>A target is not a routing table. It only says who should look at the action;
 * every node decides for itself whether it is addressed and then runs the action
 * exactly once.</p>
 */
public final class ModuleActionTarget {

    /** The kind of a target. */
    public enum Kind {

        /** Every MHCenter2 node of the network, the proxy included. */
        ALL,

        /** Every Paper node of the network. */
        PAPER,

        /** Every Velocity node of the network. */
        VELOCITY,

        /** Exactly one node, named by its {@code remote.server-id}. */
        SERVER
    }

    /** Every MHCenter2 node of the network. */
    public static final ModuleActionTarget ALL = new ModuleActionTarget(Kind.ALL, "");

    /** Every Paper node of the network. */
    public static final ModuleActionTarget PAPER = new ModuleActionTarget(Kind.PAPER, "");

    /** Every Velocity node of the network. */
    public static final ModuleActionTarget VELOCITY = new ModuleActionTarget(Kind.VELOCITY, "");

    private final Kind kind;
    private final String serverId;

    private ModuleActionTarget(final Kind kind, final String serverId) {
        this.kind = kind;
        this.serverId = serverId;
    }

    /**
     * @param serverId the {@code remote.server-id} of the wanted node
     * @return a target for exactly that node
     * @throws IllegalArgumentException if the id is empty
     */
    public static ModuleActionTarget server(final String serverId) {
        if (serverId == null || serverId.isBlank()) {
            throw new IllegalArgumentException("A server target needs the server-id of the wanted node.");
        }
        return new ModuleActionTarget(Kind.SERVER, serverId.trim().toLowerCase(Locale.ROOT));
    }

    /** @return the kind of this target. */
    public Kind kind() {
        return kind;
    }

    /** @return the addressed server id, empty for every kind but {@link Kind#SERVER}. */
    public String serverId() {
        return serverId;
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof ModuleActionTarget target
                && kind == target.kind
                && serverId.equals(target.serverId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, serverId);
    }

    @Override
    public String toString() {
        return kind == Kind.SERVER ? Kind.SERVER + ":" + serverId : kind.name();
    }
}
