package net.managerhub.center.api;

/**
 * The log of one module.
 *
 * <p>Paper and Velocity log in different ways, so a module never talks to the
 * logger of the platform directly.</p>
 */
public interface ModuleLogger {

    /**
     * @param message normal information
     */
    void info(String message);

    /**
     * @param message something an administrator should look at
     */
    void warn(String message);

    /**
     * @param message  what went wrong
     * @param failure  the cause, may be {@code null}
     */
    void error(String message, Throwable failure);
}
