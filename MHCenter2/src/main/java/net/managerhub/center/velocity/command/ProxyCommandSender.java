package net.managerhub.center.velocity.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.managerhub.center.api.ModuleCommandSender;

/**
 * The Velocity sender behind the platform neutral module command API.
 *
 * <p>A module never sees the Velocity type; it only ever gets
 * {@link ModuleCommandSender}, exactly like on Paper.</p>
 *
 * @param source the sender of the proxy
 */
public record ProxyCommandSender(CommandSource source) implements ModuleCommandSender {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    /** Name MHCenter2 uses for the proxy console. */
    private static final String CONSOLE = "CONSOLE";

    @Override
    public String name() {
        return source instanceof Player player ? player.getUsername() : CONSOLE;
    }

    @Override
    public boolean isPlayer() {
        return source instanceof Player;
    }

    @Override
    public boolean hasPermission(final String permission) {
        return source.hasPermission(permission);
    }

    @Override
    public void sendMessage(final String message) {
        source.sendMessage(MINI_MESSAGE.deserialize(message));
    }
}
