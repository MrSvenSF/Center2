package net.managerhub.center.common.module;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Pattern;

import net.managerhub.center.Center;
import net.managerhub.center.api.ModulePlatform;
import net.managerhub.center.common.config.ConfigurationException;
import net.managerhub.center.common.util.Version;

/**
 * The metadata of one module, read from {@code center-module.properties} inside
 * the module jar.
 *
 * <pre>
 * id=MyModule
 * name=My Module
 * version=1.0
 * author=Someone
 * main=com.example.mymodule.MyModule
 * platform=PAPER
 * center-min-version=0.2.0
 * center-max-version=0.2.99
 * minecraft-min-version=1.21.4
 * minecraft-max-version=1.21.11
 * </pre>
 *
 * <p>The file is read as UTF-8, so a name or an author may contain any character.</p>
 *
 * <p>Every module names the Center2 versions it supports. A module that runs on
 * Paper ({@code PAPER} or {@code BOTH}) additionally names the Minecraft versions
 * it supports; a {@code VELOCITY} module may leave them out, because the proxy has
 * no single Minecraft version of its own.</p>
 *
 * @param id                the short name of the module, also the name of its config folder
 * @param name              visible name
 * @param version           version of the module
 * @param author            who wrote the module
 * @param mainClass         class that implements {@link CenterModule}
 * @param platform          the platform the module supports
 * @param centerVersions    the Center2 versions the module supports
 * @param minecraftVersions the Minecraft versions the module supports, empty for a proxy only module
 */
public record ModuleDescriptor(String id,
                               String name,
                               String version,
                               String author,
                               String mainClass,
                               ModulePlatform platform,
                               VersionRange centerVersions,
                               Optional<VersionRange> minecraftVersions) {

    /** Metadata entry with the oldest supported Center2 version. */
    public static final String CENTER_MIN_VERSION = "center-min-version";

    /** Metadata entry with the newest supported Center2 version. */
    public static final String CENTER_MAX_VERSION = "center-max-version";

    /** Metadata entry with the oldest supported Minecraft version. */
    public static final String MINECRAFT_MIN_VERSION = "minecraft-min-version";

    /** Metadata entry with the newest supported Minecraft version. */
    public static final String MINECRAFT_MAX_VERSION = "minecraft-max-version";

    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Pattern CLASS_NAME = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*");

    /**
     * Reads and validates the metadata of a module.
     *
     * @param source visible name of the module jar, used in the error messages
     * @param in     content of {@code center-module.properties}
     * @return the validated metadata
     * @throws ConfigurationException if a value is missing or invalid
     */
    public static ModuleDescriptor read(final String source, final InputStream in) throws ConfigurationException {
        final Properties properties = new Properties();
        // Read as UTF-8, not as the ISO-8859-1 default of Properties, so a module
        // name or an author with umlauts arrives the way it was written.
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (final IOException failure) {
            throw new ConfigurationException(source + ": '" + Center.MODULE_DESCRIPTOR_FILE
                    + "' could not be read: " + failure.getMessage(), failure);
        }

        final String id = require(source, properties, "id");
        if (!ID.matcher(id).matches()) {
            throw invalid(source, "id", id, "allowed characters are a-z, A-Z, 0-9, '_' and '-'");
        }
        final String mainClass = require(source, properties, "main");
        if (!CLASS_NAME.matcher(mainClass).matches()) {
            throw invalid(source, "main", mainClass, "it must be a fully qualified class name");
        }
        final ModulePlatform platform = platform(source, require(source, properties, "platform"));

        return new ModuleDescriptor(
                id,
                require(source, properties, "name"),
                require(source, properties, "version"),
                require(source, properties, "author"),
                mainClass,
                platform,
                range(source, properties, CENTER_MIN_VERSION, CENTER_MAX_VERSION, true).orElseThrow(),
                range(source, properties, MINECRAFT_MIN_VERSION, MINECRAFT_MAX_VERSION,
                        platform.supports(ModulePlatform.PAPER)));
    }

    /**
     * @param running the platform Center2 is running on
     * @return {@code true} if this module belongs on that platform
     */
    public boolean supportsPlatform(final ModulePlatform running) {
        return platform.supports(running);
    }

    /**
     * @param centerVersion version of the running Center2
     * @return {@code true} if this module supports that Center2 version
     */
    public boolean supportsCenter(final Version centerVersion) {
        return centerVersions.includes(centerVersion);
    }

    /**
     * @param minecraftVersion version of the running Minecraft server
     * @return {@code true} if this module supports that Minecraft version; a module
     *         without a Minecraft range never blocks on this check
     */
    public boolean supportsMinecraft(final Version minecraftVersion) {
        return minecraftVersions.map(range -> range.includes(minecraftVersion)).orElse(true);
    }

    /**
     * Reads one version range.
     *
     * @param required whether the range has to be present
     * @return the range, or empty if it is not required and not present
     */
    private static Optional<VersionRange> range(final String source,
                                                final Properties properties,
                                                final String minimumKey,
                                                final String maximumKey,
                                                final boolean required) throws ConfigurationException {
        final String rawMinimum = properties.getProperty(minimumKey);
        final String rawMaximum = properties.getProperty(maximumKey);
        if (!required && isBlank(rawMinimum) && isBlank(rawMaximum)) {
            return Optional.empty();
        }

        final Version minimum = version(source, minimumKey, require(source, properties, minimumKey));
        final Version maximum = version(source, maximumKey, require(source, properties, maximumKey));
        if (minimum.isAfter(maximum)) {
            throw invalid(source, minimumKey, minimum.display(),
                    "it is newer than '" + maximumKey + "' (" + maximum.display() + ")");
        }
        return Optional.of(new VersionRange(minimum, maximum));
    }

    private static Version version(final String source,
                                   final String key,
                                   final String raw) throws ConfigurationException {
        return Version.of(raw).orElseThrow(() ->
                invalid(source, key, raw, "a version looks like \"1.21.4\""));
    }

    private static ModulePlatform platform(final String source, final String raw) throws ConfigurationException {
        try {
            return ModulePlatform.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException unknown) {
            throw invalid(source, "platform", raw, "allowed are PAPER, VELOCITY and BOTH");
        }
    }

    private static String require(final String source,
                                  final Properties properties,
                                  final String key) throws ConfigurationException {
        final String value = properties.getProperty(key);
        if (isBlank(value)) {
            throw new ConfigurationException(source + ": '" + Center.MODULE_DESCRIPTOR_FILE + "' is missing the entry '"
                    + key + "'.");
        }
        return value.trim();
    }

    private static boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }

    private static ConfigurationException invalid(final String source,
                                                  final String key,
                                                  final String value,
                                                  final String reason) {
        return new ConfigurationException(source + ": '" + key + "' is \"" + value + "\": " + reason + ".");
    }
}
