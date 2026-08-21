package net.managerhub.center.velocity.module;

import java.nio.file.Path;
import java.util.Optional;

import com.velocitypowered.api.proxy.ProxyServer;
import net.managerhub.center.api.ModuleCommand;
import net.managerhub.center.api.ModuleContext;
import net.managerhub.center.api.ModuleLogger;
import net.managerhub.center.api.ModuleNetwork;
import net.managerhub.center.api.ModulePlatform;
import net.managerhub.center.api.velocity.VelocityModuleApi;
import net.managerhub.center.common.command.CommandPath;
import net.managerhub.center.common.command.ModuleCommandRegistry;
import net.managerhub.center.common.config.ConfigurationException;
import net.managerhub.center.common.module.ModuleCleanup;
import net.managerhub.center.common.module.ModuleDescriptor;
import net.managerhub.center.common.module.ModuleLifecycle;
import net.managerhub.center.common.module.ModuleLoader;
import net.managerhub.center.common.module.ModuleReport;
import net.managerhub.center.common.remote.ModuleRemoteAccess;
import net.managerhub.center.common.remote.ModuleActionFallback;
import net.managerhub.center.common.remote.RemoteService;
import org.slf4j.Logger;

/**
 * The context a module gets on Velocity.
 *
 * <p>The neutral part is exactly the same
 * {@link net.managerhub.center.api.ModuleContext} a module gets on Paper: id, own
 * folder, log, platform, cleanup, command registration and the network. A proxy
 * module therefore never needs a Paper class, and the same module code works on
 * both sides as long as the module itself stays platform neutral.</p>
 *
 * <p>On top of that, and only here, {@link #service(Class)} answers with
 * {@link VelocityModuleApi}. That is where a proxy module reaches Velocity
 * events, the proxy scheduler, the backend servers and the server list ping - the
 * things a real proxy module is written for.</p>
 *
 * @param descriptor      metadata of the module
 * @param configDirectory folder the module may use for its own files
 * @param logger          log of the module
 * @param commands        where the commands of the modules are collected
 * @param report          where a rejected module command is written
 * @param cleanup         where the module says how its resources are removed again
 * @param network         what this module may do in the Center2 network
 * @param proxyApi        the Velocity part of the module API for this module
 */
public record VelocityModuleContext(ModuleDescriptor descriptor,
                                    Path configDirectory,
                                    ModuleLogger logger,
                                    ModuleCommandRegistry commands,
                                    ModuleReport report,
                                    ModuleCleanup cleanup,
                                    ModuleNetwork network,
                                    VelocityModuleApi proxyApi) implements ModuleContext {

    /**
     * @param proxy       the running proxy
     * @param plugin      the Center2 plugin instance, which owns every registration
     * @param proxyLogger log of Center2 on the proxy
     * @param commands    where the commands of the modules are collected
     * @param report      where a rejected module command is written
     * @param remote      the optional remote system of this node
     * @return a factory that builds the context of every module
     */
    public static ModuleLoader.ContextFactory factory(final ProxyServer proxy,
                                                      final Object plugin,
                                                      final Logger proxyLogger,
                                                      final ModuleCommandRegistry commands,
                                                      final ModuleReport report,
                                                      final RemoteService remote,
                                                      final ModuleActionFallback fallback) {
        return (ModuleDescriptor descriptor, Path configDirectory, ModuleCleanup cleanup) ->
                new VelocityModuleContext(descriptor, configDirectory,
                        new ProxyLogger(proxyLogger, descriptor.id()), commands, report, cleanup,
                        new ModuleRemoteAccess(descriptor.id(), remote, fallback),
                        new ProxyModuleApi(proxy, plugin, cleanup));
    }

    /**
     * @param proxyLogger log of Center2 on the proxy
     * @return the log Center2 itself uses for the module system
     */
    public static ModuleLogger coreLogger(final Logger proxyLogger) {
        return new ProxyLogger(proxyLogger, "");
    }

    @Override
    public String moduleId() {
        return descriptor.id();
    }

    @Override
    public ModulePlatform platform() {
        return ModulePlatform.VELOCITY;
    }

    @Override
    public void registerCleanup(final Runnable action) {
        cleanup.register(action);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The proxy offers exactly one service: {@link VelocityModuleApi}. Asking
     * for anything else is not an error, it simply answers empty - that is how a
     * {@code BOTH} module finds out where it is without ever loading a class of
     * the other platform.</p>
     */
    @Override
    public <T> Optional<T> service(final Class<T> type) {
        if (type == null || !type.isInstance(proxyApi)) {
            return Optional.empty();
        }
        return Optional.of(type.cast(proxyApi));
    }

    @Override
    public boolean registerCommand(final String path, final ModuleCommand command) {
        final CommandPath commandPath;
        try {
            commandPath = CommandPath.of("command", path);
        } catch (final ConfigurationException invalid) {
            return rejected("the command path \"" + path + "\" is invalid: " + invalid.getMessage());
        }
        // The decision is made now, not later while the command tree is built,
        // so the answer a module gets is the truth.
        final Optional<String> refused = commands.register(moduleId(), commandPath, command);
        if (refused.isPresent()) {
            return rejected(refused.get());
        }
        // The command is removed again by the same cleanup that removes every
        // other resource, so a module that fails later leaves none behind.
        cleanup.register(() -> commands.unregister(moduleId(), commandPath));
        return true;
    }

    /** A rejected command is a problem of the module, never a reason to stop it. */
    private boolean rejected(final String reason) {
        report.error(descriptor, ModuleLifecycle.COMMAND_REGISTRATION, reason, null);
        return false;
    }

    /** Writes the lines of a module into the normal proxy log. */
    private record ProxyLogger(Logger logger, String moduleId) implements ModuleLogger {

        @Override
        public void info(final String message) {
            logger.info(prefixed(message));
        }

        @Override
        public void warn(final String message) {
            logger.warn(prefixed(message));
        }

        @Override
        public void error(final String message, final Throwable failure) {
            logger.error(prefixed(message), failure);
        }

        private String prefixed(final String message) {
            return moduleId.isEmpty() ? message : "[" + moduleId + "] " + message;
        }
    }
}
