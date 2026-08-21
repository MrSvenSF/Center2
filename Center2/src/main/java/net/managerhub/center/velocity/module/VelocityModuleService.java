package net.managerhub.center.velocity.module;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.velocitypowered.api.proxy.ProxyServer;
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

import net.managerhub.center.velocity.command.ProxyCommandService;
import org.slf4j.Logger;

/**
 * The module administration of Center2 on the proxy.
 *
 * <p>It is the counterpart of the Paper module service and uses the same
 * {@link ModuleLoader} and the same {@link ModuleAdministration}, so a module goes
 * through exactly the same lifecycle on both platforms. Only the command
 * registration is different, because Velocity registers commands in its own
 * way.</p>
 *
 * <p>The proxy has no single Minecraft version, so no Minecraft range is checked
 * here. Inventing a value would only produce wrong answers.</p>
 */
public final class VelocityModuleService implements ModuleAdministration.Modules {

    private final ProxyServer proxy;
    private final Object plugin;
    private final Logger logger;
    private final ModuleCommandRegistry moduleCommands;
    private final Supplier<Language> language;
    private final RemoteService remote;
    private final ModuleActionFallback fallback;

    private ProxyCommandService commands;
    private ModuleLoader loader;
    private Path jarsDirectory;
    private Path configsDirectory;

    /**
     * @param proxy          the running proxy
     * @param plugin         the Center2 plugin instance, which owns every registration
     * @param logger         log of Center2 on the proxy
     * @param moduleCommands where the commands of the modules are collected
     * @param language       the texts that are currently active
     * @param remote         the optional remote system of this node
     */
    public VelocityModuleService(final ProxyServer proxy,
                                 final Object plugin,
                                 final Logger logger,
                                 final ModuleCommandRegistry moduleCommands,
                                 final Supplier<Language> language,
                                 final RemoteService remote,
                                 final ModuleActionFallback fallback) {
        this.proxy = proxy;
        this.plugin = plugin;
        this.logger = logger;
        this.moduleCommands = moduleCommands;
        this.language = language;
        this.remote = remote;
        this.fallback = fallback;
    }

    /**
     * Connects the service with the command registration of the proxy.
     *
     * @param commands the command service of Center2 on the proxy
     */
    public void bind(final ProxyCommandService commands) {
        this.commands = commands;
    }

    /**
     * Builds the loader and reads {@code Modules/Jars} for the first time.
     *
     * @param dataDirectory the Center2 folder of the proxy
     * @param states        where the switched off modules are remembered
     */
    public void start(final Path dataDirectory, final ModuleStateStore states) {
        final Path modulesFolder = dataDirectory.resolve(Center.MODULES_DIRECTORY);
        this.jarsDirectory = modulesFolder.resolve(Center.MODULE_JARS_DIRECTORY);
        this.configsDirectory = modulesFolder.resolve(Center.MODULE_CONFIGS_DIRECTORY);

        final LoggingModuleReport report =
                new LoggingModuleReport(VelocityModuleContext.coreLogger(logger), language);
        this.loader = new ModuleLoader(ModulePlatform.VELOCITY,
                Version.of(Center.VERSION).orElseThrow(),
                Optional.empty(),
                VelocityModuleContext.factory(proxy, plugin, logger, moduleCommands, report, remote, fallback),
                states,
                report);

        scan();
    }

    /**
     * Tells every running module that the configuration was reloaded.
     *
     * <p>Part of the network wide reload. No jar is read again and no module is
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
        logger.info(language.get().get(MessageKey.MODULE_RELOADED, "modules", Integer.toString(reloaded)));
    }

    /** Stops every running module and removes every module command. */
    public void stop() {
        if (loader != null) {
            loader.disableAll();
            loader = null;
        }
        moduleCommands.clear();
        if (commands != null) {
            commands.apply();
        }
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

    /** Reads the jar folder and writes the result into the proxy log. */
    private void scan() {
        loader.refresh(jarsDirectory, configsDirectory);
        synchronizeCommands();
        logger.info(language.get().get(MessageKey.MODULE_LOADED,
                "installed", Integer.toString(loader.modules().size()),
                "enabled", Integer.toString(loader.enabledCount())));
    }

    /**
     * Drops the commands of every module that is not running and registers the
     * remaining commands on the proxy again.
     */
    private void synchronizeCommands() {
        for (final ModuleLoader.InstalledModule module : loader.modules()) {
            if (module.status() != ModuleStatus.ENABLED) {
                moduleCommands.unregisterModule(module.descriptor().id());
            }
        }
        if (commands != null) {
            commands.apply();
        }
    }
}
