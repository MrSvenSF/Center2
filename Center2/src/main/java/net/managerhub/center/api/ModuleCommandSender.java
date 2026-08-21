package net.managerhub.center.api;

/**
 * Who used a command of a module.
 *
 * <p>The type is platform neutral on purpose: a module for Velocity must never
 * touch a Paper class, and a module for Paper must never touch a Velocity class.
 * Center2 wraps the sender of the platform behind this small interface.</p>
 */
public interface ModuleCommandSender {

    /** @return the visible name of the sender, or {@code CONSOLE} for the server itself. */
    String name();

    /** @return {@code true} if a player used the command, {@code false} for the console. */
    boolean isPlayer();

    /**
     * @param permission permission node to check
     * @return {@code true} if the sender has it; the console always has it
     */
    boolean hasPermission(String permission);

    /**
     * Sends one line back to the sender.
     *
     * <p>The text is read as Adventure MiniMessage, exactly like every other
     * Center2 text, so {@code <green>done} works on both platforms. Text that
     * comes from outside - a player name, a file name - should be escaped by the
     * module before it is put into the message.</p>
     *
     * @param message the line to send
     */
    void sendMessage(String message);
}
