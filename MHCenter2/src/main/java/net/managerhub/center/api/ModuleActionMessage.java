package net.managerhub.center.api;

import java.util.UUID;

/**
 * One network action a module received.
 *
 * <p>The action was written by the same module on another MHCenter2 node. MHCenter2
 * itself does not understand the type and never looks into the payload: it only
 * makes sure that the action belongs to this module, that it is not expired and
 * that this node runs it exactly once.</p>
 *
 * <p>The action type is a name the module chose. It is never a console command
 * and MHCenter2 will never execute it as one.</p>
 *
 * @param id       the id of the action, unique in the whole network
 * @param type     the action type the sending module chose
 * @param origin   {@code remote.server-id} of the node that sent the action
 * @param payload  the data of the module, never interpreted by MHCenter2
 */
public record ModuleActionMessage(UUID id, String type, String origin, byte[] payload) {

    public ModuleActionMessage {
        payload = payload == null ? new byte[0] : payload.clone();
    }

    /** @return a copy of the payload; changing it does not change the action. */
    @Override
    public byte[] payload() {
        return payload.clone();
    }
}
