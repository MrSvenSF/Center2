package net.managerhub.center.common.command;

import java.util.Locale;
import java.util.regex.Pattern;

import net.managerhub.center.common.config.ConfigurationException;

/**
 * Validation of configurable command labels and aliases.
 *
 * <p>Names are stored without a leading slash and are treated case insensitively,
 * so {@code Center} and {@code center} can never be registered next to each
 * other.</p>
 */
public final class CommandNames {

    /** Maximum length of a command label or alias. */
    public static final int MAX_LENGTH = 32;

    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9_-]{1," + MAX_LENGTH + "}");

    private CommandNames() {
        throw new AssertionError("No instances.");
    }

    /**
     * Validates a command label or alias and returns its normalized (lower case) form.
     *
     * @param path configuration path, used for the error message
     * @param raw  raw value from {@code Commands.yml}
     * @return the normalized name
     * @throws ConfigurationException if the name is invalid
     */
    public static String validate(final String path, final String raw) throws ConfigurationException {
        if (raw == null || raw.isBlank()) {
            throw invalid(path, String.valueOf(raw), "it must not be empty");
        }
        if (raw.startsWith("/")) {
            throw invalid(path, raw, "command names are configured without a leading slash");
        }
        for (int i = 0; i < raw.length(); i++) {
            if (Character.isWhitespace(raw.charAt(i))) {
                throw invalid(path, raw, "it must not contain spaces");
            }
        }
        if (raw.indexOf(':') >= 0) {
            throw invalid(path, raw, "':' is reserved for the namespace of the server and must not be used");
        }
        if (raw.length() > MAX_LENGTH) {
            throw invalid(path, raw, "it must not be longer than " + MAX_LENGTH + " characters");
        }
        if (!VALID.matcher(raw).matches()) {
            throw invalid(path, raw, "allowed characters are a-z, A-Z, 0-9, '_' and '-'");
        }
        return raw.toLowerCase(Locale.ROOT);
    }

    private static ConfigurationException invalid(final String path, final String value, final String reason) {
        return new ConfigurationException(
                "Commands.yml: '" + path + "' contains the invalid command name \"" + value + "\": " + reason + ".");
    }
}
