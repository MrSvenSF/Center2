package net.managerhub.center.paper.config;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * One validated entry of {@code Permissions.yml}.
 *
 * <p>With {@code op: false} a player needs the permission itself; being an
 * operator is not enough. Bukkit would otherwise grant every unknown permission
 * to operators, which would let an operator walk past the MHCenter2 admin area
 * without ever being given {@code center.admin}.</p>
 *
 * <p>With {@code op: true} the normal platform behaviour applies, so an operator
 * may use the function without an explicit permission.</p>
 *
 * @param node permission node that is handed to the permission system of the server
 * @param op   whether being an operator alone is enough
 */
public record PermissionSetting(String node, boolean op) implements PermissionGate {

    /**
     * @param sender sender to check
     * @return {@code true} if the sender may use the function
     */
    @Override
    public boolean allows(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            // The console and command blocks are the server itself.
            return true;
        }
        if (op) {
            return player.hasPermission(node);
        }
        return player.isPermissionSet(node) && player.hasPermission(node);
    }
}
