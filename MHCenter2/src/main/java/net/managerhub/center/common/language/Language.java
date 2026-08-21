package net.managerhub.center.common.language;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.managerhub.center.Center;
import net.managerhub.center.common.config.ConfigurationException;

/**
 * All texts of one language, read from {@code Language/<CODE>.yml}.
 *
 * <p>The class is platform neutral: the platform specific loader only reads the
 * raw {@code path -> text} pairs out of the file, the validation and the
 * placeholder handling happen here. A language file must contain exactly the
 * paths of {@link MessageKey}, so {@code DE.yml} and {@code EN.yml} always have
 * the same keys.</p>
 */
public final class Language {

    /** The supported language codes. */
    public static final List<String> SUPPORTED = List.of("DE", "EN");

    private final String code;
    private final Map<MessageKey, String> messages;

    private Language(final String code, final Map<MessageKey, String> messages) {
        this.code = code;
        this.messages = messages;
    }

    /**
     * Validates a configured language code.
     *
     * @param path configuration path, used for the error message
     * @param raw  raw value of {@code language} in {@code MainConfig.yml}
     * @return the normalized (upper case) code
     * @throws ConfigurationException if the code is empty or not supported
     */
    public static String normalizeCode(final String path, final String raw) throws ConfigurationException {
        if (raw == null || raw.isBlank()) {
            throw new ConfigurationException(Center.MAIN_CONFIG_FILE + ": '" + path
                    + "' is missing or empty. Supported languages: " + String.join(", ", SUPPORTED) + ".");
        }
        final String code = raw.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED.contains(code)) {
            throw new ConfigurationException(Center.MAIN_CONFIG_FILE + ": '" + path + "' is \"" + raw
                    + "\", but only " + String.join(" and ", SUPPORTED) + " are supported.");
        }
        return code;
    }

    /** @return the file name of a language, for example {@code DE.yml}. */
    public static String fileName(final String code) {
        return code + ".yml";
    }

    /**
     * Builds a language from the raw content of a language file.
     *
     * @param code     normalized language code
     * @param fileName visible file name, used in error messages
     * @param raw      every {@code path -> text} pair of the file
     * @return the validated language
     * @throws ConfigurationException if a key is missing, empty or unknown
     */
    public static Language of(final String code,
                              final String fileName,
                              final Map<String, String> raw) throws ConfigurationException {
        final Map<MessageKey, String> messages = new EnumMap<>(MessageKey.class);
        final List<String> missing = new ArrayList<>();
        for (final MessageKey key : MessageKey.values()) {
            final String text = raw.get(key.path());
            if (text == null || text.isBlank()) {
                missing.add(key.path());
                continue;
            }
            messages.put(key, text);
        }
        if (!missing.isEmpty()) {
            throw new ConfigurationException(fileName + ": the following texts are missing or empty: "
                    + String.join(", ", missing) + ".");
        }

        final List<String> unknown = new ArrayList<>(raw.keySet());
        unknown.removeAll(Arrays.stream(MessageKey.values()).map(MessageKey::path).toList());
        if (!unknown.isEmpty()) {
            throw new ConfigurationException(fileName + ": the following texts are unknown to this "
                    + Center.PRODUCT_NAME + " version: " + String.join(", ", unknown) + ".");
        }

        return new Language(code, messages);
    }

    /** @return the language code, for example {@code DE}. */
    public String code() {
        return code;
    }

    /**
     * Reads a text and replaces its placeholders.
     *
     * @param key          the wanted text
     * @param placeholders placeholder name and value in pairs, for example
     *                     {@code "product", "MHCenter2"} for {@code {product}}
     * @return the finished text
     */
    public String get(final MessageKey key, final String... placeholders) {
        if (placeholders.length % 2 != 0) {
            throw new IllegalArgumentException("Placeholders must be given as name and value pairs.");
        }
        String text = messages.get(key);
        for (int i = 0; i < placeholders.length; i += 2) {
            text = text.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
        }
        return text;
    }
}
