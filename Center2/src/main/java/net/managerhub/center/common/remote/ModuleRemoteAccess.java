package net.managerhub.center.common.remote;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import net.managerhub.center.Center;
import net.managerhub.center.api.ModuleActionListener;
import net.managerhub.center.api.ModuleActionTarget;
import net.managerhub.center.api.ModuleNetwork;
import net.managerhub.center.api.ModuleRemoteException;
import net.managerhub.center.api.ModuleStorage;

/**
 * What one module sees of the remote system.
 *
 * <p>Every module gets its own instance, bound to its module id. That id is the
 * namespace of its storage and of its actions, so one module can neither read nor
 * overwrite the data of another one through this API.</p>
 *
 * <p>Actions prefer MariaDB and may fall back to Plugin Messaging. Storage never
 * falls back to local SQLite: shared data must not silently stay on one node.</p>
 */
public final class ModuleRemoteAccess implements ModuleNetwork, ModuleStorage {

    private final String moduleId;
    private final RemoteService remote;
    private final ModuleActionFallback fallback;
    private final ModuleStorage storage = new ModuleStorage() {
        @Override public boolean available() { return remote.available(); }
        @Override public void put(final String key, final byte[] payload, final Duration ttl)
                throws ModuleRemoteException { putStorage(key, payload, ttl); }
        @Override public Optional<byte[]> get(final String key) throws ModuleRemoteException {
            return getStorage(key);
        }
        @Override public Optional<byte[]> take(final String key) throws ModuleRemoteException {
            return takeStorage(key);
        }
        @Override public boolean delete(final String key) throws ModuleRemoteException {
            return deleteStorage(key);
        }
    };

    /**
     * @param moduleId id of the module, which is also its namespace
     * @param remote   the remote system of this node
     */
    public ModuleRemoteAccess(final String moduleId, final RemoteService remote) {
        this(moduleId, remote, ModuleActionFallback.UNAVAILABLE);
    }

    public ModuleRemoteAccess(final String moduleId,
                              final RemoteService remote,
                              final ModuleActionFallback fallback) {
        this.moduleId = moduleId;
        this.remote = remote;
        this.fallback = fallback == null ? ModuleActionFallback.UNAVAILABLE : fallback;
    }

    @Override
    public boolean available() {
        return remote.available() || fallback.available();
    }

    @Override
    public String serverId() {
        return remote.serverId().isEmpty() ? fallback.serverId() : remote.serverId();
    }

    @Override
    public List<String> onlineNodes() {
        return remote.available() ? remote.onlineNodes() : fallback.onlineNodes();
    }

    @Override
    public ModuleStorage storage() {
        return storage;
    }

    @Override
    public void onAction(final ModuleActionListener listener) {
        remote.moduleListener(moduleId, listener);
    }

    @Override
    public void send(final String type,
                     final ModuleActionTarget target,
                     final byte[] payload,
                     final Duration lifetime) throws ModuleRemoteException {
        if (target == null) {
            throw new IllegalArgumentException("An action needs a target.");
        }
        try {
            if (remote.available()) {
                remote.publishModuleAction(moduleId, type, target, payload, lifetime);
            } else {
                fallback.send(remote.createModuleAction(moduleId, type, target, payload, lifetime,
                        fallback.serverId()));
            }
        } catch (final RemoteException failure) {
            throw new ModuleRemoteException(failure.getMessage(), failure);
        }
    }

    @Override
    public void put(final String key, final byte[] payload, final Duration ttl) throws ModuleRemoteException {
        putStorage(key, payload, ttl);
    }

    private void putStorage(final String key, final byte[] payload, final Duration ttl) throws ModuleRemoteException {
        final byte[] data = payload == null ? new byte[0] : payload;
        checkKey(key);
        if (data.length > RemoteService.MAX_STORAGE_PAYLOAD) {
            throw new IllegalArgumentException("A storage entry is at most "
                    + RemoteService.MAX_STORAGE_PAYLOAD + " bytes, this one has " + data.length + ".");
        }
        if (ttl == null || ttl.toSeconds() < 1) {
            throw new IllegalArgumentException("A storage entry needs a lifetime of at least one second.");
        }
        store(store -> {
            store.putData(moduleId, key, data, System.currentTimeMillis() + ttl.toMillis());
            return null;
        });
    }

    @Override
    public Optional<byte[]> get(final String key) throws ModuleRemoteException {
        return getStorage(key);
    }

    private Optional<byte[]> getStorage(final String key) throws ModuleRemoteException {
        checkKey(key);
        return store(store -> store.readData(moduleId, key, System.currentTimeMillis()));
    }

    @Override
    public Optional<byte[]> take(final String key) throws ModuleRemoteException {
        return takeStorage(key);
    }

    private Optional<byte[]> takeStorage(final String key) throws ModuleRemoteException {
        checkKey(key);
        return store(store -> store.takeData(moduleId, key, claimant(), System.currentTimeMillis()));
    }

    @Override
    public boolean delete(final String key) throws ModuleRemoteException {
        return deleteStorage(key);
    }

    private boolean deleteStorage(final String key) throws ModuleRemoteException {
        checkKey(key);
        return store(store -> store.deleteData(moduleId, key));
    }

    /** One operation on the remote store. */
    @FunctionalInterface
    private interface Operation<T> {
        T run(RemoteStore store) throws RemoteException;
    }

    /**
     * Runs one operation, or explains why it cannot run.
     *
     * <p>This is the single place that turns an unavailable remote system into an
     * honest failure. There is deliberately no other branch here: no local
     * database, no cache, no "best effort".</p>
     */
    private <T> T store(final Operation<T> operation) throws ModuleRemoteException {
        final RemoteStore open;
        try {
            open = remote.required();
        } catch (final RemoteException unavailable) {
            throw new ModuleRemoteException("The module '" + moduleId + "' cannot use the remote storage of "
                    + Center.PRODUCT_NAME + ": " + unavailable.getMessage()
                    + " Remote data is never written into the local database.", unavailable);
        }
        try {
            return operation.run(open);
        } catch (final RemoteException failure) {
            throw new ModuleRemoteException("The remote operation of module '" + moduleId + "' failed: "
                    + failure.getMessage(), failure);
        }
    }

    /** @return who is taking an entry; the node id, or the module id if there is none. */
    private String claimant() {
        final String node = remote.serverId();
        return node.isEmpty() ? moduleId : node;
    }

    private static void checkKey(final String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("A storage key must not be empty.");
        }
        if (key.length() > RemoteService.MAX_STORAGE_KEY) {
            throw new IllegalArgumentException("A storage key is at most "
                    + RemoteService.MAX_STORAGE_KEY + " characters, this one has " + key.length() + ".");
        }
    }
}
