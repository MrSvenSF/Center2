package net.managerhub.center.velocity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.managerhub.center.Center;
import net.managerhub.center.api.ModulePlatform;
import net.managerhub.center.common.command.ModuleCommandRegistry;
import net.managerhub.center.common.config.ConfigMigration;
import net.managerhub.center.common.config.ConfigurationException;
import net.managerhub.center.common.db.DatabaseException;
import net.managerhub.center.common.db.SqliteDatabase;
import net.managerhub.center.common.language.Language;
import net.managerhub.center.common.language.MessageKey;
import net.managerhub.center.common.module.ModuleLoader;
import net.managerhub.center.common.network.ReloadOrigin;
import net.managerhub.center.common.remote.MariaDbRemoteStore;
import net.managerhub.center.common.remote.RemoteException;
import net.managerhub.center.common.remote.RemoteService;
import net.managerhub.center.common.remote.RemoteWorker;
import net.managerhub.center.common.util.DefaultFiles;
import net.managerhub.center.velocity.command.ProxyCommandService;
import net.managerhub.center.velocity.config.ProxyConfiguration;
import net.managerhub.center.velocity.config.VelocityConfigLoader;
import net.managerhub.center.velocity.module.VelocityModuleContext;
import net.managerhub.center.velocity.module.VelocityModuleService;
import net.managerhub.center.velocity.network.NetworkStatusService;
import org.slf4j.Logger;

/**
 * Velocity entry point of MHCenter2.
 *
 * <p>On the proxy MHCenter2 manages its own lifecycle, its texts, its own local
 * SQLite database, the optional remote database and the network status of the
 * MHCenter2 instances. It registers only the module administration and the reload
 * as commands and has no menu, so {@code Commands.yml}, {@code Permissions.yml}
 * and {@code Menus/} are not created here.</p>
 *
 * <p>The proxy is a full MHCenter2 node: it takes part in the network wide reload
 * and in the remote actions, and it does so without depending on anybody being
 * able to type a command on the proxy console. A reload that starts on a Paper
 * server reaches it either through the plugin message or through the remote
 * database.</p>
 *
 * <p>The values of the {@link Plugin} annotation must stay in sync with
 * {@link Center}. They are written out literally because an annotation is
 * evaluated at build time by the Velocity annotation processor, which generates
 * {@code velocity-plugin.json} from them.</p>
 */
@Plugin(
        id = "mhcenter2",
        name = "MHCenter2",
        version = "1.0.1",
        description = "MHCenter2 server core for Paper and Velocity.",
        url = "https://managerhub.net",
        authors = {"Manager Hub"}
)
public final class CenterVelocityPlugin {

    /** Name of this platform, used in the lifecycle messages. */
    private static final String PLATFORM = "Velocity";

    private static final String VELOCITY_DEFAULTS = "defaults/velocity/";
    private static final String LANGUAGE_DEFAULTS = "defaults/language/";

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private SqliteDatabase database;
    private NetworkStatusService network;
    private VelocityModuleService modules;
    private ProxyCommandService commands;
    private RemoteService remote;
    private RemoteWorker remoteWorker;

    /** The configuration that is active right now. */
    private volatile ProxyConfiguration configuration;

    @Inject
    public CenterVelocityPlugin(final ProxyServer proxy,
                                final Logger logger,
                                final @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = centerDirectory(dataDirectory);
    }

    /**
     * Velocity derives the injected folder from the plugin id, which has to be
     * lower case. MHCenter2 stores its files under the product name instead, so the
     * folder matches the Paper side and no second folder is created.
     *
     * @param injected folder Velocity offers
     * @return the folder MHCenter2 really uses
     */
    private static Path centerDirectory(final Path injected) {
        final Path plugins = injected.getParent();
        return plugins == null ? injected : plugins.resolve(Center.PRODUCT_NAME);
    }

    @Subscribe
    public void onProxyInitialize(final ProxyInitializeEvent event) {
        try {
            installDefaults();
        } catch (final IOException failure) {
            failStartup("The default configuration files could not be created: " + failure.getMessage());
            return;
        }

        try {
            configuration = VelocityConfigLoader.load(dataDirectory);
        } catch (final ConfigurationException failure) {
            failStartup(failure.getMessage());
            return;
        }

        final SqliteDatabase sqlite =
                new SqliteDatabase(dataDirectory.resolve(Center.DATABASE_DIRECTORY).resolve(Center.DATABASE_FILE));
        try {
            sqlite.initialize();
        } catch (final DatabaseException failure) {
            failStartup(failure.getMessage());
            return;
        }
        this.database = sqlite;

        // The proxy has no single Minecraft version of its own, so it reports none.
        this.remote = new RemoteService(ModulePlatform.VELOCITY, "",
                VelocityModuleContext.coreLogger(logger),
                this::language,
                MariaDbRemoteStore::new,
                action -> {
                    if (!reloadLocally(ReloadOrigin.REMOTE_REQUEST)) {
                        // The receipt has to say that it failed, otherwise the
                        // administrator would be told this node was reloaded.
                        throw new IllegalStateException("The local reload of "
                                + Center.PRODUCT_NAME + " failed.");
                    }
                },
                System::currentTimeMillis);
        this.remoteWorker = new RemoteWorker(remote, VelocityModuleContext.coreLogger(logger), this::language);

        this.network = new NetworkStatusService(proxy, this, remote);
        this.network.onReload(this::reloadLocally);
        this.network.remoteNodes(remote::onlineNodes);
        this.network.register();
        proxy.getEventManager().register(this, network);

        // The remote system starts first, so the modules are already handed a
        // network that is on its way up. It connects in the background either
        // way, so a module must not expect it to be usable in onEnable.
        remoteWorker.apply(configuration.remote());
        loadModules();

        logger.info(language().get(MessageKey.PLUGIN_ENABLED_PROXY,
                "product", Center.PRODUCT_NAME, "version", Center.VERSION, "platform", PLATFORM));
    }

    /**
     * Starts the module system of the proxy.
     *
     * <p>A module that was built for Paper only does not belong here and is
     * skipped; the proxy keeps running either way. A {@code VELOCITY} or
     * {@code BOTH} module goes through exactly the same lifecycle as on Paper,
     * may register its own proxy commands and may use the Velocity part of the
     * module API.</p>
     */
    private void loadModules() {
        final ModuleCommandRegistry moduleCommands = new ModuleCommandRegistry();
        modules = new VelocityModuleService(proxy, this, logger, moduleCommands, this::language, remote, network);
        commands = new ProxyCommandService(proxy, this, logger, moduleCommands, this::language, modules,
                () -> reloadLocally(ReloadOrigin.LOCAL_USER_REQUEST));
        modules.bind(commands);
        // A module command is only accepted when it does not collide with a
        // command of the core, so the registry has to know them.
        moduleCommands.reserve(commands::corePaths);
        // And a command name another plugin already owns can never be served.
        moduleCommands.platform(commands::takenByOtherPlugin);

        modules.start(dataDirectory, database);
    }

    /**
     * Reloads MHCenter2 on this proxy.
     *
     * <p>The one reload pipeline of the proxy: the texts, the remote settings and
     * {@code onReload()} of every running module. It is used by a request that
     * arrived from a Paper server, by an action from the remote database and by
     * the reload command of the proxy, so all three really do the same thing.</p>
     *
     * <p>Nothing of it is a new load: no module jar is read again and no module is
     * created a second time.</p>
     *
     * @param origin why the reload happens
     * @return {@code true} if the new configuration is active now
     */
    private boolean reloadLocally(final ReloadOrigin origin) {
        final Language previous = language();
        final ProxyConfiguration next;
        try {
            next = VelocityConfigLoader.load(dataDirectory);
        } catch (final ConfigurationException failure) {
            logger.warn(previous.get(MessageKey.RELOAD_LOG_FAILED,
                    "product", Center.PRODUCT_NAME, "reason", String.valueOf(failure.getMessage())));
            return false;
        }
        // The new configuration is complete and valid, so it becomes the current
        // one before anything is told to use it.
        configuration = next;
        remoteWorker.apply(next.remote());
        if (modules != null) {
            modules.reloadModules();
        }
        logger.info(next.language().get(MessageKey.RELOAD_LOG_SUCCESS,
                "product", Center.PRODUCT_NAME,
                "commands", Integer.toString(commands == null ? 0 : commands.registeredCommands())));

        if (origin.spreads() && remote != null && remote.available()) {
            // Only a reload somebody asked for here goes out into the network. A
            // reload that arrived from the network is never handed on again.
            spread();
        }
        return true;
    }

    /**
     * Writes a network wide reload into the remote database.
     *
     * <p>The proxy is not the usual place to start a reload - the network
     * administration of MHCenter2 never depends on being able to type a command
     * here - but if somebody does, it works the same way as on Paper.</p>
     */
    private void spread() {
        final UUID requestId = UUID.randomUUID();
        remoteWorker.submit("reload-publish", () -> {
            try {
                remote.publishReload(requestId);
            } catch (final RemoteException failure) {
                logger.warn(language().get(MessageKey.RELOAD_NETWORK_FAILED,
                        "reason", String.valueOf(failure.getMessage())));
            }
        });
    }

    @Subscribe
    public void onProxyShutdown(final ProxyShutdownEvent event) {
        if (remoteWorker != null) {
            // First: the remote thread stops and this node disappears from the
            // network list, so nobody sends it an action it can no longer answer.
            remoteWorker.close();
            remoteWorker = null;
            remote = null;
        }
        if (modules != null) {
            modules.stop();
            modules = null;
        }
        if (commands != null) {
            commands.unregisterAll();
            commands = null;
        }
        if (network != null) {
            network.unregister();
            network = null;
        }
        if (database != null) {
            database.close();
            database = null;
        }
        if (configuration != null) {
            logger.info(language().get(MessageKey.PLUGIN_DISABLED,
                    "product", Center.PRODUCT_NAME, "version", Center.VERSION, "platform", PLATFORM));
            configuration = null;
        }
    }

    /** @return the texts of the configuration that is active right now. */
    private Language language() {
        final ProxyConfiguration current = configuration;
        return current.language();
    }

    private void installDefaults() throws IOException {
        Files.createDirectories(dataDirectory);
        install(dataDirectory.resolve(Center.MAIN_CONFIG_FILE), VELOCITY_DEFAULTS + Center.MAIN_CONFIG_FILE);
        for (final String code : Language.SUPPORTED) {
            install(dataDirectory.resolve(Center.LANGUAGE_DIRECTORY).resolve(Language.fileName(code)),
                    LANGUAGE_DEFAULTS + Language.fileName(code));
        }
        ModuleLoader.createDirectories(dataDirectory.resolve(Center.MODULES_DIRECTORY));
    }

    /**
     * Makes sure the file exists and carries every entry this MHCenter2 version
     * needs.
     *
     * <p>Messages here are written before a language file was read, so they stay
     * English on purpose.</p>
     */
    private void install(final Path target, final String resource) throws IOException {
        final DefaultFiles.Installation installation = DefaultFiles.install(target, resource);
        if (installation == DefaultFiles.Installation.KEPT) {
            // Only a file that was already there can be missing a new entry.
            migrate(target, resource);
        }
        if (installation == DefaultFiles.Installation.REPAIRED) {
            logger.warn("'{}' was empty and has been restored from the {} default.",
                    target.getFileName(), Center.PRODUCT_NAME);
        }
    }

    private void migrate(final Path target, final String resource) {
        final ConfigMigration.Result result;
        try {
            result = ConfigMigration.apply(target, resource);
        } catch (final IOException | RuntimeException failure) {
            logger.warn("'{}' could not be brought up to the current {} default: {}",
                    target.getFileName(), Center.PRODUCT_NAME, failure.getMessage());
            return;
        }
        if (result.skippedNewer()) {
            logger.warn("'{}' declares config-version {}, but this {} version only knows {}. "
                            + "The file was not changed.",
                    target.getFileName(), result.fromVersion(), Center.PRODUCT_NAME, Center.CONFIG_VERSION);
            return;
        }
        if (result.changed()) {
            logger.info("'{}': added {} new default entries {} and set config-version to {}. "
                            + "Existing values were kept.",
                    target.getFileName(), result.added().size(), result.added(), result.toVersion());
        }
    }

    /**
     * Stops the MHCenter2 initialization. Messages that are written before a
     * configuration exists cannot use the language files yet, so they stay in
     * English on purpose. The proxy itself keeps running.
     *
     * @param reason concrete cause for the administrator
     */
    private void failStartup(final String reason) {
        logger.error("{} could not start: {}", Center.PRODUCT_NAME, reason);
        logger.error("The initialization of {} is stopped. The proxy keeps running.", Center.PRODUCT_NAME);
    }
}
