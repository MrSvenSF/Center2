package net.managerhub.center.api;

import java.time.Duration;
import java.util.Optional;

/**
 * Short lived data one module keeps in the shared remote database.
 *
 * <p>Every module has its own namespace, which is its module id. A module can
 * neither read nor overwrite the entries of another module through this
 * interface.</p>
 *
 * <p>Center2 does not look into the payload. It is a block of bytes: a
 * serialized inventory, a small JSON document, anything the module understands.
 * The core only manages namespace, key, size and expiry.</p>
 *
 * <p><strong>This storage is remote only.</strong> It exists exactly when the
 * remote database is switched on and reachable. If it is not, every method
 * throws {@link ModuleRemoteException}; nothing is written into the local SQLite
 * database of the server. That is on purpose: data that is meant to travel
 * between servers is worthless when it silently stays on one of them.</p>
 *
 * <p>Every entry needs an expiry. The storage is a hand-over point, not a
 * database: an entry nobody picks up is removed again, so a transfer that was
 * interrupted cannot leave data behind forever.</p>
 *
 * <p>All methods block on a database call. Never call them on the main server
 * thread of Paper or on the event loop of Velocity.</p>
 */
public interface ModuleStorage {

    /**
     * @return {@code true} if the remote database is switched on and reachable
     *         right now; {@code false} means every other method will throw
     */
    boolean available();

    /**
     * Writes one entry and replaces an entry with the same key.
     *
     * @param key     key inside the namespace of this module, at most 190 characters
     * @param payload the data, at most 8 MiB
     * @param ttl     how long the entry stays readable, at least one second
     * @throws ModuleRemoteException if the remote database is not available or the write failed
     * @throws IllegalArgumentException if key, payload or ttl are outside the allowed range
     */
    void put(String key, byte[] payload, Duration ttl) throws ModuleRemoteException;

    /**
     * Reads one entry without consuming it.
     *
     * @param key key inside the namespace of this module
     * @return the data, or empty if there is no such entry or it has expired
     * @throws ModuleRemoteException if the remote database is not available or the read failed
     */
    Optional<byte[]> get(String key) throws ModuleRemoteException;

    /**
     * Reads one entry and consumes it in the same step.
     *
     * <p>Exactly one caller in the whole network gets the data; every further
     * call answers empty, even when two servers ask at the same moment. This is
     * what a player transfer needs: an inventory that was handed over must never
     * be applied twice.</p>
     *
     * @param key key inside the namespace of this module
     * @return the data, or empty if there is no such entry, it has expired or
     *         somebody else took it first
     * @throws ModuleRemoteException if the remote database is not available or the operation failed
     */
    Optional<byte[]> take(String key) throws ModuleRemoteException;

    /**
     * Removes one entry, whether it was consumed or not.
     *
     * @param key key inside the namespace of this module
     * @return {@code true} if an entry was removed
     * @throws ModuleRemoteException if the remote database is not available or the delete failed
     */
    boolean delete(String key) throws ModuleRemoteException;
}
