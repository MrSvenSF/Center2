package net.managerhub.center.paper.module;

import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Level;

import net.managerhub.center.api.ModuleCommand;
import net.managerhub.center.api.ModuleContext;
import net.managerhub.center.api.ModuleLogger;
import net.managerhub.center.api.ModuleNetwork;
import net.managerhub.center.api.ModulePlatform;
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
import org.bukkit.plugin.Plugin;

/**
 * Builds the context every module gets on Paper.
 *
 * <p>The context is exactly the platform neutral
 * {@link net.managerhub.center.api.ModuleContext}: id, own folder, log, platform,
 * cleanup and command registration. Nothing of the internal MHCenter2
 * implementation is handed out, and a module never sees a Bukkit type through
 * its MHCenter2 API.</p>
 */
public final class PaperModuleSupport implements ModuleLoader.ContextFactory {

    private final Plugin plugin;
    private final ModuleCommandRegistry commands;
    private final ModuleReport report;
    private final RemoteService remote;
    private final ModuleActionFallback fallback;

    /**
     * @param plugin   the MHCenter2 plugin, used for the log
     * @param commands where the commands of the modules are collected
     * @param report   where a rejected module command is written
     * @param remote   the optional remote system of this node
     */
    public PaperModuleSupport(final Plugin plugin,
                              final ModuleCommandRegistry commands,
                              final ModuleReport report,
                              final RemoteService remote,
                              final ModuleActionFallback fallback) {
        this.plugin = plugin;
        this.commands = commands;
        this.report = report;
        this.remote = remote;
        this.fallback = fallback;
    }

    /**
     * @param plugin the MHCenter2 plugin
     * @return the log MHCenter2 itself uses for the module system
     */
    public static ModuleLogger coreLogger(final Plugin plugin) {
        return new PluginLogger(plugin, "");
    }

    @Override
    public ModuleContext create(final ModuleDescriptor descriptor,
                                final Path configDirectory,
                                final ModuleCleanup cleanup) {
        return new Context(descriptor, configDirectory,
                new PluginLogger(plugin, descriptor.id()), commands, report, cleanup,
                new ModuleRemoteAccess(descriptor.id(), remote, fallback));
    }

    /** Writes the lines of a module into the normal server log. */
    private record PluginLogger(Plugin plugin, String moduleId) implements ModuleLogger {

        @Override
        public void info(final String message) {
            plugin.getLogger().info(prefixed(message));
        }

        @Override
        public void warn(final String message) {
            plugin.getLogger().warning(prefixed(message));
        }

        @Override
        public void error(final String message, final Throwable failure) {
            plugin.getLogger().log(Level.SEVERE, prefixed(message), failure);
        }

        private String prefixed(final String message) {
            return moduleId.isEmpty() ? message : "[" + moduleId + "] " + message;
        }
    }

    private record Context(ModuleDescriptor descriptor,
                           Path configDirectory,
                           ModuleLogger logger,
                           ModuleCommandRegistry commands,
                           ModuleReport report,
                           ModuleCleanup cleanup,
                           ModuleNetwork network) implements ModuleContext {

        /**
         * Paper offers no service of its own: a Paper module already has the whole
         * Bukkit API on its class path and needs nothing from MHCenter2 for it.
         */
        @Override
        public <T> Optional<T> service(final Class<T> type) {
            return Optional.empty();
        }

        @Override
        public String moduleId() {
            return descriptor.id();
        }

        @Override
        public ModulePlatform platform() {
            return ModulePlatform.PAPER;
        }

        @Override
        public void registerCleanup(final Runnable action) {
            cleanup.register(action);
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
    }
}
