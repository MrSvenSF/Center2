package net.managerhub.center.paper.command;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import net.managerhub.center.Center;
import net.managerhub.center.common.command.CommandsDefinition;
import net.managerhub.center.common.language.Language;
import net.managerhub.center.common.language.MessageKey;
import net.managerhub.center.paper.config.PermissionGate;
import net.managerhub.center.paper.menu.CenterMenu;
import net.managerhub.center.api.ModuleCommand;
import net.managerhub.center.paper.text.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.StringUtil;

/**
 * One command of Center2 that is registered on the server.
 *
 * <p>A command path like {@code center info} is split into the registered name
 * {@code center} and the fixed arguments behind it. Every path that starts with
 * the same name is served by one instance of this class, so {@code /center info}
 * and {@code /center reload} share a single registered command.</p>
 *
 * <p>A new instance is built for every successful reload, so this object always
 * describes exactly one configuration snapshot. The permission is checked per
 * route and never on the registered command itself, because the routes of one
 * command can have different permissions.</p>
 */
public final class CenterCommand extends Command implements PluginIdentifiableCommand {

    /**
     * One command path served by this command.
     *
     * @param key        internal key of the command, decides what happens; for a
     *                   command of a module this is the id of the module
     * @param tail       the path segments behind the registered name
     * @param permission required permission, empty for a public route
     * @param module     what to run for a command of a module, empty for a command of the core
     */
    public record Route(String key,
                        List<String> tail,
                        Optional<PermissionGate> permission,
                        Optional<ModuleCommand> module) {

        public Route {
            tail = List.copyOf(tail);
        }

        /**
         * @param key        internal key of the command
         * @param tail       the path segments behind the registered name
         * @param permission required permission, empty for a public route
         * @return a route of the core
         */
        public static Route core(final String key,
                                 final List<String> tail,
                                 final Optional<PermissionGate> permission) {
            return new Route(key, tail, permission, Optional.empty());
        }

        /**
         * @param moduleId id of the module
         * @param tail     the path segments behind the registered name
         * @param command  what the command does
         * @return a route of a module
         */
        public static Route module(final String moduleId, final List<String> tail, final ModuleCommand command) {
            return new Route(moduleId, tail, Optional.empty(), Optional.of(command));
        }
    }

    private final Plugin plugin;
    private final Language language;
    private final List<Route> routes;
    private final CenterMenu menu;
    private final ReloadAction reloadAction;
    private final ModuleAdminAction moduleAdmin;

    /**
     * @param plugin       owning plugin
     * @param name         registered command name, the first segment of every path
     * @param language     texts of the active configuration snapshot
     * @param routes       every path served by this command
     * @param menu         the Center-Info menu
     * @param reloadAction reload of the configuration
     * @param moduleAdmin  the module administration
     */
    public CenterCommand(final Plugin plugin,
                         final String name,
                         final Language language,
                         final List<Route> routes,
                         final CenterMenu menu,
                         final ReloadAction reloadAction,
                         final ModuleAdminAction moduleAdmin) {
        super(name, Center.PRODUCT_NAME + " " + Center.VERSION, "/" + name, List.of());
        this.plugin = plugin;
        this.language = language;
        // The longest path wins, so "center reload" is never swallowed by "center".
        this.routes = routes.stream()
                .sorted((left, right) -> Integer.compare(right.tail().size(), left.tail().size()))
                .toList();
        this.menu = menu;
        this.reloadAction = reloadAction;
        this.moduleAdmin = moduleAdmin;
    }

    @Override
    public Plugin getPlugin() {
        return plugin;
    }

    @Override
    public boolean execute(final CommandSender sender, final String label, final String[] args) {
        final List<String> input = normalized(args, args.length);
        final Optional<Route> match = routes.stream().filter(route -> matches(route, input)).findFirst();
        if (match.isEmpty()) {
            if (input.isEmpty()) {
                sendOverview(sender, label);
            } else {
                sender.sendMessage(Text.of(language.get(MessageKey.COMMAND_UNKNOWN, "path", label)));
            }
            return true;
        }

        final Route route = match.get();
        if (!isAllowed(sender, route)) {
            sender.sendMessage(Text.of(language.get(MessageKey.COMMAND_NO_PERMISSION)));
            return true;
        }

        if (route.module().isPresent()) {
            route.module().get().execute(new PaperCommandSender(sender));
            return true;
        }
        switch (route.key()) {
            case CommandsDefinition.CENTER_INFO_KEY -> openCenterInfo(sender, label);
            case CommandsDefinition.RELOAD_KEY -> reloadAction.performReload(sender, language);
            case CommandsDefinition.MODULES_KEY -> moduleAdmin.listModules(sender, language);
            case CommandsDefinition.MODULES_RELOAD_KEY -> moduleAdmin.reloadModules(sender, language);
            case CommandsDefinition.MODULES_ENABLE_KEY ->
                    moduleAdmin.enableModule(sender, language, argument(route, input), used(route, label));
            case CommandsDefinition.MODULES_DISABLE_KEY ->
                    moduleAdmin.disableModule(sender, language, argument(route, input), used(route, label));
            default -> sender.sendMessage(Text.of(language.get(MessageKey.COMMAND_UNKNOWN, "path", label)));
        }
        return true;
    }

    /** @return the first argument behind the fixed path, empty if it was not given. */
    private static String argument(final Route route, final List<String> input) {
        final int index = route.tail().size();
        return input.size() > index ? input.get(index) : "";
    }

    /**
     * The path the sender really used, so a usage hint names the configured
     * command and not a path that may have been renamed in {@code Commands.yml}.
     */
    private static String used(final Route route, final String label) {
        return route.tail().isEmpty() ? label : label + " " + String.join(" ", route.tail());
    }

    @Override
    public List<String> tabComplete(final CommandSender sender, final String alias, final String[] args) {
        if (args.length == 0) {
            return List.of();
        }
        final List<String> typed = normalized(args, args.length - 1);
        final Set<String> options = new LinkedHashSet<>();
        for (final Route route : routes) {
            if (!isAllowed(sender, route)) {
                continue;
            }
            final List<String> tail = route.tail();
            if (tail.equals(typed) && takesModuleId(route)) {
                // Behind "modules enable" and "modules disable" a module id follows.
                options.addAll(moduleAdmin.moduleIds());
                continue;
            }
            if (tail.size() <= typed.size() || !tail.subList(0, typed.size()).equals(typed)) {
                continue;
            }
            options.add(tail.get(typed.size()));
        }
        return StringUtil.copyPartialMatches(args[args.length - 1], options, new ArrayList<>());
    }

    private static boolean takesModuleId(final Route route) {
        return CommandsDefinition.MODULES_ENABLE_KEY.equals(route.key())
                || CommandsDefinition.MODULES_DISABLE_KEY.equals(route.key());
    }

    private void openCenterInfo(final CommandSender sender, final String label) {
        if (sender instanceof Player player) {
            menu.openCenterInfo(player);
            return;
        }
        // A menu cannot be opened in the console, so it gets the text overview.
        sendOverview(sender, label);
    }

    private void sendOverview(final CommandSender sender, final String label) {
        sender.sendMessage(Text.of(language.get(MessageKey.COMMAND_OVERVIEW_HEADER,
                "product", Center.PRODUCT_NAME, "version", Center.VERSION)));
        boolean any = false;
        for (final Route route : routes) {
            if (!isAllowed(sender, route)) {
                continue;
            }
            any = true;
            final String path = route.tail().isEmpty() ? label : label + " " + String.join(" ", route.tail());
            sender.sendMessage(Text.of(language.get(MessageKey.COMMAND_OVERVIEW_ENTRY,
                    "path", path, "description", describe(route))));
        }
        if (!any) {
            sender.sendMessage(Text.of(language.get(MessageKey.COMMAND_OVERVIEW_EMPTY)));
        }
    }

    private String describe(final Route route) {
        return switch (route.key()) {
            case CommandsDefinition.CENTER_INFO_KEY ->
                    language.get(MessageKey.COMMAND_DESCRIPTION_CENTER_INFO, "product", Center.PRODUCT_NAME);
            case CommandsDefinition.RELOAD_KEY ->
                    language.get(MessageKey.COMMAND_DESCRIPTION_RELOAD, "product", Center.PRODUCT_NAME);
            case CommandsDefinition.MODULES_KEY -> language.get(MessageKey.COMMAND_DESCRIPTION_MODULES);
            case CommandsDefinition.MODULES_RELOAD_KEY ->
                    language.get(MessageKey.COMMAND_DESCRIPTION_MODULES_RELOAD);
            case CommandsDefinition.MODULES_ENABLE_KEY ->
                    language.get(MessageKey.COMMAND_DESCRIPTION_MODULES_ENABLE);
            case CommandsDefinition.MODULES_DISABLE_KEY ->
                    language.get(MessageKey.COMMAND_DESCRIPTION_MODULES_DISABLE);
            default -> route.key();
        };
    }

    private static boolean matches(final Route route, final List<String> input) {
        final List<String> tail = route.tail();
        if (tail.isEmpty()) {
            // A path without further segments only answers the bare command.
            return input.isEmpty();
        }
        return input.size() >= tail.size() && input.subList(0, tail.size()).equals(tail);
    }

    private static boolean isAllowed(final CommandSender sender, final Route route) {
        return route.permission().isEmpty() || route.permission().get().allows(sender);
    }

    private static List<String> normalized(final String[] args, final int length) {
        final List<String> normalized = new ArrayList<>(length);
        for (int index = 0; index < length; index++) {
            normalized.add(args[index].toLowerCase(Locale.ROOT));
        }
        return List.copyOf(normalized);
    }
}
