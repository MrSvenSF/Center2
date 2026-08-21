package net.managerhub.center.paper.command;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;

import net.managerhub.center.Center;
import net.managerhub.center.common.command.CommandConflicts;
import net.managerhub.center.common.command.CommandPath;
import net.managerhub.center.common.command.CommandSpec;
import net.managerhub.center.common.command.CommandsDefinition;
import net.managerhub.center.common.command.ModuleCommandRegistry;
import net.managerhub.center.common.config.ConfigurationException;
import net.managerhub.center.common.config.TransactionalConfiguration;
import net.managerhub.center.common.language.Language;
import net.managerhub.center.common.language.MessageKey;
import net.managerhub.center.paper.config.CenterConfiguration;
import net.managerhub.center.paper.config.CommandsFileWriter;
import net.managerhub.center.paper.config.PermissionGate;
import net.managerhub.center.paper.config.PermissionsSettings;
import net.managerhub.center.paper.menu.CenterMenu;
import net.managerhub.center.paper.network.NetworkReloadClient;
import net.managerhub.center.paper.text.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

/**
 * Turns a configuration snapshot into Bukkit commands and swaps them at runtime.
 *
 * <p>This class is the activator of the transactional configuration: it is only
 * called with a snapshot that is already fully validated. Because the reload is
 * triggered by a command that this service registers itself, the running
 * transaction is handed over once through {@link #bind}.</p>
 *
 * <p>The reload command is not configurable. It is added here as a fixed system
 * command, so it exists no matter what {@code Commands.yml} contains.</p>
 */
public final class CommandService implements TransactionalConfiguration.Activator<CenterConfiguration>, ReloadAction {

    private final Plugin plugin;
    private final CommandRegistry registry;
    private final CenterMenu menu;
    private final Path commandsFile;
    private final ModuleCommandRegistry moduleCommands;
    private final ModuleAdminAction moduleAdmin;
    private TransactionalConfiguration<CenterConfiguration> configuration;
    private Language activeLanguage;

    /** What else belongs to a reload: the modules and the remote system. */
    private Consumer<CenterConfiguration> afterReload = snapshot -> { };

    /** How this server reaches the rest of the network. */
    private NetworkReloadClient network = null;

    public CommandService(final Plugin plugin,
                          final CommandRegistry registry,
                          final CenterMenu menu,
                          final Path commandsFile,
                          final ModuleCommandRegistry moduleCommands,
                          final ModuleAdminAction moduleAdmin) {
        this.plugin = plugin;
        this.registry = registry;
        this.menu = menu;
        this.commandsFile = commandsFile;
        this.moduleCommands = moduleCommands;
        this.moduleAdmin = moduleAdmin;
    }

    /**
     * Registers the commands again, so commands the modules added while starting
     * become usable.
     */
    public void refresh() {
        try {
            activate(configuration.current());
        } catch (final ConfigurationException conflict) {
            // A module command is checked against the core paths the moment it is
            // registered, so this cannot normally happen. If it ever does, the
            // running command tree stays as it is and the reason is in the log.
            plugin.getLogger().log(Level.WARNING, conflict.getMessage());
        }
    }

    /**
     * Connects the service with the transaction it belongs to.
     *
     * @param configuration transactional configuration of the plugin
     */
    public void bind(final TransactionalConfiguration<CenterConfiguration> configuration) {
        this.configuration = configuration;
    }

    /**
     * Connects the reload with the parts of Center2 that are not configuration
     * files.
     *
     * @param network     how this server reaches the rest of the network
     * @param afterReload what else has to follow a successful reload
     */
    public void bindNetwork(final NetworkReloadClient network,
                            final Consumer<CenterConfiguration> afterReload) {
        this.network = network;
        this.afterReload = afterReload;
    }

    @Override
    public void activate(final CenterConfiguration snapshot) throws ConfigurationException {
        rejectModuleCommandConflicts(snapshot);
        this.activeLanguage = snapshot.language();
        registry.apply(build(snapshot), snapshot.language());
    }

    /**
     * Refuses a command configuration that would take a path away from a running
     * module.
     *
     * <p>Without this check a reload would look successful and the command of a
     * module would simply be gone afterwards, with nothing but a warning in the
     * log. The whole new command configuration is refused instead, the
     * transaction rolls back to the last working one, and the administrator gets
     * a message that names the path and the module.</p>
     *
     * <p>Disabled commands count as well: their path belongs to the core and
     * would come back with the next reload.</p>
     */
    private void rejectModuleCommandConflicts(final CenterConfiguration snapshot) throws ConfigurationException {
        final Set<String> corePaths =
                CommandConflicts.corePaths(CommandsDefinition.SYSTEM_PATHS, snapshot.commands());
        final Optional<ModuleCommandRegistry.Registered> swallowed =
                CommandConflicts.swallowedModuleCommand(corePaths, moduleCommands.all());
        if (swallowed.isPresent()) {
            throw new ConfigurationException(Center.COMMANDS_FILE + ": the command path \""
                    + swallowed.get().path().display() + "\" is already used by the running module '"
                    + swallowed.get().moduleId() + "'. A command of " + Center.PRODUCT_NAME
                    + " may not take a path away from a module that is running. Choose another path, "
                    + "or switch that module off first.");
        }
    }

    @Override
    public void performReload(final CommandSender sender, final Language language) {
        if (reloadLocally(sender) && network != null) {
            network.spread(sender);
        }
    }

    /**
     * Reloads the configuration of this server only.
     *
     * <p>This is the one reload pipeline of Center2 on Paper. It is used by
     * {@code /center reload} and by a reload that arrived from the network, so
     * both really do the same thing: the configuration files, the commands, the
     * permissions, the texts, the menus, the remote settings and
     * {@code onReload()} of every running module.</p>
     *
     * <p>No module jar is read again. A changed module binary still needs a
     * restart of the server.</p>
     *
     * @param sender who should see the answer, {@code null} for a reload that
     *               came from the network
     * @return {@code true} if the new configuration is active now
     */
    public boolean reloadLocally(final CommandSender sender) {
        final Language language = configuration.current().language();
        try {
            configuration.reload();
        } catch (final ConfigurationException failure) {
            final String reason = Text.escape(String.valueOf(failure.getMessage()));
            if (sender != null) {
                sender.sendMessage(Text.of(language.get(MessageKey.RELOAD_FAILED, "reason", reason)));
                sender.sendMessage(Text.of(language.get(MessageKey.RELOAD_PREVIOUS_ACTIVE)));
            }
            plugin.getLogger().log(Level.WARNING, language.get(MessageKey.RELOAD_LOG_FAILED,
                    "product", Center.PRODUCT_NAME, "reason", String.valueOf(failure.getMessage())));
            return false;
        }

        final CenterConfiguration snapshot = configuration.current();
        synchronizeCommandsFile();
        // Everything that is not a configuration file but still belongs to a
        // reload: the modules and the remote system.
        afterReload.accept(snapshot);

        final String commands = Integer.toString(registry.size());
        if (sender != null) {
            sender.sendMessage(Text.of(snapshot.language().get(MessageKey.RELOAD_SUCCESS,
                    "product", Center.PRODUCT_NAME, "commands", commands)));
        }
        plugin.getLogger().info(snapshot.language().get(MessageKey.RELOAD_LOG_SUCCESS,
                "product", Center.PRODUCT_NAME, "commands", commands));
        return true;
    }

    /**
     * Writes {@code enabled: false} into {@code Commands.yml} for every command
     * whose menu is switched off in {@code MainConfig.yml}.
     *
     * <p>Only {@code false} is ever written. A command that an administrator
     * switched off on purpose is never switched on again, not even when its menu
     * is switched on again.</p>
     */
    public void synchronizeCommandsFile() {
        final CenterConfiguration snapshot = configuration.current();
        if (snapshot.centerInfoMenuEnabled()) {
            return;
        }
        final Language language = snapshot.language();
        try {
            if (CommandsFileWriter.disableCommand(commandsFile, CommandsDefinition.CENTER_INFO_KEY)) {
                plugin.getLogger().info(language.get(MessageKey.COMMANDS_FILE_DISABLED,
                        "menu", CommandsDefinition.CENTER_INFO_KEY,
                        "command", CommandsDefinition.CENTER_INFO_KEY,
                        "file", Center.COMMANDS_FILE));
            }
        } catch (final IOException failure) {
            plugin.getLogger().warning(language.get(MessageKey.COMMANDS_FILE_DISABLE_FAILED,
                    "command", CommandsDefinition.CENTER_INFO_KEY,
                    "file", Center.COMMANDS_FILE,
                    "reason", String.valueOf(failure.getMessage())));
        }
    }

    /** Removes every Center2 command from the server. */
    public void unregisterAll() {
        if (activeLanguage == null) {
            // Nothing was ever registered, so there is nothing to remove or to report.
            return;
        }
        registry.unregisterAll(activeLanguage);
    }

    /** @return the number of currently registered Center2 commands. */
    public int registeredCommands() {
        return registry.size();
    }

    /**
     * Every command path Center2 itself owns.
     *
     * <p>The module command registry asks this before it accepts a path, so a
     * module can never be told that a core path was accepted. Paths of commands
     * that are switched off count as well: they belong to the core and may come
     * back with the next reload.</p>
     *
     * @return the configured paths with their aliases and the fixed system paths
     */
    public Set<String> corePaths() {
        final Set<String> paths = new LinkedHashSet<>();
        for (final CommandPath system : CommandsDefinition.SYSTEM_PATHS) {
            paths.add(system.display());
        }
        if (configuration == null || !configuration.isInitialized()) {
            return Set.copyOf(paths);
        }
        for (final CommandSpec spec : configuration.current().commands().commands()) {
            for (final CommandPath path : spec.allPaths()) {
                paths.add(path.display());
            }
        }
        return Set.copyOf(paths);
    }

    private List<Command> build(final CenterConfiguration snapshot) {
        final Map<String, List<CenterCommand.Route>> byName = new LinkedHashMap<>();
        final Set<String> taken = new LinkedHashSet<>();

        for (final CommandSpec spec : snapshot.commands().enabledCommands()) {
            final Optional<PermissionGate> gate = gate(spec.key(), snapshot.permissions());
            for (final CommandPath path : spec.allPaths()) {
                taken.add(path.display());
                byName.computeIfAbsent(path.rootName(), name -> new ArrayList<>())
                        .add(CenterCommand.Route.core(spec.key(), path.tail(), gate));
            }
        }
        // The system commands are fixed: they exist even when Commands.yml is empty.
        system(byName, taken, CommandsDefinition.RELOAD_KEY, CommandsDefinition.RELOAD_PATH,
                snapshot.permissions().reloadGate());
        system(byName, taken, CommandsDefinition.MODULES_KEY, CommandsDefinition.MODULES_PATH,
                snapshot.permissions().modulesGate());

        // Commands of the modules come last, so they can never take a path of the core.
        for (final ModuleCommandRegistry.Registered command : moduleCommands.all()) {
            if (!taken.add(command.path().display())) {
                plugin.getLogger().warning(snapshot.language().get(MessageKey.MODULE_COMMAND_CONFLICT,
                        "module", command.moduleId(), "path", command.path().display()));
                continue;
            }
            byName.computeIfAbsent(command.path().rootName(), name -> new ArrayList<>())
                    .add(CenterCommand.Route.module(command.moduleId(), command.path().tail(), command.command()));
        }

        final List<Command> commands = new ArrayList<>();
        byName.forEach((name, routes) -> commands.add(
                new CenterCommand(plugin, name, snapshot.language(), routes, menu, this, moduleAdmin)));
        return commands;
    }

    /** Adds one fixed system command that is not part of {@code Commands.yml}. */
    private static void system(final Map<String, List<CenterCommand.Route>> byName,
                               final Set<String> taken,
                               final String key,
                               final CommandPath path,
                               final PermissionGate gate) {
        taken.add(path.display());
        byName.computeIfAbsent(path.rootName(), name -> new ArrayList<>())
                .add(CenterCommand.Route.core(key, path.tail(), Optional.of(gate)));
    }

    /**
     * The one place that says what a configurable command requires.
     *
     * @return the gate of that command, empty for a command everybody may use
     */
    private static Optional<PermissionGate> gate(final String key, final PermissionsSettings permissions) {
        return switch (key) {
            case CommandsDefinition.MODULES_RELOAD_KEY -> Optional.of(permissions.modulesReloadGate());
            case CommandsDefinition.MODULES_ENABLE_KEY -> Optional.of(permissions.modulesEnableGate());
            case CommandsDefinition.MODULES_DISABLE_KEY -> Optional.of(permissions.modulesDisableGate());
            // The Center-Info command is public, exactly as it was before.
            default -> Optional.empty();
        };
    }
}
