package net.managerhub.center.paper.module;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.managerhub.center.Center;
import net.managerhub.center.api.ModulePlatform;
import net.managerhub.center.common.command.ModuleCommandRegistry;
import net.managerhub.center.common.language.Language;
import net.managerhub.center.common.language.MessageKey;
import net.managerhub.center.common.module.LoggingModuleReport;
import net.managerhub.center.common.module.ModuleAdministration;
import net.managerhub.center.common.module.ModuleLoader;
import net.managerhub.center.common.module.ModuleStateStore;
import net.managerhub.center.common.module.ModuleStatus;
import net.managerhub.center.common.remote.RemoteService;
import net.managerhub.center.common.remote.ModuleActionFallback;
import net.managerhub.center.common.util.Version;
import net.managerhub.center.paper.command.CommandService;
import net.managerhub.center.paper.command.ModuleAdminAction;
import net.managerhub.center.paper.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

/**
 * The module administration of Center2 on Paper.
 *
 * <p>Commands and menu take exactly this way, so both always see the same state.
 * The service owns the {@link ModuleLoader}, keeps the registered module commands
 * in step with it and hands the answers to the shared
 * {@link ModuleAdministration}, so Paper and the proxy really answer the same
 * way. Technical error details never leave the server console.</p>
 *
 * <p>If the module system could not be started at all - on Paper that happens
 * when the Minecraft version cannot be determined - the service stays
 * unavailable. That is deliberately not the same answer as "no module is
 * installed".</p>
 */
public final class ModuleService implements ModuleAdminAction, ModuleAdministration.Modules {

    private final Plugin plugin;
    private final ModuleCommandRegistry moduleCommands;
    private final RemoteService remote;
    private final ModuleActionFallback fallback;

    private CommandService commands;
    private Supplier<Language> language;
    private ModuleLoader loader;
    private Path jarsDirectory;
    private Path configsDirectory;

    /**
     * @param plugin         the Center2 plugin
     * @param moduleCommands where the commands of the modules are collected
     * @param remote         the optional remote system of this node
     */
    public ModuleService(final Plugin plugin,
                         final ModuleCommandRegistry moduleCommands,
                         final RemoteService remote,
                         final ModuleActionFallback fallback) {
        this.plugin = plugin;
        this.moduleCommands = moduleCommands;
        this.remote = remote;
        this.fallback = fallback;
    }

    /**
     * Connects the service with the parts that only exist after the configuration
     * was built.
     *
     * @param commands the command registration of Center2
     * @param language the texts of the currently active configuration
     */
    public void bind(final CommandService commands, final Supplier<Language> language) {
        this.commands = commands;
        this.language = language;
    }

    /**
     * Builds the loader and reads {@code Modules/Jars} for the first time.
     *
     * @param dataFolder the Center2 folder of this server
     * @param states     where the switched off modules are remembered
     */
    public void start(final Path dataFolder, final ModuleStateStore states) {
        final Path modulesFolder = dataFolder.resolve(Center.MODULES_DIRECTORY);
        this.jarsDirectory = modulesFolder.resolve(Center.MODULE_JARS_DIRECTORY);
        this.configsDirectory = modulesFolder.resolve(Center.MODULE_CONFIGS_DIRECTORY);

        // The Minecraft range of a module names Minecraft versions, so the real
        // game version has to be compared, not the Bukkit API version string.
        final String running = Bukkit.getMinecraftVersion();
        final Optional<Version> minecraft = Version.of(running);
        if (minecraft.isEmpty()) {
            // Fail closed: an unknown version means "compatibility not confirmed",
            // never "no check needed". The module system stays unavailable.
            plugin.getLogger().severe(language.get().get(MessageKey.MODULE_ENVIRONMENT_UNKNOWN,
                    "product", Center.PRODUCT_NAME, "version", String.valueOf(running)));
            return;
        }
        plugin.getLogger().info(language.get().get(MessageKey.MODULE_ENVIRONMENT,
                "product", Center.PRODUCT_NAME, "version", minecraft.get().display()));

        final LoggingModuleReport report =
                new LoggingModuleReport(PaperModuleSupport.coreLogger(plugin), language);
        this.loader = new ModuleLoader(ModulePlatform.PAPER,
                Version.of(Center.VERSION).orElseThrow(),
                minecraft,
                new PaperModuleSupport(plugin, moduleCommands, report, remote, fallback),
                states,
                report);

        scan();
    }

    /**
     * Tells every running module that the configuration was reloaded.
     *
     * <p>Part of {@code /center reload}. No jar is read again and no module is
     * created a second time; the instances that are running only get the chance
     * to read their own configuration again.</p>
     */
    public void reloadModules() {
        if (loader == null) {
            return;
        }
        final int reloaded = loader.reloadModules();
        // A module that failed the reload was stopped, so its commands have to go
        // with it.
        synchronizeCommands();
        plugin.getLogger().info(language.get().get(MessageKey.MODULE_RELOADED,
                "modules", Integer.toString(reloaded)));
    }

    /** Stops every running module. Belongs to the shutdown of the server. */
    public void stop() {
        if (loader != null) {
            loader.disableAll();
            loader = null;
        }
        moduleCommands.clear();
    }

    /**
     * @param moduleId id of the module, upper and lower case are the same id
     * @return the installed module, or empty if no such module is installed
     */
    public Optional<ModuleLoader.InstalledModule> module(final String moduleId) {
        return loader == null ? Optional.empty() : loader.module(moduleId);
    }

    /** @return every installed module with its current state, sorted by id. */
    public List<ModuleLoader.InstalledModule> modules() {
        return installed();
    }

    @Override
    public boolean available() {
        return loader != null;
    }

    @Override
    public List<ModuleLoader.InstalledModule> installed() {
        return loader == null ? List.of() : loader.modules();
    }

    @Override
    public int enabledCount() {
        return loader == null ? 0 : loader.enabledCount();
    }

    @Override
    public void reload() {
        if (loader != null) {
            scan();
        }
    }

    @Override
    public Optional<ModuleStatus> enable(final String moduleId) {
        if (loader == null) {
            return Optional.empty();
        }
        final Optional<ModuleStatus> status = loader.enable(moduleId);
        status.ifPresent(ignored -> synchronizeCommands());
        return status;
    }

    @Override
    public Optional<ModuleStatus> disable(final String moduleId) {
        if (loader == null) {
            return Optional.empty();
        }
        final Optional<ModuleStatus> status = loader.disable(moduleId);
        status.ifPresent(ignored -> synchronizeCommands());
        return status;
    }

    @Override
    public List<String> moduleIds() {
        return ModuleAdministration.moduleIds(this);
    }

    @Override
    public void listModules(final CommandSender sender, final Language texts) {
        ModuleAdministration.list(this, texts, Text::escape, send(sender));
    }

    @Override
    public void reloadModules(final CommandSender sender, final Language texts) {
        ModuleAdministration.reload(this, texts, send(sender));
    }

    @Override
    public void enableModule(final CommandSender sender,
                             final Language texts,
                             final String moduleId,
                             final String commandPath) {
        ModuleAdministration.enable(this, texts, Text::escape, moduleId, commandPath, send(sender));
    }

    @Override
    public void disableModule(final CommandSender sender,
                              final Language texts,
                              final String moduleId,
                              final String commandPath) {
        ModuleAdministration.disable(this, texts, Text::escape, moduleId, commandPath, send(sender));
    }

    private static Consumer<String> send(final CommandSender sender) {
        return message -> sender.sendMessage(Text.of(message));
    }

    /** Reads the jar folder and writes the result into the server log. */
    private void scan() {
        loader.refresh(jarsDirectory, configsDirectory);
        synchronizeCommands();
        plugin.getLogger().info(language.get().get(MessageKey.MODULE_LOADED,
                "installed", Integer.toString(loader.modules().size()),
                "enabled", Integer.toString(loader.enabledCount())));
    }

    /**
     * Drops the commands of every module that is not running and registers the
     * remaining commands again.
     *
     * <p>A module that failed while starting may already have registered a
     * command, and a module that was switched off must not answer any longer.</p>
     */
    private void synchronizeCommands() {
        for (final ModuleLoader.InstalledModule module : loader.modules()) {
            if (module.status() != ModuleStatus.ENABLED) {
                moduleCommands.unregisterModule(module.descriptor().id());
            }
        }
        if (commands != null) {
            commands.refresh();
        }
    }
}
