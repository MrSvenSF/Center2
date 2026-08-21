package net.managerhub.center.common.db;

/**
 * Thrown when the local SQLite database cannot be prepared or used.
 *
 * <p>The message is written for an administrator and names the relative file
 * {@code DB/Center.db} instead of an absolute path.</p>
 */
public class DatabaseException extends Exception {

    private static final long serialVersionUID = 1L;

    public DatabaseException(final String message) {
        super(message);
    }

    public DatabaseException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
