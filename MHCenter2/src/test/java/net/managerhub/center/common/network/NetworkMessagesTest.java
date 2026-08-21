package net.managerhub.center.common.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.managerhub.center.api.ModuleActionTarget;
import net.managerhub.center.common.remote.RemoteAction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NetworkMessagesTest {

    @Test
    @DisplayName("the channel follows the MHCenter2 naming")
    void channelIsNamespaced() {
        assertEquals("mhcenter2:network", NetworkMessages.CHANNEL);
    }

    @Test
    @DisplayName("a message without payload only carries its type")
    void writesSimpleMessages() {
        assertEquals(NetworkMessages.HELLO,
                NetworkMessages.typeOf(NetworkMessages.simple(NetworkMessages.HELLO)));
        assertEquals(NetworkMessages.REQUEST,
                NetworkMessages.typeOf(NetworkMessages.simple(NetworkMessages.REQUEST)));
    }

    @Test
    @DisplayName("a status answer survives the round trip in order")
    void writesAndReadsStatus() {
        final Map<String, ServerStatus> servers = new LinkedHashMap<>();
        servers.put("lobby", ServerStatus.CONNECTED);
        servers.put("survival", ServerStatus.UNREACHABLE);
        servers.put("citybuild", ServerStatus.UNKNOWN);

        final byte[] message = NetworkMessages.status(servers);

        assertEquals(NetworkMessages.STATUS, NetworkMessages.typeOf(message));
        assertEquals(servers, NetworkMessages.readStatus(message));
        assertEquals(java.util.List.of("lobby", "survival", "citybuild"),
                java.util.List.copyOf(NetworkMessages.readStatus(message).keySet()));
    }

    @Test
    @DisplayName("an empty answer is valid")
    void writesEmptyStatus() {
        assertTrue(NetworkMessages.readStatus(NetworkMessages.status(Map.of())).isEmpty());
    }

    @Test
    @DisplayName("a broken message is never guessed")
    void ignoresBrokenMessages() {
        assertNull(NetworkMessages.typeOf(new byte[] {1, 2, 3}));
        assertTrue(NetworkMessages.readStatus(new byte[] {1, 2, 3}).isEmpty());
        assertTrue(NetworkMessages.readStatus(NetworkMessages.simple(NetworkMessages.HELLO)).isEmpty());
    }

    @Test
    @DisplayName("an unexpected state falls back to unknown instead of to a missing plugin")
    void unexpectedStateIsUnknown() {
        assertEquals(ServerStatus.UNKNOWN, ServerStatus.of("SOMETHING_ELSE"));
        assertEquals(ServerStatus.CONNECTED, ServerStatus.of("CONNECTED"));
    }

    @Test
    @DisplayName("a module action survives the plugin-message round trip")
    void writesAndReadsModuleAction() {
        final RemoteAction action = new RemoteAction(UUID.randomUUID(), "homes", "SYNC", "lobby",
                ModuleActionTarget.server("survival"), 10L, 20L, new byte[] {4, 5, 6});

        final byte[] encoded = NetworkMessages.moduleAction(NetworkMessages.MODULE_ACTION_EXECUTE, action);
        final RemoteAction decoded = NetworkMessages.readModuleAction(
                NetworkMessages.MODULE_ACTION_EXECUTE, encoded).orElseThrow();

        assertEquals(action.id(), decoded.id());
        assertEquals(action.namespace(), decoded.namespace());
        assertEquals(action.type(), decoded.type());
        assertEquals(action.originServerId(), decoded.originServerId());
        assertEquals(action.target(), decoded.target());
        assertTrue(java.util.Arrays.equals(action.payload(), decoded.payload()));
    }

    @Test
    @DisplayName("a module action is never accepted under the wrong message type")
    void rejectsWrongModuleActionType() {
        final RemoteAction action = new RemoteAction(UUID.randomUUID(), "homes", "SYNC", "lobby",
                ModuleActionTarget.ALL, 10L, 20L, new byte[0]);
        final byte[] encoded = NetworkMessages.moduleAction(NetworkMessages.MODULE_ACTION_REQUEST, action);

        assertTrue(NetworkMessages.readModuleAction(NetworkMessages.MODULE_ACTION_EXECUTE, encoded).isEmpty());
    }

    @Test
    @DisplayName("the fallback leaves room for its protocol header")
    void rejectsOversizedFallbackPayload() {
        final RemoteAction action = new RemoteAction(UUID.randomUUID(), "homes", "SYNC", "lobby",
                ModuleActionTarget.ALL, 10L, 20L,
                new byte[NetworkMessages.MAX_MODULE_ACTION_PAYLOAD + 1]);

        assertThrows(IllegalArgumentException.class,
                () -> NetworkMessages.moduleAction(NetworkMessages.MODULE_ACTION_REQUEST, action));
    }
}
