package net.managerhub.center.paper.command;

import java.util.List;

import net.managerhub.center.common.language.Language;
import org.bukkit.command.CommandSender;

/**
 * The module administration behind {@code /center modules ...}.
 *
 * <p>The commands only collect the input and hand it over; what happens with a
 * module is decided by the module service alone, so command and menu always take
 * the same way.</p>
 */
public interface ModuleAdminAction {

    /**
     * Sends the list of every installed module.
     *
     * @param sender   who asked
     * @param language texts of the active configuration
     */
    void listModules(CommandSender sender, Language language);

    /**
     * Reads the module folder again and starts every new module that may run.
     *
     * @param sender   who asked
     * @param language texts of the active configuration
     */
    void reloadModules(CommandSender sender, Language language);

    /**
     * Starts one installed module.
     *
     * @param sender      who asked
     * @param language    texts of the active configuration
     * @param moduleId    id of the module, empty if it was not given
     * @param commandPath the configured path the sender used, for the usage hint
     */
    void enableModule(CommandSender sender, Language language, String moduleId, String commandPath);

    /**
     * Stops one installed module.
     *
     * @param sender      who asked
     * @param language    texts of the active configuration
     * @param moduleId    id of the module, empty if it was not given
     * @param commandPath the configured path the sender used, for the usage hint
     */
    void disableModule(CommandSender sender, Language language, String moduleId, String commandPath);

    /** @return the ids of every installed module, used for tab completion. */
    List<String> moduleIds();
}
