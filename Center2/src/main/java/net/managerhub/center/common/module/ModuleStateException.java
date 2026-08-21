package net.managerhub.center.common.module;

/**
 * The stored state of the modules could not be read or written.
 *
 * <p>This is deliberately not swallowed anywhere: "no module is switched off" and
 * "Center2 could not find out which modules are switched off" are two completely
 * different situations, and only the first one is safe to act on.</p>
 */
public class ModuleStateException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * @param message what went wrong, without any credentials or absolute paths
     * @param cause   the original failure
     */
    public ModuleStateException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
