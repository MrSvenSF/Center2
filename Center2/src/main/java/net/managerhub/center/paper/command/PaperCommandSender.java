package net.managerhub.center.paper.command;

import net.managerhub.center.api.ModuleCommandSender;
import net.managerhub.center.paper.text.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * The Paper sender behind the platform neutral module command API.
 *
 * <p>A module never sees the Bukkit type; it only ever gets
 * {@link ModuleCommandSender}.</p>
 *
 * @param sender the sender of the platform
 */
public record PaperCommandSender(CommandSender sender) implements ModuleCommandSender {

    @Override
    public String name() {
        return sender.getName();
    }

    @Override
    public boolean isPlayer() {
        return sender instanceof Player;
    }

    @Override
    public boolean hasPermission(final String permission) {
        return sender.hasPermission(permission);
    }

    @Override
    public void sendMessage(final String message) {
        sender.sendMessage(Text.of(message));
    }
}
