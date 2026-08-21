package net.managerhub.center.api;

/**
 * The platform a module was built for.
 *
 * <p>A module declares its platform in its metadata. Center2 only enables a
 * module on a platform the module actually supports.</p>
 */
public enum ModulePlatform {

    /** The module only runs on Paper. */
    PAPER,

    /** The module only runs on Velocity. */
    VELOCITY,

    /** The module runs on both platforms. */
    BOTH;

    /**
     * @param running the platform Center2 is running on
     * @return {@code true} if this module may be enabled there
     */
    public boolean supports(final ModulePlatform running) {
        return this == BOTH || this == running;
    }
}
