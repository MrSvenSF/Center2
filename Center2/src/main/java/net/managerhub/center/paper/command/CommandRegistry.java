package net.managerhub.center.paper.command;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import net.managerhub.center.common.language.Language;
import net.managerhub.center.common.language.MessageKey;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Registers and removes the Center2 commands at runtime.
 *
 * <p>Only public API is used: {@code Server#getCommandMap()} and
 * {@code CommandMap#getKnownCommands()} are part of the Paper API. There is no
 * reflection, no access to internal server structures and no NMS.</p>
 *
 * <p>Removal only touches entries that point to a command instance of Center2,
 * so a command of another plugin can never be removed by accident. After every
 * change the command tree is sent to the online players again, so tab completion
 * matches the new state immediately.</p>
 */
public final class CommandRegistry {

    private final Plugin plugin;
    private final String fallbackPrefix;
    private final List<Command> active = new ArrayList<>();

    /**
     * @param plugin         owning plugin
     * @param fallbackPrefix namespace used for the {@code namespace:command} form
     */
    public CommandRegistry(final Plugin plugin, final String fallbackPrefix) {
        this.plugin = plugin;
        this.fallbackPrefix = fallbackPrefix;
    }

    /**
     * Replaces every currently registered Center2 command by the given list.
     *
     * @param commands the new commands, may be empty
     * @param language texts of the configuration snapshot the commands belong to
     */
    public void apply(final List<Command> commands, final Language language) {
        requirePrimaryThread();
        final CommandMap commandMap = plugin.getServer().getCommandMap();
        removeAll(commandMap, language);
        for (final Command command : commands) {
            final boolean registered = commandMap.register(fallbackPrefix, command);
            if (registered) {
                hideNamespacedForm(commandMap, command);
            } else {
                // Another plugin owns the plain name, so the namespaced form stays
                // as the only way to reach this command.
                plugin.getLogger().warning(language.get(MessageKey.REGISTRY_NAME_TAKEN,
                        "command", command.getName(), "namespace", fallbackPrefix));
            }
            active.add(command);
        }
        updateOnlinePlayers();
    }

    /**
     * Removes every Center2 command from the server.
     *
     * @param language texts used for the log messages
     */
    public void unregisterAll(final Language language) {
        if (active.isEmpty()) {
            return;
        }
        removeAll(plugin.getServer().getCommandMap(), language);
        updateOnlinePlayers();
    }

    /** @return the number of currently registered Center2 commands. */
    public int size() {
        return active.size();
    }

    /**
     * Whether a command name belongs to another plugin.
     *
     * <p>The command map of Paper is the truth here, so a module can be told at
     * once that the name it wants is taken. A name Center2 registered itself is
     * not foreign: a module command like {@code center test} lives under the
     * {@code center} command of the core on purpose.</p>
     *
     * @param name the command name a module wants
     * @return {@code true} if another plugin already owns that name
     */
    public boolean takenByOtherPlugin(final String name) {
        final Command existing = plugin.getServer().getCommandMap()
                .getKnownCommands().get(name.toLowerCase(Locale.ROOT));
        return existing != null && !(existing instanceof CenterCommand);
    }

    /**
     * Drops the technical {@code namespace:command} entry of an own command.
     *
     * <p>Registering always creates that second entry. It is only a fallback for
     * the case that another plugin already owns the plain name, so as long as the
     * plain name belongs to Center2 the namespaced form is removed again and
     * players do not see it as a second command.</p>
     */
    private void hideNamespacedForm(final CommandMap commandMap, final Command command) {
        final String namespaced = fallbackPrefix.toLowerCase(Locale.ROOT) + ":"
                + command.getName().toLowerCase(Locale.ROOT);
        try {
            // Only an entry that really points at this command is touched, so a
            // command of another plugin can never be removed here.
            commandMap.getKnownCommands().remove(namespaced, command);
        } catch (final UnsupportedOperationException ignored) {
            // Leaving the fallback in place is harmless, the command still works.
        }
    }

    private void removeAll(final CommandMap commandMap, final Language language) {
        if (active.isEmpty()) {
            return;
        }
        final Set<Command> owned = identitySet(active);
        final Map<String, Command> known = commandMap.getKnownCommands();
        final List<String> obsolete = new ArrayList<>();
        for (final Map.Entry<String, Command> entry : known.entrySet()) {
            if (owned.contains(entry.getValue())) {
                obsolete.add(entry.getKey());
            }
        }
        for (final String label : obsolete) {
            final Command command = known.get(label);
            if (command == null || !owned.contains(command)) {
                continue;
            }
            try {
                known.remove(label, command);
            } catch (final UnsupportedOperationException failure) {
                plugin.getLogger().log(Level.WARNING,
                        language.get(MessageKey.REGISTRY_REMOVE_FAILED, "command", label), failure);
            }
        }
        for (final Command command : active) {
            command.unregister(commandMap);
        }
        active.clear();

        final List<String> leftovers = new ArrayList<>();
        for (final String label : obsolete) {
            final Command command = known.get(label);
            if (command != null && owned.contains(command)) {
                leftovers.add(label);
            }
        }
        if (!leftovers.isEmpty()) {
            plugin.getLogger().log(Level.WARNING, language.get(MessageKey.REGISTRY_REMOVE_LEFTOVERS,
                    "commands", String.join(", ", leftovers)));
        }
    }

    private void updateOnlinePlayers() {
        for (final Player player : plugin.getServer().getOnlinePlayers()) {
            player.updateCommands();
        }
    }

    private void requirePrimaryThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Center2 commands may only be changed on the main server thread.");
        }
    }

    private static Set<Command> identitySet(final List<Command> commands) {
        // Command does not override equals, so a normal set already compares by identity.
        return new LinkedHashSet<>(commands);
    }
}
