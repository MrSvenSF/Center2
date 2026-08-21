package net.managerhub.center.common.module;

/**
 * The step Center2 was doing with a module when something went wrong.
 *
 * <p>The step is only written into the server log, never into a menu.</p>
 */
public enum ModuleLifecycle {

    /** The jar was opened and the main class was created and prepared. */
    LOAD,

    /** The module was started. */
    ENABLE,

    /** The module was told that the configuration was reloaded. */
    RELOAD,

    /** The module was stopped. */
    DISABLE,

    /** A command of the module was registered. */
    COMMAND_REGISTRATION,

    /** A resource the module registered for cleanup was removed again. */
    CLEANUP
}
