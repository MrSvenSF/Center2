package net.managerhub.center.velocity.config;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import net.managerhub.center.Center;
import net.managerhub.center.common.config.ConfigurationException;
import net.managerhub.center.common.language.Language;
import net.managerhub.center.common.remote.RemoteSettings;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

/**
 * Reads the configuration files Center2 uses on Velocity.
 *
 * <p>The proxy has no menu and no {@code Commands.yml}, so it only reads
 * {@code MainConfig.yml} and the selected file of {@code Language/}. YAML is
 * parsed with Configurate, which the Velocity API provides; the Paper side uses
 * the YAML reader of Bukkit instead.</p>
 *
 * <p>The loader has no side effects: it either returns a complete, validated
 * snapshot or it throws. That is what makes the reload on the proxy safe - a
 * broken file leaves the running configuration exactly as it was.</p>
 */
public final class VelocityConfigLoader {

    private static final String CONFIG_VERSION = "config-version";
    private static final String LANGUAGE = "language";

    private VelocityConfigLoader() {
        throw new AssertionError("No instances.");
    }

    /**
     * Loads everything Center2 needs on the proxy.
     *
     * @param dataDirectory data folder of Center2 on the proxy
     * @return the validated snapshot
     * @throws ConfigurationException if a file is missing, invalid or incomplete
     */
    public static ProxyConfiguration load(final Path dataDirectory) throws ConfigurationException {
        final ConfigurationNode main = read(dataDirectory.resolve(Center.MAIN_CONFIG_FILE), Center.MAIN_CONFIG_FILE);
        requireConfigVersion(main, Center.MAIN_CONFIG_FILE);
        final String code = Language.normalizeCode(LANGUAGE, main.node(LANGUAGE).getString());

        final String fileName = Language.fileName(code);
        final ConfigurationNode texts = read(
                dataDirectory.resolve(Center.LANGUAGE_DIRECTORY).resolve(fileName), fileName);
        requireConfigVersion(texts, fileName);

        final Map<String, String> flat = new LinkedHashMap<>();
        collect(texts, "", fileName, flat);
        flat.remove(CONFIG_VERSION);
        return new ProxyConfiguration(Language.of(code, fileName, flat), remote(main));
    }

    /**
     * Loads only the language.
     *
     * @param dataDirectory data folder of Center2 on the proxy
     * @return the validated texts of the selected language
     * @throws ConfigurationException if a file is missing, invalid or incomplete
     */
    public static Language loadLanguage(final Path dataDirectory) throws ConfigurationException {
        return load(dataDirectory).language();
    }

    /**
     * Reads the {@code remote} section of {@code MainConfig.yml}.
     *
     * <p>Every entry has to be there, whether the section is switched on or not:
     * a typo must not stay hidden until somebody switches the remote system on.
     * Whether the values work together is decided later by
     * {@link RemoteSettings#problems()}.</p>
     */
    private static RemoteSettings remote(final ConfigurationNode main) throws ConfigurationException {
        final ConfigurationNode remote = main.node("remote");
        final ConfigurationNode database = remote.node("database");
        final ConfigurationNode polling = remote.node("polling");
        final ConfigurationNode heartbeat = remote.node("heartbeat");
        return new RemoteSettings(
                requireBoolean(remote, "remote.enabled", "enabled"),
                RemoteSettings.normalizeServerId(optionalText(remote, "remote.server-id", "server-id")),
                new RemoteSettings.Database(
                        requireText(database, "remote.database.host", "host"),
                        requireInt(database, "remote.database.port", "port"),
                        requireText(database, "remote.database.database", "database"),
                        requireText(database, "remote.database.username", "username"),
                        optionalText(database, "remote.database.password", "password"),
                        requireBoolean(database, "remote.database.ssl", "ssl")),
                new RemoteSettings.Polling(
                        requireInt(polling, "remote.polling.interval-ms", "interval-ms"),
                        requireInt(polling, "remote.polling.action-ttl-seconds", "action-ttl-seconds")),
                new RemoteSettings.Heartbeat(
                        requireInt(heartbeat, "remote.heartbeat.interval-seconds", "interval-seconds")));
    }

    private static String requireText(final ConfigurationNode parent,
                                      final String path,
                                      final String key) throws ConfigurationException {
        final String value = parent.node(key).getString();
        if (value == null || value.isBlank()) {
            throw missing(path, "a text value");
        }
        return value;
    }

    private static String optionalText(final ConfigurationNode parent,
                                       final String path,
                                       final String key) throws ConfigurationException {
        final ConfigurationNode node = parent.node(key);
        if (node.virtual()) {
            throw missing(path, "a text value");
        }
        final String value = node.getString();
        return value == null ? "" : value;
    }

    private static int requireInt(final ConfigurationNode parent,
                                  final String path,
                                  final String key) throws ConfigurationException {
        final ConfigurationNode node = parent.node(key);
        final int value = node.getInt(Integer.MIN_VALUE);
        if (node.virtual() || value == Integer.MIN_VALUE) {
            throw missing(path, "a whole number");
        }
        return value;
    }

    private static boolean requireBoolean(final ConfigurationNode parent,
                                          final String path,
                                          final String key) throws ConfigurationException {
        final ConfigurationNode node = parent.node(key);
        if (node.virtual() || node.getString() == null) {
            throw missing(path, "true/false");
        }
        return node.getBoolean();
    }

    private static ConfigurationException missing(final String path, final String expected) {
        return new ConfigurationException(Center.MAIN_CONFIG_FILE + ": '" + path
                + "' is missing or is not " + expected + ".");
    }

    private static ConfigurationNode read(final Path file, final String fileName) throws ConfigurationException {
        try {
            return YamlConfigurationLoader.builder().path(file).build().load();
        } catch (final ConfigurateException failure) {
            throw new ConfigurationException(fileName + " could not be read: " + failure.getMessage(), failure);
        }
    }

    private static void requireConfigVersion(final ConfigurationNode root,
                                             final String fileName) throws ConfigurationException {
        final int version = root.node(CONFIG_VERSION).getInt(Integer.MIN_VALUE);
        if (version == Integer.MIN_VALUE) {
            throw new ConfigurationException(fileName + ": '" + CONFIG_VERSION
                    + "' is missing or is not a number. Expected: " + Center.CONFIG_VERSION + ".");
        }
        if (version != Center.CONFIG_VERSION) {
            throw new ConfigurationException(fileName + ": '" + CONFIG_VERSION + "' is " + version + ", but this "
                    + Center.PRODUCT_NAME + " version needs " + Center.CONFIG_VERSION + ".");
        }
    }

    private static void collect(final ConfigurationNode node,
                                final String prefix,
                                final String fileName,
                                final Map<String, String> texts) throws ConfigurationException {
        for (final Map.Entry<Object, ? extends ConfigurationNode> child : node.childrenMap().entrySet()) {
            final String path = prefix.isEmpty() ? String.valueOf(child.getKey()) : prefix + "." + child.getKey();
            final ConfigurationNode value = child.getValue();
            if (value.isMap()) {
                collect(value, path, fileName, texts);
                continue;
            }
            final String text = value.getString();
            if (text == null) {
                throw new ConfigurationException(fileName + ": '" + path + "' is missing or is not a text value.");
            }
            texts.put(path, text);
        }
    }
}
