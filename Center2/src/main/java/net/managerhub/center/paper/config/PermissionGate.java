package net.managerhub.center.paper.config;

import org.bukkit.command.CommandSender;

/**
 * One complete authorization check of Center2.
 *
 * <p>A gate is the only thing a command, a menu or a button ever asks. It
 * already contains the whole rule, including the master permission and the
 * general admin barrier, so command and menu can never drift apart.</p>
 */
@FunctionalInterface
public interface PermissionGate {

    /**
     * @param sender sender to check
     * @return {@code true} if the sender may use the function behind this gate
     */
    boolean allows(CommandSender sender);
}
