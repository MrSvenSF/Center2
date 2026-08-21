package net.managerhub.center.common.remote;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything Center2 stores in the optional remote database.
 *
 * <p>The interface exists so the rest of Center2 never sees SQL and so the whole
 * behaviour - who runs an action, what a receipt prevents, when data expires -
 * can be tested without a database server.</p>
 *
 * <p>There are exactly four things in there: the nodes, the actions, one receipt
 * per node and action, and the short lived data of the modules. Nothing else is
 * synchronized: the local SQLite database of every server stays what it is and is
 * never mirrored here.</p>
 *
 * <p>Every method may block on the network. Nothing here is called on the main
 * server thread of Paper or on the event loop of Velocity.</p>
 */
public interface RemoteStore extends AutoCloseable {

    /**
     * Opens the connection and creates the tables if they are missing.
     *
     * @throws RemoteException if the database cannot be reached or prepared
     */
    void initialize() throws RemoteException;

    /**
     * Reports that this node is alive.
     *
     * @param node this node
     * @throws RemoteException if the heartbeat could not be written
     */
    void heartbeat(RemoteNode node) throws RemoteException;

    /**
     * @param offlineAfterSeconds age above which a node is left out
     * @return every node that reported recently, in no particular order
     * @throws RemoteException if the nodes could not be read
     */
    List<RemoteNode> onlineNodes(int offlineAfterSeconds) throws RemoteException;

    /**
     * Reads the row of one node.
     *
     * @param serverId id of the wanted node
     * @return the node, or empty if no node of that id ever reported
     * @throws RemoteException if the node could not be read
     */
    Optional<RemoteNode> node(String serverId) throws RemoteException;

    /**
     * Removes this node from the list again. Belongs to a clean shutdown.
     *
     * @param serverId  id of this node
     * @param runtimeId id of this run, so a node that was already replaced by a
     *                  restart does not delete the row of the new run
     * @throws RemoteException if the row could not be removed
     */
    void removeNode(String serverId, String runtimeId) throws RemoteException;

    /**
     * Writes one action into the network.
     *
     * @param action the action
     * @throws RemoteException if the action could not be written
     */
    void publish(RemoteAction action) throws RemoteException;

    /**
     * Reads the actions this node has not answered yet.
     *
     * <p>Expired actions are left out, and so is everything this node already
     * has a receipt for. The list is the reason a node with no player still
     * learns about a network reload.</p>
     *
     * @param serverId  id of this node
     * @param nowMillis the current time
     * @param limit     largest number of actions in one answer
     * @return the open actions, oldest first
     * @throws RemoteException if the actions could not be read
     */
    List<RemoteAction> openActions(String serverId, long nowMillis, int limit) throws RemoteException;

    /**
     * Claims one action for this node.
     *
     * <p>This is the step that makes "exactly once per node" true. Only the
     * first caller for one pair of action and node gets {@code true}; a second
     * attempt - after a restart, after a second poll, from a second thread - gets
     * {@code false} and must not run the action.</p>
     *
     * @param actionId id of the action
     * @param serverId id of this node
     * @return {@code true} if this node may run the action now
     * @throws RemoteException if the claim could not be written
     */
    boolean claim(UUID actionId, String serverId) throws RemoteException;

    /**
     * Writes down how the action ended on this node.
     *
     * @param actionId id of the action
     * @param serverId id of this node
     * @param status   what happened
     * @param error    short reason for {@link RemoteActionStatus#FAILED}, may be empty
     * @throws RemoteException if the receipt could not be written
     */
    void finish(UUID actionId, String serverId, RemoteActionStatus status, String error) throws RemoteException;

    /**
     * @param actionId id of the action
     * @return the receipt of every node for that action
     * @throws RemoteException if the receipts could not be read
     */
    List<RemoteReceipt> receipts(UUID actionId) throws RemoteException;

    /**
     * Writes one entry of the module storage and replaces an entry with the same key.
     *
     * @param namespace       id of the module
     * @param key             key inside that namespace
     * @param payload         the data
     * @param expiresAtMillis when the entry stops being readable
     * @throws RemoteException if the entry could not be written
     */
    void putData(String namespace, String key, byte[] payload, long expiresAtMillis) throws RemoteException;

    /**
     * @param namespace id of the module
     * @param key       key inside that namespace
     * @param nowMillis the current time
     * @return the data, empty if there is none, it expired or it was consumed
     * @throws RemoteException if the entry could not be read
     */
    Optional<byte[]> readData(String namespace, String key, long nowMillis) throws RemoteException;

    /**
     * Reads one entry and consumes it in the same step.
     *
     * <p>Exactly one caller in the whole network gets the data. This is what
     * makes a player transfer safe: two target servers can ask at the same
     * moment and only one of them gets the inventory.</p>
     *
     * @param namespace id of the module
     * @param key       key inside that namespace
     * @param claimedBy who is taking it, only used to find the row again
     * @param nowMillis the current time
     * @return the data, empty if there is none, it expired or somebody was faster
     * @throws RemoteException if the entry could not be taken
     */
    Optional<byte[]> takeData(String namespace, String key, String claimedBy, long nowMillis)
            throws RemoteException;

    /**
     * @param namespace id of the module
     * @param key       key inside that namespace
     * @return {@code true} if an entry was removed
     * @throws RemoteException if the entry could not be removed
     */
    boolean deleteData(String namespace, String key) throws RemoteException;

    /**
     * Removes everything that has expired.
     *
     * <p>Runs regularly, so an interrupted transfer cannot leave module data in
     * the database forever. The remote storage is a hand-over point and must
     * never grow into a permanent item database.</p>
     *
     * @param nowMillis the current time
     * @return how many rows were removed
     * @throws RemoteException if the cleanup failed
     */
    int purgeExpired(long nowMillis) throws RemoteException;

    /** Closes the connection. Never throws. */
    @Override
    void close();
}
