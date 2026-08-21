package net.managerhub.center.common.module;

/**
 * Where the module system writes what happened to a module.
 *
 * <p>Everything reported here belongs into the server console. Technical details
 * - exceptions, class names, stack traces, file paths - are written to the log
 * only and never into a Minecraft menu.</p>
 */
public interface ModuleReport {

    /**
     * A jar below {@code Modules/Jars} that is not a usable Center2 module at all.
     *
     * @param source file name of the jar
     * @param reason why the jar was skipped
     */
    void skipped(String source, String reason);

    /**
     * A module that does not support the running Center2 version.
     *
     * @param module  metadata of the module
     * @param running the running Center2 version
     */
    void incompatibleCenter(ModuleDescriptor module, String running);

    /**
     * A module that does not support the running Minecraft version.
     *
     * @param module  metadata of the module
     * @param running the running Minecraft version
     */
    void incompatibleMinecraft(ModuleDescriptor module, String running);

    /**
     * A module that stays off because an administrator switched it off.
     *
     * @param module metadata of the module
     */
    void administrativelyDisabled(ModuleDescriptor module);

    /**
     * The jar of an already loaded module was replaced on disk.
     *
     * @param module metadata of the loaded module
     * @param source file name of the jar
     */
    void jarChanged(ModuleDescriptor module, String source);

    /**
     * A module failed in one step of its lifecycle.
     *
     * @param module  metadata of the module
     * @param step    the step the module was in
     * @param reason  short description of what went wrong
     * @param failure the cause, may be {@code null} if there is no exception
     */
    void error(ModuleDescriptor module, ModuleLifecycle step, String reason, Throwable failure);

    /**
     * The folder with the module jars could not be read.
     *
     * <p>This is not the same as an empty folder and must never look like it.</p>
     *
     * @param directory the folder Center2 tried to read
     * @param failure   the cause
     */
    void scanFailed(String directory, Throwable failure);

    /**
     * The stored list of switched off modules could not be read.
     *
     * <p>Center2 does not know which modules an administrator switched off, so no
     * module is started automatically in this scan.</p>
     *
     * @param failure the cause
     */
    void stateUnreadable(Throwable failure);

    /**
     * The decision to switch a module on or off could not be stored.
     *
     * <p>The running server follows the decision, but a restart may not.</p>
     *
     * @param module   metadata of the module
     * @param disabled {@code true} if the module was switched off
     * @param failure  the cause
     */
    void statePersistFailed(ModuleDescriptor module, boolean disabled, Throwable failure);
}
