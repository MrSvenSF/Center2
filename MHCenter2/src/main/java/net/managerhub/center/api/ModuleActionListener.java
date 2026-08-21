package net.managerhub.center.api;

/**
 * What a module does with a network action it received.
 *
 * <p>MHCenter2 calls this off the main server thread and off the event loop of the
 * proxy, so a module may read and write here. Everything that has to touch the
 * game world has to be handed to the scheduler of the platform by the module
 * itself.</p>
 */
@FunctionalInterface
public interface ModuleActionListener {

    /**
     * @param action the action this module received
     * @throws Exception if the action could not be carried out; MHCenter2 writes the
     *                   failure into the log and marks the action as failed for
     *                   this node, so it is not tried again in an endless loop
     */
    void onAction(ModuleActionMessage action) throws Exception;
}
