package net.managerhub.center.api;

/**
 * A remote operation of a module could not be carried out.
 *
 * <p>This is thrown instead of quietly doing something else. The remote database
 * of MHCenter2 is optional: it can be switched off, it can be misconfigured and it
 * can be down for a while. A module has to see that difference, because the data
 * it keeps there is deliberately <em>not</em> stored anywhere else.</p>
 *
 * <p>There is no local fallback. MHCenter2 never writes remote-only module data
 * into the local SQLite database, not even to be helpful: a player transfer that
 * silently landed in the database of one single server would look successful and
 * would still lose the data.</p>
 */
public class ModuleRemoteException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * @param message what could not be done, without credentials and without the
     *                payload of the module
     */
    public ModuleRemoteException(final String message) {
        super(message);
    }

    /**
     * @param message what could not be done, without credentials and without the
     *                payload of the module
     * @param cause   the original failure
     */
    public ModuleRemoteException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
