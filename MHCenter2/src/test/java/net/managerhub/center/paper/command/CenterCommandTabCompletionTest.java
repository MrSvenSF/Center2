package net.managerhub.center.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.managerhub.center.common.command.CommandsDefinition;
import net.managerhub.center.common.language.Language;
import net.managerhub.center.paper.config.PermissionSetting;
import net.managerhub.center.paper.config.PermissionsSettings;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CenterCommandTabCompletionTest {

    private static final String MASTER = "center.admin.*";
    private static final String ADMIN = "center.admin";
    private static final String MODULES = "center.admin.modules";
    private static final String MODULES_RELOAD = "center.admin.modules.reload";
    private static final String MODULES_ENABLE = "center.admin.modules.enable";

    private static final PermissionsSettings PERMISSIONS = new PermissionsSettings(
            new PermissionSetting(MASTER, false),
            new PermissionSetting(ADMIN, false),
            new PermissionSetting("center.admin.reload", false),
            new PermissionSetting(MODULES, false),
            new PermissionSetting(MODULES_RELOAD, false),
            new PermissionSetting(MODULES_ENABLE, false),
            new PermissionSetting("center.admin.modules.disable", false));

    @Test
    @DisplayName("a player without the admin gate gets no module suggestion")
    void suggestsNothingWithoutTheAdminGate() throws Exception {
        // The public info command stays visible, the administration does not.
        assertEquals(List.of("info"), complete(player(), "center", ""));
        // Even the exact sub permission alone must not make the command visible.
        assertEquals(List.of("info"), complete(player(MODULES_RELOAD), "center", ""));
        assertEquals(List.of("info"), complete(player(MODULES, MODULES_RELOAD), "center", ""));
        assertEquals(List.of(), complete(player(MODULES, MODULES_RELOAD), "center", "modules", ""));
    }

    @Test
    @DisplayName("a player only sees the module actions he is allowed to use")
    void suggestsOnlyTheAllowedActions() throws Exception {
        final Player viewer = player(ADMIN, MODULES, MODULES_RELOAD);

        assertTrue(complete(viewer, "center", "").contains("modules"));
        assertEquals(List.of("reload"), complete(viewer, "center", "modules", ""));
    }

    @Test
    @DisplayName("the master permission shows every module action")
    void suggestsEveryActionForTheMaster() throws Exception {
        final List<String> actions = complete(player(MASTER), "center", "modules", "");

        assertEquals(Set.of("reload", "enable", "disable"), Set.copyOf(actions));
    }

    @Test
    @DisplayName("the module ids follow the enable action")
    void suggestsTheModuleIds() throws Exception {
        final Player viewer = player(ADMIN, MODULES, MODULES_ENABLE);

        assertEquals(List.of("TestModule"), complete(viewer, "center", "modules", "enable", ""));
        assertTrue(complete(viewer, "center", "modules", "reload", "").isEmpty(),
                "reload takes no module id and is not allowed for this player");
    }

    /**
     * @param sender who presses tab
     * @param label  the typed command name
     * @param args   the typed arguments, the last one is the incomplete word
     * @return what MHCenter2 suggests
     */
    private static List<String> complete(final CommandSender sender,
                                         final String label,
                                         final String... args) throws Exception {
        return command().tabComplete(sender, label, args);
    }

    /** The command tree MHCenter2 builds from the default configuration. */
    private static CenterCommand command() throws Exception {
        final List<CenterCommand.Route> routes = List.of(
                CenterCommand.Route.core(CommandsDefinition.CENTER_INFO_KEY, List.of("info"), Optional.empty()),
                CenterCommand.Route.core(CommandsDefinition.RELOAD_KEY, List.of("reload"),
                        Optional.of(PERMISSIONS.reloadGate())),
                CenterCommand.Route.core(CommandsDefinition.MODULES_KEY, List.of("modules"),
                        Optional.of(PERMISSIONS.modulesGate())),
                CenterCommand.Route.core(CommandsDefinition.MODULES_RELOAD_KEY, List.of("modules", "reload"),
                        Optional.of(PERMISSIONS.modulesReloadGate())),
                CenterCommand.Route.core(CommandsDefinition.MODULES_ENABLE_KEY, List.of("modules", "enable"),
                        Optional.of(PERMISSIONS.modulesEnableGate())),
                CenterCommand.Route.core(CommandsDefinition.MODULES_DISABLE_KEY, List.of("modules", "disable"),
                        Optional.of(PERMISSIONS.modulesDisableGate())));
        return new CenterCommand(null, "center", bundled(), routes, null, null, new TestModuleAdmin());
    }

    /** Only the module ids are needed here, nothing else is ever called. */
    private static final class TestModuleAdmin implements ModuleAdminAction {

        @Override
        public void listModules(final CommandSender sender, final Language language) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void reloadModules(final CommandSender sender, final Language language) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void enableModule(final CommandSender sender,
                                 final Language language,
                                 final String moduleId,
                                 final String commandPath) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void disableModule(final CommandSender sender,
                                  final Language language,
                                  final String moduleId,
                                  final String commandPath) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> moduleIds() {
            return List.of("TestModule");
        }
    }

    private static Player player(final String... granted) {
        final Set<String> nodes = Set.of(granted);
        return (Player) Proxy.newProxyInstance(
                CenterCommandTabCompletionTest.class.getClassLoader(),
                new Class<?>[] {Player.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "isPermissionSet", "hasPermission" -> nodes.contains(String.valueOf(arguments[0]));
                    case "toString" -> "Player" + nodes;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static Language bundled() throws Exception {
        final YamlConfiguration yaml = new YamlConfiguration();
        try (InputStream in = CenterCommandTabCompletionTest.class.getClassLoader()
                .getResourceAsStream("defaults/language/" + Language.fileName("EN"));
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            yaml.load(reader);
        }
        final Map<String, String> texts = new LinkedHashMap<>();
        for (final String path : yaml.getKeys(true)) {
            if (!yaml.isConfigurationSection(path) && !"config-version".equals(path)) {
                texts.put(path, yaml.getString(path));
            }
        }
        return Language.of("EN", Language.fileName("EN"), texts);
    }
}
