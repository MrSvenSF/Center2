package net.managerhub.center.api;

/**
 * The contract every Center2 module implements.
 *
 * <p>A module is a normal jar below {@code Modules/Jars}. It carries a
 * {@code center-module.properties} that names the class implementing this
 * interface. The class needs a public constructor without arguments.</p>
 *
 * <p>Center2 calls the lifecycle methods in this order and never in parallel:
 * {@link #onLoad(ModuleContext)}, {@link #onEnable()}, then {@link #onReload()}
 * on every configuration reload, and {@link #onDisable()} when the module is
 * switched off or the server shuts down. A module that throws is reported and
 * skipped; the core and the other modules keep running.</p>
 */
public interface CenterModule {

    /**
     * Hands over the context. Nothing of the module is active yet, so this is
     * the place to remember the context and to read the own configuration.
     *
     * @param context the context of this module
     * @throws Exception if the module cannot be prepared
     */
    void onLoad(ModuleContext context) throws Exception;

    /**
     * Starts the module. Commands and listeners belong here.
     *
     * @throws Exception if the module cannot be started
     */
    void onEnable() throws Exception;

    /**
     * Tells the module that the configuration of Center2 was reloaded.
     *
     * <p>Center2 calls this on every running module when an administrator uses
     * {@code /center reload}, no matter whether that reload started on this
     * server or somewhere else in the network. It is the place to read the own
     * configuration again, to drop a cache and to apply changed settings.</p>
     *
     * <p>This never exchanges the jar of the module: the classes that are running
     * stay exactly the ones that were loaded at startup. A changed module binary
     * still needs a restart of the server.</p>
     *
     * <p>The method has a default implementation, so a module that has nothing to
     * reload simply leaves it out. A module that throws here is reported and
     * stopped, because Center2 cannot know what part of its configuration was
     * applied.</p>
     *
     * @throws Exception if the module cannot apply its configuration again
     */
    default void onReload() throws Exception {
    }

    /**
     * Stops the module again. Center2 calls this once for every module that was
     * enabled successfully.
     *
     * @throws Exception if the module cannot be stopped cleanly
     */
    void onDisable() throws Exception;
}
