package net.managerhub.center.paper.config;

import java.nio.file.Path;

import net.managerhub.center.Center;
import net.managerhub.center.common.config.ConfigurationException;
import net.managerhub.center.common.language.Language;
import net.managerhub.center.common.remote.RemoteSettings;

/**
 * Reads and validates {@code MainConfig.yml}.
 *
 * <p>The file selects the language, switches the menus of MHCenter2 on and off and
 * carries the optional remote database of the network. The menu itself is
 * configured in {@code Menus/CenterInfo.yml}, the commands are configured in
 * {@code Commands.yml}.</p>
 *
 * <p>The {@code remote} section is read completely, whether it is switched on or
 * not. A value that is not a number or not a text is a configuration error even
 * with {@code remote.enabled: false}, exactly like everywhere else in MHCenter2:
 * a mistake must not stay hidden until somebody switches the section on. Whether
 * the values make sense <em>together</em> is decided by
 * {@link RemoteSettings#problems()}, so a half filled but switched off section
 * never blocks the start.</p>
 */
final class MainConfigLoader {

    private MainConfigLoader() {
        throw new AssertionError("No instances.");
    }

    static MainSettings load(final Path file) throws ConfigurationException {
        final YamlReader reader = YamlReader.read(file, Center.MAIN_CONFIG_FILE);
        reader.requireConfigVersion();

        final String language = Language.normalizeCode("language", reader.requireString("language"));
        final boolean centerInfoMenuEnabled = reader.requireBoolean("menus.center-info.enabled");

        return new MainSettings(language, centerInfoMenuEnabled, remote(reader));
    }

    /**
     * Reads the {@code remote} section.
     *
     * <p>{@code server-id} and {@code password} may be empty - a node that does
     * not use the remote system has no id, and a database without a password is
     * unusual but possible - so both are read as optional text. Everything else
     * has to be there.</p>
     */
    private static RemoteSettings remote(final YamlReader reader) throws ConfigurationException {
        return new RemoteSettings(
                reader.requireBoolean("remote.enabled"),
                RemoteSettings.normalizeServerId(reader.optionalString("remote.server-id")),
                new RemoteSettings.Database(
                        reader.requireString("remote.database.host"),
                        reader.requireInt("remote.database.port"),
                        reader.requireString("remote.database.database"),
                        reader.requireString("remote.database.username"),
                        reader.optionalString("remote.database.password"),
                        reader.requireBoolean("remote.database.ssl")),
                new RemoteSettings.Polling(
                        reader.requireInt("remote.polling.interval-ms"),
                        reader.requireInt("remote.polling.action-ttl-seconds")),
                new RemoteSettings.Heartbeat(
                        reader.requireInt("remote.heartbeat.interval-seconds")));
    }
}
