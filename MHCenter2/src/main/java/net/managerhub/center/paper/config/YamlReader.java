package net.managerhub.center.paper.config;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.managerhub.center.Center;
import net.managerhub.center.common.config.ConfigurationException;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Small typed reader around {@link YamlConfiguration}.
 *
 * <p>Every accessor produces a concrete error message that names the file, the
 * configuration path and the offending value, because those messages are shown
 * to the administrator who runs the reload.</p>
 */
final class YamlReader {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();
    private static final Pattern UNESCAPED_TAG = Pattern.compile("(?<!\\\\)<[^<>]+>");

    private final String fileName;
    private final YamlConfiguration yaml;

    private YamlReader(final String fileName, final YamlConfiguration yaml) {
        this.fileName = fileName;
        this.yaml = yaml;
    }

    /**
     * Reads and parses a YAML file.
     *
     * @param file     absolute file location
     * @param fileName visible file name used in error messages
     * @return the reader
     * @throws ConfigurationException if the file cannot be read or is not valid YAML
     */
    static YamlReader read(final Path file, final String fileName) throws ConfigurationException {
        final YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file.toFile());
        } catch (final IOException failure) {
            throw new ConfigurationException(fileName + " could not be read: " + failure.getMessage(), failure);
        } catch (final InvalidConfigurationException failure) {
            throw new ConfigurationException(fileName + " is not valid YAML: " + failure.getMessage(), failure);
        }
        return new YamlReader(fileName, yaml);
    }

    /**
     * Checks that the file declares the expected configuration version.
     *
     * @throws ConfigurationException if the version is missing or different
     */
    void requireConfigVersion() throws ConfigurationException {
        if (!yaml.isInt("config-version")) {
            throw error("'config-version' is missing or is not a number. Expected: " + Center.CONFIG_VERSION + ".");
        }
        final int version = yaml.getInt("config-version");
        if (version != Center.CONFIG_VERSION) {
            throw error("'config-version' is " + version + ", but this " + Center.PRODUCT_NAME + " version needs "
                    + Center.CONFIG_VERSION + ".");
        }
    }

    ConfigurationSection requireSection(final String path) throws ConfigurationException {
        final ConfigurationSection section = yaml.getConfigurationSection(path);
        if (section == null) {
            throw error("the section '" + path + "' is missing.");
        }
        return section;
    }

    String requireString(final String path) throws ConfigurationException {
        if (!yaml.isString(path)) {
            throw error("'" + path + "' is missing or is not a text value.");
        }
        final String value = yaml.getString(path);
        if (value == null || value.isBlank()) {
            throw error("'" + path + "' must not be empty.");
        }
        return value;
    }

    /**
     * Reads a text value that may be empty.
     *
     * <p>Not every text has to carry something: a node without the remote system
     * has no server-id, and a database user may have no password. Both are still
     * entries of the file and have to exist as a key.</p>
     *
     * @param path configuration path
     * @return the value, or an empty text if the key holds nothing
     * @throws ConfigurationException if the key is missing or is not a text value
     */
    String optionalString(final String path) throws ConfigurationException {
        if (!yaml.contains(path)) {
            throw error("'" + path + "' is missing.");
        }
        if (yaml.get(path) == null) {
            return "";
        }
        if (!yaml.isString(path)) {
            throw error("'" + path + "' is not a text value.");
        }
        final String value = yaml.getString(path);
        return value == null ? "" : value;
    }

    String requireMiniMessage(final String path) throws ConfigurationException {
        final String value = requireString(path);
        validateMiniMessage(path, value);
        return value;
    }

    void validateMiniMessage(final String path, final String value) throws ConfigurationException {
        final Component parsed;
        try {
            parsed = MINI_MESSAGE.deserialize(value);
        } catch (final RuntimeException failure) {
            throw new ConfigurationException(fileName + ": '" + path
                    + "' is not valid MiniMessage: " + failure.getMessage(), failure);
        }

        // MiniMessage intentionally renders unknown tags as literal text, even in
        // strict mode. Treat such unresolved configuration tags as an error so a
        // mistyped gradient or color cannot silently appear in chat. Administrators
        // can still render a tag literally by escaping its opening bracket.
        final String plainText = PLAIN_TEXT.serialize(parsed);
        final Matcher matcher = UNESCAPED_TAG.matcher(value);
        while (matcher.find()) {
            final String candidate = matcher.group();
            if (plainText.contains(candidate)) {
                throw error("'" + path + "' contains the unknown or invalid MiniMessage tag "
                        + candidate + ". Escape it as \\" + candidate + " if it should be visible text.");
            }
        }
    }

    boolean requireBoolean(final String path) throws ConfigurationException {
        if (!yaml.isBoolean(path)) {
            throw error("'" + path + "' is missing or is not true/false.");
        }
        return yaml.getBoolean(path);
    }

    int requireInt(final String path) throws ConfigurationException {
        if (!yaml.isInt(path)) {
            throw error("'" + path + "' is missing or is not a whole number.");
        }
        return yaml.getInt(path);
    }

    int requireIntInRange(final String path, final int minimum, final int maximum) throws ConfigurationException {
        final int value = requireInt(path);
        if (value < minimum || value > maximum) {
            throw error("'" + path + "' is " + value + ", allowed is " + minimum + " to " + maximum + ".");
        }
        return value;
    }

    /**
     * Reads every text value of the file, including the nested ones.
     *
     * <p>Sections themselves are skipped, so the result only contains the leaves
     * of the file as {@code path -> text}.</p>
     *
     * @param ignoredPaths paths that are not part of the result
     * @return every text value of the file
     * @throws ConfigurationException if a leaf is not a text value
     */
    Map<String, String> leafTexts(final String... ignoredPaths) throws ConfigurationException {
        final Set<String> ignored = Set.of(ignoredPaths);
        final Map<String, String> texts = new LinkedHashMap<>();
        for (final String path : yaml.getKeys(true)) {
            if (ignored.contains(path) || yaml.isConfigurationSection(path)) {
                continue;
            }
            if (!yaml.isString(path)) {
                throw error("'" + path + "' is missing or is not a text value.");
            }
            texts.put(path, yaml.getString(path));
        }
        return texts;
    }

    /**
     * Checks that a section only contains the expected keys.
     *
     * @param path     path of the section
     * @param expected the only allowed keys
     * @throws ConfigurationException if a key is missing or unknown
     */
    void requireExactKeys(final String path, final Set<String> expected) throws ConfigurationException {
        final Set<String> present = requireSection(path).getKeys(false);
        for (final String key : expected) {
            if (!present.contains(key)) {
                throw error("the entry '" + path + "." + key + "' is missing.");
            }
        }
        for (final String key : present) {
            if (!expected.contains(key)) {
                throw error("'" + path + "' contains the unknown entry '" + key + "'. " + Center.PRODUCT_NAME
                        + " only knows: " + String.join(", ", expected) + ".");
            }
        }
    }

    /**
     * Reads an optional list of text values.
     *
     * <p>A missing key and a key without a value are both read as "no entries",
     * so {@code aliases:} may be left out completely or may stay empty.</p>
     *
     * @return the list, empty if the path is absent
     * @throws ConfigurationException if the path exists but is not a list of text values
     */
    List<String> optionalStringList(final String path) throws ConfigurationException {
        if (!yaml.contains(path)) {
            return List.of();
        }
        if (!yaml.isList(path)) {
            throw error("'" + path + "' must be a list.");
        }
        final List<?> raw = yaml.getList(path, List.of());
        for (final Object element : raw) {
            if (!(element instanceof String)) {
                throw error("'" + path + "' must only contain text values, found: " + element + ".");
            }
        }
        return yaml.getStringList(path);
    }

    Material requireMaterial(final String path) throws ConfigurationException {
        final String raw = requireString(path);
        final Material material = Material.matchMaterial(raw);
        if (material == null) {
            throw error("'" + path + "' contains the unknown material \"" + raw + "\".");
        }
        if (!material.isItem() || material.isAir()) {
            throw error("'" + path + "' contains the material \"" + raw + "\", which cannot be used as a menu item.");
        }
        return material;
    }

    ConfigurationException error(final String message) {
        return new ConfigurationException(fileName + ": " + message);
    }
}
