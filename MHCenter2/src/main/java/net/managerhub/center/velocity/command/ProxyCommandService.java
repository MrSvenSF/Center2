package net.managerhub.center.velocity.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import net.managerhub.center.Center;
import net.managerhub.center.api.ModuleCommand;
import net.managerhub.center.common.command.CommandPath;
import net.managerhub.center.common.command.ModuleCommandRegistry;
import net.managerhub.center.common.language.Language;
import net.managerhub.center.common.language.MessageKey;
import net.managerhub.center.common.module.ModuleAdministration;
import org.slf4j.Logger;

/**
 * The commands of MHCenter2 on the proxy.
 *
 * <p>Velocity has no {@code Commands.yml} and no menu, so the paths of the module
 * administration are fixed here. Everything else works like on Paper: one
 * registered command name serves every path that starts with it, the module
 * commands live next to the ones of the core, and a command of another plugin is
 * never taken over.</p>
 */
public final class ProxyCommandService {

    /** The fixed reload command of the proxy. */
    public static final CommandPath RELOAD_PATH = new CommandPath(List.of("center", "reload"));

    /** The fixed module overview of the proxy. */
    public static final CommandPath MODULES_PATH = new CommandPath(List.of("center", "modules"));

    /** The fixed module reload command of the proxy. */
    public static final CommandPath MODULES_RELOAD_PATH = new CommandPath(List.of("center", "modules", "reload"));

    /** The fixed module enable command of the proxy. */
    public static final CommandPath MODULES_ENABLE_PATH = new CommandPath(List.of("center", "modules", "enable"));

    /** The fixed module disable command of the proxy. */
    public static final CommandPath MODULES_DISABLE_PATH = new CommandPath(List.of("center", "modules", "disable"));

    /** Every path the core owns on the proxy. */
    public static final List<CommandPath> CORE_PATHS =
            List.of(RELOAD_PATH, MODULES_PATH, MODULES_RELOAD_PATH, MODULES_ENABLE_PATH, MODULES_DISABLE_PATH);

    /** What one command path does. */
    private record Route(List<String> tail,
                         Predicate<CommandSource> allowed,
                         Optional<ModuleCommand> module,
                         Optional<CoreAction> core,
                         Optional<MessageKey> description) {
    }

    /** One administrative action of the core. */
    @FunctionalInterface
    private interface CoreAction {

        /**
         * @param source   who used the command
         * @param argument the first argument behind the fixed path, empty if not given
         * @param path     the path that was used, for a usage hint
         */
        void run(CommandSource source, String argument, String path);
    }

    private final ProxyServer proxy;
    private final Object plugin;
    private final Logger logger;
    private final ModuleCommandRegistry moduleCommands;
    private final Supplier<Language> language;
    private final ModuleAdministration.Modules modules;
    private final BooleanSupplier reload;

    /** The command names MHCenter2 has registered on the proxy right now. */
    private final Set<String> registered = new LinkedHashSet<>();

    /**
     * @param proxy          the running proxy
     * @param plugin         the MHCenter2 plugin instance, needed for the command metadata
     * @param logger         log of MHCenter2 on the proxy
     * @param moduleCommands where the commands of the modules are collected
     * @param language       the texts that are currently active
     * @param modules        the module administration of the proxy
     * @param reload         reloads MHCenter2 on this proxy, answers whether it worked
     */
    public ProxyCommandService(final ProxyServer proxy,
                               final Object plugin,
                               final Logger logger,
                               final ModuleCommandRegistry moduleCommands,
                               final Supplier<Language> language,
                               final ModuleAdministration.Modules modules,
                               final BooleanSupplier reload) {
        this.proxy = proxy;
        this.plugin = plugin;
        this.logger = logger;
        this.moduleCommands = moduleCommands;
        this.language = language;
        this.modules = modules;
        this.reload = reload;
    }

    /** @return every path the core owns, so a module command can never take one. */
    public Set<String> corePaths() {
        final Set<String> paths = new LinkedHashSet<>();
        for (final CommandPath path : CORE_PATHS) {
            paths.add(path.display());
        }
        return Set.copyOf(paths);
    }

    /**
     * Registers the current command tree on the proxy again.
     *
     * <p>This is called after every change of the module state, so a command of a
     * module appears when the module starts and is gone when it stops.</p>
     */
    public void apply() {
        final Map<String, List<Route>> byName = new LinkedHashMap<>();
        core(byName);
        for (final ModuleCommandRegistry.Registered command : moduleCommands.all()) {
            byName.computeIfAbsent(command.path().rootName(), name -> new ArrayList<>())
                    .add(new Route(command.path().tail(), source -> true,
                            Optional.of(command.command()), Optional.empty(), Optional.empty()));
        }

        unregisterAll();
        final CommandManager manager = proxy.getCommandManager();
        byName.forEach((name, routes) -> {
            if (manager.hasCommand(name)) {
                logger.warn(language.get().get(MessageKey.REGISTRY_NAME_TAKEN,
                        "command", name, "namespace", Center.PRODUCT_NAME.toLowerCase(Locale.ROOT)));
                return;
            }
            final CommandMeta meta = manager.metaBuilder(name).plugin(plugin).build();
            manager.register(meta, new CenterProxyCommand(name, routes));
            registered.add(name);
        });
    }

    /** Removes every command MHCenter2 registered on the proxy. */
    public void unregisterAll() {
        final CommandManager manager = proxy.getCommandManager();
        for (final String name : registered) {
            manager.unregister(name);
        }
        registered.clear();
    }

    /** @return the number of command names MHCenter2 has registered. */
    public int registeredCommands() {
        return registered.size();
    }

    /**
     * Whether a command name belongs to another plugin of the proxy.
     *
     * <p>The command manager of Velocity is the truth here, so a module can be
     * told at once that the name it wants is taken. A name MHCenter2 registered
     * itself is not foreign: a module command like {@code center proxytest} lives
     * under the {@code center} command of the core on purpose.</p>
     *
     * @param name the command name a module wants
     * @return {@code true} if another plugin already owns that name
     */
    public boolean takenByOtherPlugin(final String name) {
        final String wanted = name.toLowerCase(Locale.ROOT);
        if (registered.stream().anyMatch(own -> own.equalsIgnoreCase(wanted))) {
            return false;
        }
        return proxy.getCommandManager().hasCommand(wanted);
    }

    private void core(final Map<String, List<Route>> byName) {
        add(byName, RELOAD_PATH, MessageKey.COMMAND_DESCRIPTION_RELOAD,
                source -> ProxyPermissions.allows(source, ProxyPermissions.ADMIN, ProxyPermissions.RELOAD),
                (source, argument, path) -> {
                    // The whole answer is one line on purpose: the details of the
                    // reload are in the proxy log, and the network administration
                    // of MHCenter2 never depends on this command.
                    final Language texts = language.get();
                    final boolean worked = reload.getAsBoolean();
                    send(source).accept(worked
                            ? texts.get(MessageKey.RELOAD_SUCCESS,
                                    "product", Center.PRODUCT_NAME,
                                    "commands", Integer.toString(registeredCommands()))
                            : texts.get(MessageKey.RELOAD_PREVIOUS_ACTIVE));
                });
        add(byName, MODULES_PATH, MessageKey.COMMAND_DESCRIPTION_MODULES,
                source -> ProxyPermissions.allows(source, ProxyPermissions.ADMIN, ProxyPermissions.MODULES),
                (source, argument, path) -> ModuleAdministration.list(
                        modules, language.get(), ProxyCommandService::escape, send(source)));
        add(byName, MODULES_RELOAD_PATH, MessageKey.COMMAND_DESCRIPTION_MODULES_RELOAD,
                source -> ProxyPermissions.allows(source, ProxyPermissions.ADMIN,
                        ProxyPermissions.MODULES, ProxyPermissions.MODULES_RELOAD),
                (source, argument, path) -> ModuleAdministration.reload(modules, language.get(), send(source)));
        add(byName, MODULES_ENABLE_PATH, MessageKey.COMMAND_DESCRIPTION_MODULES_ENABLE,
                source -> ProxyPermissions.allows(source, ProxyPermissions.ADMIN,
                        ProxyPermissions.MODULES, ProxyPermissions.MODULES_ENABLE),
                (source, argument, path) -> ModuleAdministration.enable(
                        modules, language.get(), ProxyCommandService::escape, argument, path, send(source)));
        add(byName, MODULES_DISABLE_PATH, MessageKey.COMMAND_DESCRIPTION_MODULES_DISABLE,
                source -> ProxyPermissions.allows(source, ProxyPermissions.ADMIN,
                        ProxyPermissions.MODULES, ProxyPermissions.MODULES_DISABLE),
                (source, argument, path) -> ModuleAdministration.disable(
                        modules, language.get(), ProxyCommandService::escape, argument, path, send(source)));
    }

    private static void add(final Map<String, List<Route>> byName,
                            final CommandPath path,
                            final MessageKey description,
                            final Predicate<CommandSource> allowed,
                            final CoreAction action) {
        byName.computeIfAbsent(path.rootName(), name -> new ArrayList<>())
                .add(new Route(path.tail(), allowed, Optional.empty(),
                        Optional.of(action), Optional.of(description)));
    }

    private static java.util.function.Consumer<String> send(final CommandSource source) {
        return message -> new ProxyCommandSender(source).sendMessage(message);
    }

    private static String escape(final String raw) {
        return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().escapeTags(raw);
    }

    /** One registered command name of MHCenter2 on the proxy. */
    private final class CenterProxyCommand implements SimpleCommand {

        private final String name;
        private final List<Route> routes;

        private CenterProxyCommand(final String name, final List<Route> routes) {
            this.name = name;
            // The longest path wins, so "modules reload" is never swallowed by "modules".
            this.routes = routes.stream()
                    .sorted((left, right) -> Integer.compare(right.tail().size(), left.tail().size()))
                    .toList();
        }

        @Override
        public void execute(final Invocation invocation) {
            final CommandSource source = invocation.source();
            final List<String> input = normalized(invocation.arguments(), invocation.arguments().length);
            final Optional<Route> match = routes.stream().filter(route -> matches(route, input)).findFirst();
            if (match.isEmpty()) {
                overview(source);
                return;
            }

            final Route route = match.get();
            if (!route.allowed().test(source)) {
                new ProxyCommandSender(source).sendMessage(language.get().get(MessageKey.COMMAND_NO_PERMISSION));
                return;
            }
            if (route.module().isPresent()) {
                route.module().get().execute(new ProxyCommandSender(source));
                return;
            }
            route.core().orElseThrow().run(source, argument(route, input), used(route));
        }

        @Override
        public List<String> suggest(final Invocation invocation) {
            final String[] arguments = invocation.arguments();
            if (arguments.length == 0) {
                return List.of();
            }
            final List<String> typed = normalized(arguments, arguments.length - 1);
            final String partial = arguments[arguments.length - 1].toLowerCase(Locale.ROOT);
            final Set<String> options = new LinkedHashSet<>();
            for (final Route route : routes) {
                if (!route.allowed().test(invocation.source())) {
                    continue;
                }
                final List<String> tail = route.tail();
                if (tail.equals(typed) && takesModuleId(route)) {
                    options.addAll(ModuleAdministration.moduleIds(modules));
                    continue;
                }
                if (tail.size() > typed.size() && tail.subList(0, typed.size()).equals(typed)) {
                    options.add(tail.get(typed.size()));
                }
            }
            return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(partial)).toList();
        }

        private void overview(final CommandSource source) {
            final ProxyCommandSender sender = new ProxyCommandSender(source);
            final Language texts = language.get();
            sender.sendMessage(texts.get(MessageKey.COMMAND_OVERVIEW_HEADER,
                    "product", Center.PRODUCT_NAME, "version", Center.VERSION));
            boolean any = false;
            for (final Route route : routes) {
                if (!route.allowed().test(source)) {
                    continue;
                }
                any = true;
                sender.sendMessage(texts.get(MessageKey.COMMAND_OVERVIEW_ENTRY,
                        "path", used(route),
                        // Some descriptions name the product, so the placeholder
                        // has to be filled in here as well.
                        "description", route.description()
                                .map(key -> texts.get(key, "product", Center.PRODUCT_NAME))
                                .orElse("")));
            }
            if (!any) {
                sender.sendMessage(texts.get(MessageKey.COMMAND_OVERVIEW_EMPTY));
            }
        }

        private String used(final Route route) {
            return route.tail().isEmpty() ? name : name + " " + String.join(" ", route.tail());
        }

        private boolean takesModuleId(final Route route) {
            return route.tail().equals(MODULES_ENABLE_PATH.tail())
                    || route.tail().equals(MODULES_DISABLE_PATH.tail());
        }
    }

    private static boolean matches(final Route route, final List<String> input) {
        final List<String> tail = route.tail();
        if (tail.isEmpty()) {
            return input.isEmpty();
        }
        return input.size() >= tail.size() && input.subList(0, tail.size()).equals(tail);
    }

    private static String argument(final Route route, final List<String> input) {
        final int index = route.tail().size();
        return input.size() > index ? input.get(index) : "";
    }

    private static List<String> normalized(final String[] arguments, final int length) {
        final List<String> normalized = new ArrayList<>(length);
        for (int index = 0; index < length; index++) {
            normalized.add(arguments[index].toLowerCase(Locale.ROOT));
        }
        return List.copyOf(normalized);
    }
}
