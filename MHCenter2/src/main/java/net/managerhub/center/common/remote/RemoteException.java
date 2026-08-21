package net.managerhub.center.common.remote;

/**
 * The remote database could not be reached or answered with a failure.
 *
 * <p>The message is written for an administrator: what was attempted and what the
 * database said. It never carries the password, the whole connection string or
 * the payload of a module.</p>
 */
public class RemoteException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * @param message what went wrong, without any credential
     */
    public RemoteException(final String message) {
        super(message);
    }

    /**
     * @param message what went wrong, without any credential
     * @param cause   the original failure
     */
    public RemoteException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
