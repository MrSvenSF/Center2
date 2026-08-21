package net.managerhub.center.common.config;

/**
 * Thrown when a Center2 configuration value is missing or invalid.
 *
 * <p>The message is meant to be shown to an administrator directly, so it always
 * names the file, the configuration path and the offending value.</p>
 */
public class ConfigurationException extends Exception {

    private static final long serialVersionUID = 1L;

    public ConfigurationException(final String message) {
        super(message);
    }

    public ConfigurationException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
