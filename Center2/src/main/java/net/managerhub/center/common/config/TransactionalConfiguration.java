package net.managerhub.center.common.config;

/**
 * Holds the currently active configuration and swaps it transactionally.
 *
 * <p>The reload works in this order:</p>
 * <ol>
 *   <li>load and validate the new configuration completely,</li>
 *   <li>activate it (register commands, publish new values),</li>
 *   <li>only then remember it as the current configuration.</li>
 * </ol>
 *
 * <p>If step 1 fails nothing at all changes. If step 2 fails the previous
 * configuration is activated again, so the last working state stays alive.</p>
 *
 * @param <T> immutable configuration snapshot type
 */
public final class TransactionalConfiguration<T> {

    /** Loads and fully validates a configuration snapshot. */
    @FunctionalInterface
    public interface Loader<T> {
        T load() throws ConfigurationException;
    }

    /** Applies a validated configuration snapshot to the running plugin. */
    @FunctionalInterface
    public interface Activator<T> {
        void activate(T configuration) throws ConfigurationException;
    }

    private final Loader<T> loader;
    private final Activator<T> activator;
    private volatile T current;

    public TransactionalConfiguration(final Loader<T> loader, final Activator<T> activator) {
        this.loader = loader;
        this.activator = activator;
    }

    /**
     * Loads and activates the configuration for the first time.
     *
     * @throws ConfigurationException if the configuration is invalid or cannot be activated
     */
    public void initialize() throws ConfigurationException {
        final T first = loader.load();
        activator.activate(first);
        this.current = first;
    }

    /** @return the currently active configuration. */
    public T current() {
        final T snapshot = this.current;
        if (snapshot == null) {
            throw new IllegalStateException("The configuration has not been initialized yet.");
        }
        return snapshot;
    }

    /** @return {@code true} once a configuration has been activated. */
    public boolean isInitialized() {
        return this.current != null;
    }

    /**
     * Reloads the configuration transactionally.
     *
     * @throws ConfigurationException if the new configuration is invalid or cannot be
     *                                activated; in both cases the previous configuration
     *                                stays active
     */
    public void reload() throws ConfigurationException {
        final T previous = current();
        final T next = loader.load();
        try {
            activator.activate(next);
        } catch (final ConfigurationException | RuntimeException failure) {
            rollback(previous, failure);
            throw new ConfigurationException(
                    "The new configuration could not be activated: " + describe(failure)
                            + " The previous configuration is still active.", failure);
        }
        this.current = next;
    }

    private void rollback(final T previous, final Throwable failure) {
        try {
            activator.activate(previous);
        } catch (final ConfigurationException | RuntimeException nested) {
            final IllegalStateException error = new IllegalStateException(
                    "The previous configuration could not be restored after a failed reload: " + describe(nested), nested);
            error.addSuppressed(failure);
            throw error;
        }
    }

    private static String describe(final Throwable failure) {
        final String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return failure.getClass().getSimpleName() + ".";
        }
        return message;
    }
}
