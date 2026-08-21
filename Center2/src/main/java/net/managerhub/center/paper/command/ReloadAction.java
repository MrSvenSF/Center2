package net.managerhub.center.paper.command;

import net.managerhub.center.common.language.Language;
import org.bukkit.command.CommandSender;

/**
 * Performs the transactional reload of every Center2 configuration file and
 * reports the result to the sender.
 */
@FunctionalInterface
public interface ReloadAction {

    /**
     * @param sender   sender of {@code /center reload}
     * @param language texts of the configuration that is active right now
     */
    void performReload(CommandSender sender, Language language);
}
