package net.managerhub.center.common.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.managerhub.center.api.ModuleActionTarget;
import net.managerhub.center.common.remote.RemoteAction;

/**
 * The small plugin message protocol between the Paper and the Velocity part of
 * MHCenter2.
 *
 * <p>Two things are exchanged. The network status: Paper announces itself once a
 * player is there to carry the message and may ask for the current picture, and
 * Velocity answers with the state of every server it knows. And the network wide
 * reload: Paper asks the proxy to reload the whole network, the proxy passes that
 * on to the other servers and answers what really happened.</p>
 *
 * <p>A plugin message always needs a player to travel through. That is the known
 * limit of this way and the reason MHCenter2 never claims that a server was
 * reloaded when it could not be reached; the optional remote database is the way
 * that also works without a single player online.</p>
 */
public final class NetworkMessages {

    /** Leaves room for the protocol header below Paper's one MiB message limit. */
    public static final int MAX_MODULE_ACTION_PAYLOAD = 900 * 1024;

    /** Namespace of the plugin message channel. */
    public static final String CHANNEL_NAMESPACE = "mhcenter2";

    /** Name of the plugin message channel. */
    public static final String CHANNEL_NAME = "network";

    /** The complete channel, as Paper and Velocity register it. */
    public static final String CHANNEL = CHANNEL_NAMESPACE + ":" + CHANNEL_NAME;

    /** Paper tells the proxy that MHCenter2 is running on this server. */
    public static final String HELLO = "HELLO";

    /** Paper asks the proxy for the current network status. */
    public static final String REQUEST = "REQUEST";

    /** The proxy answers with the status of every known server. */
    public static final String STATUS = "STATUS";

    /** Paper asks the proxy to reload the whole MHCenter2 network. */
    public static final String RELOAD_REQUEST = "RELOAD_REQUEST";

    /** The proxy tells one Paper server to reload its own MHCenter2. */
    public static final String RELOAD_EXECUTE = "RELOAD_EXECUTE";

    /** One Paper server tells the proxy how its own reload ended. */
    public static final String RELOAD_RESULT = "RELOAD_RESULT";

    /** The proxy tells the origin what really happened with its reload request. */
    public static final String RELOAD_REPORT = "RELOAD_REPORT";

    /** Paper sends a module action to Velocity for routing. */
    public static final String MODULE_ACTION_REQUEST = "MODULE_ACTION_REQUEST";

    /** Velocity sends a routed module action to Paper. */
    public static final String MODULE_ACTION_EXECUTE = "MODULE_ACTION_EXECUTE";

    private NetworkMessages() {
        throw new AssertionError("No instances.");
    }

    /** @return a message without payload, for {@link #HELLO} and {@link #REQUEST}. */
    public static byte[] simple(final String type) {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF(type);
        } catch (final IOException impossible) {
            throw new UncheckedIOException(impossible);
        }
        return bytes.toByteArray();
    }

    /**
     * @param servers server name to state, in the order the menu should show them
     * @return the encoded {@link #STATUS} answer
     */
    public static byte[] status(final Map<String, ServerStatus> servers) {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF(STATUS);
            out.writeInt(servers.size());
            for (final Map.Entry<String, ServerStatus> server : servers.entrySet()) {
                out.writeUTF(server.getKey());
                out.writeUTF(server.getValue().name());
            }
        } catch (final IOException impossible) {
            throw new UncheckedIOException(impossible);
        }
        return bytes.toByteArray();
    }

    /**
     * @param message received message
     * @return the message type, or {@code null} if the message cannot be read
     */
    public static String typeOf(final byte[] message) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            return in.readUTF();
        } catch (final IOException malformed) {
            return null;
        }
    }

    /**
     * Reads a {@link #STATUS} answer.
     *
     * @param message received message
     * @return server name to state, empty if the message cannot be read
     */
    public static Map<String, ServerStatus> readStatus(final byte[] message) {
        final Map<String, ServerStatus> servers = new LinkedHashMap<>();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            if (!STATUS.equals(in.readUTF())) {
                return Map.of();
            }
            final int count = in.readInt();
            for (int index = 0; index < count; index++) {
                final String name = in.readUTF();
                servers.put(name, ServerStatus.of(in.readUTF()));
            }
        } catch (final IOException malformed) {
            return Map.of();
        }
        return servers;
    }

    /**
     * Encodes a reload request or a reload order.
     *
     * @param type    {@link #RELOAD_REQUEST} or {@link #RELOAD_EXECUTE}
     * @param message the request
     * @return the encoded message
     */
    public static byte[] reload(final String type, final ReloadMessage message) {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF(type);
            out.writeUTF(message.requestId().toString());
            out.writeUTF(message.origin());
            out.writeLong(message.expiresAtMillis());
        } catch (final IOException impossible) {
            throw new UncheckedIOException(impossible);
        }
        return bytes.toByteArray();
    }

    /**
     * Reads a reload request or a reload order.
     *
     * @param expectedType {@link #RELOAD_REQUEST} or {@link #RELOAD_EXECUTE}
     * @param message      received message
     * @return the request, or empty if the message is not the expected one or is broken
     */
    public static Optional<ReloadMessage> readReload(final String expectedType, final byte[] message) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            if (!expectedType.equals(in.readUTF())) {
                return Optional.empty();
            }
            final UUID requestId = UUID.fromString(in.readUTF());
            final String origin = in.readUTF();
            return Optional.of(new ReloadMessage(requestId, origin, in.readLong()));
        } catch (final IOException | IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    /** Encodes a module action for either fallback direction. */
    public static byte[] moduleAction(final String messageType, final RemoteAction action) {
        if (action.payload().length > MAX_MODULE_ACTION_PAYLOAD) {
            throw new IllegalArgumentException("A Plugin Messaging module action is at most "
                    + MAX_MODULE_ACTION_PAYLOAD + " bytes, this one has " + action.payload().length + ".");
        }
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF(messageType);
            out.writeUTF(action.id().toString());
            out.writeUTF(action.namespace());
            out.writeUTF(action.type());
            out.writeUTF(action.originServerId());
            out.writeUTF(action.target().kind().name());
            out.writeUTF(action.target().serverId());
            out.writeLong(action.createdAtMillis());
            out.writeLong(action.expiresAtMillis());
            final byte[] payload = action.payload();
            out.writeInt(payload.length);
            out.write(payload);
        } catch (final IOException impossible) {
            throw new UncheckedIOException(impossible);
        }
        return bytes.toByteArray();
    }

    /** Reads a module action and rejects malformed or oversized messages. */
    public static Optional<RemoteAction> readModuleAction(final String expectedType, final byte[] message) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            if (!expectedType.equals(in.readUTF())) {
                return Optional.empty();
            }
            final UUID id = UUID.fromString(in.readUTF());
            final String namespace = in.readUTF();
            final String type = in.readUTF();
            final String origin = in.readUTF();
            final ModuleActionTarget.Kind kind = ModuleActionTarget.Kind.valueOf(in.readUTF());
            final String server = in.readUTF();
            final ModuleActionTarget target = kind == ModuleActionTarget.Kind.SERVER
                    ? ModuleActionTarget.server(server)
                    : switch (kind) {
                        case ALL -> ModuleActionTarget.ALL;
                        case PAPER -> ModuleActionTarget.PAPER;
                        case VELOCITY -> ModuleActionTarget.VELOCITY;
                        case SERVER -> throw new AssertionError();
                    };
            final long created = in.readLong();
            final long expires = in.readLong();
            final int length = in.readInt();
            if (length < 0 || length > MAX_MODULE_ACTION_PAYLOAD
                    || length > in.available()) {
                return Optional.empty();
            }
            final byte[] payload = in.readNBytes(length);
            if (in.available() != 0) {
                return Optional.empty();
            }
            return Optional.of(new RemoteAction(id, namespace, type, origin, target, created, expires, payload));
        } catch (final IOException | IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    /**
     * Encodes how the reload of one Paper server ended.
     *
     * <p>The server does not name itself here. The proxy knows which connection
     * the message came through, and that is the only trustworthy source for the
     * name of a backend server.</p>
     *
     * @param requestId id of the request
     * @param status    what happened on that server
     * @return the encoded message
     */
    public static byte[] reloadResult(final UUID requestId, final NetworkReloadStatus status) {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF(RELOAD_RESULT);
            out.writeUTF(requestId.toString());
            out.writeUTF(status.name());
        } catch (final IOException impossible) {
            throw new UncheckedIOException(impossible);
        }
        return bytes.toByteArray();
    }

    /**
     * Reads how the reload of one Paper server ended.
     *
     * @param message received message
     * @return the result, empty if the message cannot be read
     */
    public static Optional<ReloadResult> readReloadResult(final byte[] message) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            if (!RELOAD_RESULT.equals(in.readUTF())) {
                return Optional.empty();
            }
            final UUID requestId = UUID.fromString(in.readUTF());
            return Optional.of(new ReloadResult(requestId, status(in.readUTF())));
        } catch (final IOException | IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    /**
     * How one Paper server answered a reload order.
     *
     * @param requestId id of the request
     * @param status    what happened there
     */
    public record ReloadResult(UUID requestId, NetworkReloadStatus status) {
    }

    /**
     * Encodes what really happened with one reload request.
     *
     * @param requestId id of the request
     * @param results   server name to state, in the order the answer should be read
     * @return the encoded message
     */
    public static byte[] reloadReport(final UUID requestId, final Map<String, NetworkReloadStatus> results) {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF(RELOAD_REPORT);
            out.writeUTF(requestId.toString());
            out.writeInt(results.size());
            for (final Map.Entry<String, NetworkReloadStatus> result : results.entrySet()) {
                out.writeUTF(result.getKey());
                out.writeUTF(result.getValue().name());
            }
        } catch (final IOException impossible) {
            throw new UncheckedIOException(impossible);
        }
        return bytes.toByteArray();
    }

    /**
     * Reads what really happened with one reload request.
     *
     * @param message received message
     * @return the request id and the state per server, empty if the message cannot be read
     */
    public static Optional<ReloadReport> readReloadReport(final byte[] message) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            if (!RELOAD_REPORT.equals(in.readUTF())) {
                return Optional.empty();
            }
            final UUID requestId = UUID.fromString(in.readUTF());
            final int count = in.readInt();
            final Map<String, NetworkReloadStatus> results = new LinkedHashMap<>();
            for (int index = 0; index < count; index++) {
                final String server = in.readUTF();
                results.put(server, status(in.readUTF()));
            }
            return Optional.of(new ReloadReport(requestId, results));
        } catch (final IOException | IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    /**
     * What every server answered to one reload request.
     *
     * @param requestId id of the request
     * @param results   server name to state
     */
    public record ReloadReport(UUID requestId, Map<String, NetworkReloadStatus> results) {

        public ReloadReport {
            results = Map.copyOf(results);
        }
    }

    private static NetworkReloadStatus status(final String raw) {
        for (final NetworkReloadStatus status : NetworkReloadStatus.values()) {
            if (status.name().equals(raw)) {
                return status;
            }
        }
        return NetworkReloadStatus.FAILED;
    }
}
