package net.managerhub.center.api;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Everything a MHCenter2 module gets from the core.
 *
 * <p>The context is handed over once in {@link CenterModule#onLoad(ModuleContext)}
 * and is the same on every platform. A module for Velocity therefore never loads
 * a Paper class through its MHCenter2 API, and the other way round.</p>
 */
public interface ModuleContext {

    /** @return the id of this module, taken from its metadata. */
    String moduleId();

    /**
     * The own folder of this module below {@code Modules/Configs}.
     *
     * <p>MHCenter2 never creates files there. A module that needs a configuration
     * creates the folder and its files itself, a module that needs nothing
     * leaves the folder out.</p>
     *
     * @return the folder this module may use for its own files
     */
    Path configDirectory();

    /** @return the log of this module. */
    ModuleLogger logger();

    /**
     * The platform this module is running on right now.
     *
     * <p>For a module with {@code platform=BOTH} this is the only correct way to
     * find out where it is: it answers {@link ModulePlatform#PAPER} or
     * {@link ModulePlatform#VELOCITY}, never {@code BOTH}.</p>
     *
     * @return the platform MHCenter2 is running on
     */
    ModulePlatform platform();

    /**
     * Hands MHCenter2 an action that removes one resource of this module again.
     *
     * <p>MHCenter2 only knows about the things it hands out itself. Everything a
     * module registers on its own - a listener, a scheduled task, an open file, a
     * thread - is removed by exactly this action. MHCenter2 runs it when the module
     * is stopped, when {@code onLoad} or {@code onEnable} fails, and when the
     * server shuts down; the newest action runs first.</p>
     *
     * <p>A module should register the action right where it creates the resource,
     * so a failure in the middle of {@code onEnable} still leaves nothing
     * behind.</p>
     *
     * @param cleanup what removes the resource again
     */
    void registerCleanup(Runnable cleanup);

    /**
     * Registers a command of this module.
     *
     * <p>The path is a complete command path such as {@code center test}, the
     * same notation the command configuration of MHCenter2 uses. The first segment
     * becomes the command that is registered on the platform, every further
     * segment is a fixed argument in front of it.</p>
     *
     * <p>The answer is honest: {@code true} means the command really is part of
     * the MHCenter2 command tree from now on. It is refused when the path is
     * invalid, when it belongs to a MHCenter2 core command or one of its aliases,
     * when another module already uses it, or when another plugin owns the
     * command name. The reason is written to the server log.</p>
     *
     * <p>MHCenter2 removes the command again when the module is stopped or fails,
     * so a module does not have to register a cleanup for it.</p>
     *
     * @param path    complete command path, without a leading slash
     * @param command what the command does
     * @return {@code true} if the command was accepted
     */
    boolean registerCommand(String path, ModuleCommand command);

    /**
     * What this module may do in the MHCenter2 network.
     *
     * <p>Always there, also when the remote database is switched off; in that
     * case {@link ModuleNetwork#available()} answers {@code false}. The object
     * belongs to this module: its storage and its actions live in the namespace
     * of this module id.</p>
     *
     * @return the network access of this module
     */
    ModuleNetwork network();

    /**
     * Asks for an additional service of the running platform.
     *
     * <p>This is the one place where a module may leave the platform neutral
     * part of the API. On the proxy MHCenter2 offers
     * {@code net.managerhub.center.api.velocity.VelocityModuleApi} here, which is
     * how a {@code VELOCITY} module reaches Velocity events, the proxy scheduler,
     * the backend servers and the server list ping. On Paper that service does
     * not exist and the answer is empty.</p>
     *
     * <p>Nothing of the platform is in the signature, so asking is always safe.
     * A module with {@code platform=BOTH} must still keep the code that uses a
     * platform service in its own class and must only reach that class after
     * {@link #platform()} confirmed the platform - otherwise the class loader
     * would look for a Velocity class while the module is starting on Paper.</p>
     *
     * @param type the wanted service interface
     * @param <T>  type of the service
     * @return the service, or empty if this platform does not offer it
     */
    <T> Optional<T> service(Class<T> type);
}
