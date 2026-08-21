package net.managerhub.center.common.module;

/**
 * The state of one installed module.
 *
 * <p>This enum is the only source of truth for the state of a module. The loader,
 * the commands and the menu all read it, so an administrator sees the same state
 * everywhere.</p>
 */
public enum ModuleStatus {

    /** The module is running. */
    ENABLED,

    /** The module is installed but switched off by an administrator. */
    DISABLED,

    /** The module does not support the running MHCenter2 version. */
    INCOMPATIBLE_CENTER,

    /** The module does not support the running Minecraft version. */
    INCOMPATIBLE_MINECRAFT,

    /** The module failed while loading, starting or stopping and is not running. */
    ERROR;

    /** @return {@code true} if the module cannot run on this server as it is. */
    public boolean isIncompatible() {
        return this == INCOMPATIBLE_CENTER || this == INCOMPATIBLE_MINECRAFT;
    }
}
