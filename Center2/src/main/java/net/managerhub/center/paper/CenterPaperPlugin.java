package net.managerhub.center.paper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import net.managerhub.center.Center;
import net.managerhub.center.common.config.ConfigMigration;
import net.managerhub.center.common.config.ConfigurationException;
import net.managerhub.center.common.config.TransactionalConfiguration;
import net.managerhub.center.common.db.DatabaseException;
import net.managerhub.center.common.db.SqliteDatabase;
import net.managerhub.center.common.language.Language;
import net.managerhub.center.common.language.MessageKey;
import net.managerhub.center.api.ModulePlatform;
import net.managerhub.center.common.module.ModuleLoader;
import net.managerhub.center.common.remote.MariaDbRemoteStore;
import net.managerhub.center.common.remote.RemoteService;
import net.managerhub.center.common.remote.RemoteWorker;
import net.managerhub.center.common.util.DefaultFiles;
import net.managerhub.center.common.util.UptimeTracker;
import net.managerhub.center.paper.command.CommandRegistry;
import net.managerhub.center.paper.command.CommandService;
import net.managerhub.center.common.command.ModuleCommandRegistry;
import net.managerhub.center.paper.config.CenterConfiguration;
import net.managerhub.center.paper.config.CenterConfigurationLoader;
import net.managerhub.center.paper.menu.CenterMenu;
import net.managerhub.center.paper.menu.CreatorHead;
import net.managerhub.center.paper.menu.MenuListener;
import net.managerhub.center.paper.module.ModuleService;
import net.managerhub.center.paper.module.PaperModuleSupport;
import net.managerhub.center.paper.network.NetworkReloadClient;
import net.managerhub.center.paper.network.NetworkStatusClient;
import net.managerhub.center.paper.status.StatusService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Paper entry point of Center2.
 *
 * <p>The class only manages the lifecycle: it installs the default files,
 * initializes the services, registers the Paper specific parts and closes every
 * resource on shutdown. All logic lives in the services.</p>
 *
 * <p>If the SQLite database cannot be initialized, neither commands nor the menu
 * are registered and the plugin disables itself. Messages that are written before
 * a configuration exists cannot use the language files yet, so they stay in
 * English on purpose.</p>
 */
public final class CenterPaperPlugin extends JavaPlugin {

    /** Name of this platform, used in the lifecycle messages. */
    private static final String PLATFORM = "Paper";

    /** Namespace of the {@code namespace:command} form of every Center2 command. */
    private static final String COMMAND_NAMESPACE = "center2";

    private static final String PAPER_DEFAULTS = "defaults/paper/";
    private static final String LANGUAGE_DEFAULTS = "defaults/language/";

    private UptimeTracker uptime;
    private SqliteDatabase database;
    private TransactionalConfiguration<CenterConfiguration> configuration;
    private CommandService commands;
    private ModuleService modules;
    private RemoteService remote;
    private RemoteWorker remoteWorker;
    private NetworkReloadClient reloadClient;
    private Language language;

    @Override
    public void onEnable() {
        uptime = UptimeTracker.started();

        final Path dataFolder = getDataFolder().toPath();
        try {
            installDefaults(dataFolder);
        } catch (final IOException failure) {
            failStartup("The default configuration files could not be created: " + failure.getMessage());
            return;
        }

        database = new SqliteDatabase(dataFolder.resolve(Center.DATABASE_DIRECTORY).resolve(Center.DATABASE_FILE));
        try {
            database.initialize();
        } catch (final DatabaseException failure) {
            database = null;
            failStartup(failure.getMessage());
            return;
        }

        final CommandRegistry registry = new CommandRegistry(this, COMMAND_NAMESPACE);
        final StatusService status = new StatusService(uptime);
        final CreatorHead creatorHead = new CreatorHead();
        creatorHead.warmUp(this);

        // The optional remote system. It is only built here; nothing is connected
        // before the configuration was read and said so.
        remote = new RemoteService(ModulePlatform.PAPER,
                Bukkit.getMinecraftVersion(),
                PaperModuleSupport.coreLogger(this),
                this::currentLanguage,
                MariaDbRemoteStore::new,
                action -> reloadClient.applyRemoteReload(action),
                System::currentTimeMillis);
        remoteWorker = new RemoteWorker(remote, PaperModuleSupport.coreLogger(this), this::currentLanguage);

        final ModuleCommandRegistry moduleCommands = new ModuleCommandRegistry();
        final NetworkStatusClient network = new NetworkStatusClient(this, remote);
        modules = new ModuleService(this, moduleCommands, remote, network);
        final CenterMenu menu =
                new CenterMenu(this, this::currentConfiguration, status, creatorHead, network, modules);
        network.onUpdate(menu::refreshServerStatusMenus);

        commands = new CommandService(this, registry, menu,
                dataFolder.resolve(Center.COMMANDS_FILE), moduleCommands, modules);
        configuration = new TransactionalConfiguration<>(new CenterConfigurationLoader(dataFolder), commands);
        commands.bind(configuration);
        modules.bind(commands, () -> configuration.current().language());
        // A module command is only accepted when it does not collide with a
        // command of the core, so the registry has to know them.
        moduleCommands.reserve(commands::corePaths);
        // And a command name another plugin already owns can never be served.
        moduleCommands.platform(registry::takenByOtherPlugin);

        reloadClient = new NetworkReloadClient(this, network, remote, remoteWorker, this::currentLanguage);
        reloadClient.onLocalReload((origin, sender) -> commands.reloadLocally(sender));
        reloadClient.register();
        // A reload always covers the configuration files, the modules and the
        // remote system - no matter whether it started here or in the network.
        commands.bindNetwork(reloadClient, snapshot -> {
            modules.reloadModules();
            remoteWorker.apply(snapshot.remote());
        });

        getServer().getPluginManager().registerEvents(new MenuListener(menu), this);
        network.register();

        try {
            configuration.initialize();
        } catch (final ConfigurationException failure) {
            failStartup(failure.getMessage());
            return;
        }

        // A menu that is switched off also switches its command off in Commands.yml.
        commands.synchronizeCommandsFile();

        language = configuration.current().language();

        // Now that the texts exist, the remote system may say what it is doing.
        remoteWorker.apply(configuration.current().remote());

        // The modules are started last, so a module command is registered on top
        // of a configuration that is already complete and valid.
        modules.start(dataFolder, database);

        getLogger().info(language.get(MessageKey.PLUGIN_ENABLED,
                "product", Center.PRODUCT_NAME,
                "version", Center.VERSION,
                "platform", PLATFORM,
                "commands", Integer.toString(commands.registeredCommands())));
    }

    @Override
    public void onDisable() {
        if (remoteWorker != null) {
            // First: the remote thread stops and this node disappears from the
            // network list, so nobody sends it an action it can no longer answer.
            remoteWorker.close();
            remoteWorker = null;
            remote = null;
            reloadClient = null;
        }
        if (modules != null) {
            modules.stop();
            modules = null;
        }
        if (commands != null) {
            commands.unregisterAll();
            commands = null;
        }
        if (database != null) {
            database.close();
            database = null;
        }
        if (language != null) {
            getLogger().info(language.get(MessageKey.PLUGIN_DISABLED,
                    "product", Center.PRODUCT_NAME, "version", Center.VERSION, "platform", PLATFORM));
            language = null;
        }
    }

    private void installDefaults(final Path dataFolder) throws IOException {
        install(dataFolder.resolve(Center.MAIN_CONFIG_FILE), PAPER_DEFAULTS + Center.MAIN_CONFIG_FILE);
        install(dataFolder.resolve(Center.COMMANDS_FILE), PAPER_DEFAULTS + Center.COMMANDS_FILE);
        install(dataFolder.resolve(Center.PERMISSIONS_FILE), PAPER_DEFAULTS + Center.PERMISSIONS_FILE);
        for (final String menu : List.of(Center.CENTER_INFO_MENU_FILE,
                Center.CENTER_ADMIN_MENU_FILE, Center.SERVER_STATUS_MENU_FILE)) {
            install(dataFolder.resolve(Center.MENUS_DIRECTORY).resolve(menu),
                    PAPER_DEFAULTS + Center.MENUS_DIRECTORY + "/" + menu);
        }
        for (final String code : Language.SUPPORTED) {
            install(dataFolder.resolve(Center.LANGUAGE_DIRECTORY).resolve(Language.fileName(code)),
                    LANGUAGE_DEFAULTS + Language.fileName(code));
        }
        ModuleLoader.createDirectories(dataFolder.resolve(Center.MODULES_DIRECTORY));
    }

    /**
     * Makes sure the file exists and carries every entry this Center2 version
     * needs.
     *
     * <p>Messages here are written before a language file was read, so they stay
     * English on purpose.</p>
     */
    private void install(final Path target, final String resource) throws IOException {
        final DefaultFiles.Installation installation = DefaultFiles.install(target, resource);
        if (installation == DefaultFiles.Installation.REPAIRED) {
            getLogger().warning("'" + target.getFileName() + "' was empty and has been restored from the "
                    + Center.PRODUCT_NAME + " default.");
        }
        if (installation == DefaultFiles.Installation.KEPT) {
            // Only a file that was already there can be missing a new entry.
            migrate(target, resource);
        }
    }

    private void migrate(final Path target, final String resource) {
        final ConfigMigration.Result result;
        try {
            result = ConfigMigration.apply(target, resource);
        } catch (final IOException | RuntimeException failure) {
            getLogger().warning("'" + target.getFileName() + "' could not be brought up to the current "
                    + Center.PRODUCT_NAME + " default: " + failure.getMessage());
            return;
        }
        if (result.skippedNewer()) {
            getLogger().warning("'" + target.getFileName() + "' declares config-version " + result.fromVersion()
                    + ", but this " + Center.PRODUCT_NAME + " version only knows " + Center.CONFIG_VERSION
                    + ". The file was not changed.");
            return;
        }
        if (result.changed()) {
            getLogger().info("'" + target.getFileName() + "': added " + result.added().size()
                    + " new default entries " + result.added() + " and set config-version to "
                    + result.toVersion() + ". Existing values were kept.");
        }
    }

    private CenterConfiguration currentConfiguration() {
        return configuration.current();
    }

    /**
     * The texts that are active right now.
     *
     * <p>The remote system starts before the first successful reload can happen
     * and may already have to say something while the configuration is being
     * built, so a missing configuration answers with the texts that were valid at
     * startup instead of throwing.</p>
     */
    private Language currentLanguage() {
        if (configuration != null && configuration.isInitialized()) {
            return configuration.current().language();
        }
        return language;
    }

    private void failStartup(final String reason) {
        getLogger().severe(Center.PRODUCT_NAME + " could not start: " + reason);
        getLogger().severe("No commands and no menu are registered, " + Center.PRODUCT_NAME + " is disabled now.");
        getServer().getPluginManager().disablePlugin(this);
    }
}
