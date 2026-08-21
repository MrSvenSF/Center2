package net.managerhub.center.api;

import java.time.Duration;
import java.util.List;

/**
 * What one module may do in the MHCenter2 network.
 *
 * <p>MHCenter2 prefers the optional remote database and falls back to player-carried
 * Plugin Messaging for actions. The fallback only exists while Velocity has
 * answered through an online player. Shared storage always remains MariaDB-only;
 * it is never silently replaced by local SQLite.</p>
 *
 * <p>There is deliberately no way to run a command on another server. An action
 * is a name and a block of bytes that reaches the same module on the other node;
 * what happens there is decided by that module, in its own code. MHCenter2 never
 * turns the content of the remote database into a console command.</p>
 */
public interface ModuleNetwork {

    /**
     * @return {@code true} if MariaDB or the verified Plugin Messaging fallback
     *         can currently carry an action
     */
    boolean available();

    /**
     * The id of this node in the network.
     *
     * <p>This is the {@code remote.server-id} an administrator configured, for
     * example {@code lobby} or {@code velocity}. It is empty when the remote
     * system is switched off.</p>
     *
     * @return the id of this MHCenter2 node, or an empty text
     */
    String serverId();

    /**
     * @return the ids of every MHCenter2 node that sent a heartbeat recently, this
     *         node included; empty if the remote system is not available
     */
    List<String> onlineNodes();

    /** @return the remote-only storage of this module. */
    ModuleStorage storage();

    /**
     * Registers what this module does with the actions that are addressed to it.
     *
     * <p>Only one listener per module: a second call replaces the first one.
     * MHCenter2 removes the listener again when the module is stopped, so a module
     * does not have to register a cleanup for it.</p>
     *
     * @param listener what to do with an action, {@code null} removes the listener
     */
    void onAction(ModuleActionListener listener);

    /**
     * Sends one action into the network.
     *
     * <p>MariaDB delivers reliably even without players. If it is unavailable,
     * Plugin Messaging is used and therefore only currently reachable nodes can
     * receive the action.</p>
     *
     * <p>The sending node itself is never addressed by its own action.</p>
     *
     * @param type     name of the action, chosen by this module, at most 64
     *                 characters of {@code a-z}, {@code A-Z}, {@code 0-9},
     *                 {@code _}, {@code -} and {@code .}
     * @param target   which nodes should run it
     * @param payload  the data of the module, at most 1 MiB through MariaDB and
     *                 900 KiB through Plugin Messaging, may be empty
     * @param lifetime how long the action stays valid, at least one second
     * @throws ModuleRemoteException if neither transport is available or sending failed
     * @throws IllegalArgumentException if type, payload or lifetime are outside the allowed range
     */
    void send(String type, ModuleActionTarget target, byte[] payload, Duration lifetime)
            throws ModuleRemoteException;
}
