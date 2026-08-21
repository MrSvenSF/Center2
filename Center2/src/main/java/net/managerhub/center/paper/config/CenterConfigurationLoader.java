package net.managerhub.center.paper.config;

import java.nio.file.Path;

import net.managerhub.center.Center;
import net.managerhub.center.common.command.CommandsDefinition;
import net.managerhub.center.common.config.ConfigurationException;
import net.managerhub.center.common.config.TransactionalConfiguration;
import net.managerhub.center.common.language.Language;

/**
 * Loads a complete configuration snapshot from the Paper data folder.
 *
 * <p>The loader has no side effects. It either returns a fully validated
 * snapshot or it throws, which is what makes the transactional reload in
 * {@link TransactionalConfiguration} possible.</p>
 *
 * <p>A menu that is switched off in {@code MainConfig.yml} also switches off the
 * command that belongs to it. The opposite never happens: a command that was
 * switched off in {@code Commands.yml} on purpose stays off, even when its menu
 * is switched on again.</p>
 */
public final class CenterConfigurationLoader implements TransactionalConfiguration.Loader<CenterConfiguration> {

    private final Path dataFolder;

    public CenterConfigurationLoader(final Path dataFolder) {
        this.dataFolder = dataFolder;
    }

    @Override
    public CenterConfiguration load() throws ConfigurationException {
        final MainSettings main = MainConfigLoader.load(dataFolder.resolve(Center.MAIN_CONFIG_FILE));
        final Language language = LanguageLoader.load(
                dataFolder.resolve(Center.LANGUAGE_DIRECTORY).resolve(Language.fileName(main.language())),
                main.language());
        final PermissionsSettings permissions =
                PermissionsConfigLoader.load(dataFolder.resolve(Center.PERMISSIONS_FILE));

        final Path menus = dataFolder.resolve(Center.MENUS_DIRECTORY);
        final CenterInfoMenuSettings centerInfo =
                MenuConfigLoader.loadCenterInfo(menus.resolve(Center.CENTER_INFO_MENU_FILE));
        final CenterAdminMenuSettings centerAdmin =
                MenuConfigLoader.loadCenterAdmin(menus.resolve(Center.CENTER_ADMIN_MENU_FILE));
        final ServerStatusMenuSettings serverStatus =
                MenuConfigLoader.loadServerStatus(menus.resolve(Center.SERVER_STATUS_MENU_FILE));

        CommandsDefinition commands = CommandsConfigLoader.load(dataFolder.resolve(Center.COMMANDS_FILE));
        if (!main.centerInfoMenuEnabled()) {
            commands = commands.withDisabled(CommandsDefinition.CENTER_INFO_KEY);
        }

        return new CenterConfiguration(main, permissions, commands, language,
                centerInfo, centerAdmin, serverStatus);
    }
}
