package net.managerhub.center.api;

/**
 * What a command of a module does.
 *
 * <p>The module never registers a command on the server or on the proxy itself.
 * It hands the command to {@link ModuleContext#registerCommand}, so the safe
 * registration of Center2 applies: no command of the core and no command of
 * another plugin can be taken over.</p>
 */
@FunctionalInterface
public interface ModuleCommand {

    /**
     * @param sender who used the command
     */
    void execute(ModuleCommandSender sender);
}
