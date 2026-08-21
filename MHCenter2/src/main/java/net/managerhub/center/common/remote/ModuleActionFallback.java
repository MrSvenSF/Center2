package net.managerhub.center.common.remote;

import java.util.List;

/** Optional player-carried transport for module actions when MariaDB is unavailable. */
public interface ModuleActionFallback {

    ModuleActionFallback UNAVAILABLE = new ModuleActionFallback() {
        @Override public boolean available() { return false; }
        @Override public String serverId() { return ""; }
        @Override public List<String> onlineNodes() { return List.of(); }
        @Override public void send(final RemoteAction action) throws RemoteException {
            throw new RemoteException("No player is available to carry a plugin message.");
        }
    };

    boolean available();

    String serverId();

    List<String> onlineNodes();

    void send(RemoteAction action) throws RemoteException;
}
