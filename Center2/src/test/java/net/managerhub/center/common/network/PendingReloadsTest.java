package net.managerhub.center.common.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The waiting reload requests of the proxy.
 *
 * <p>This is the honest part of the plugin message way: a server nobody is
 * connected to cannot be reached, so the request waits instead of being
 * reported as done - and it is dropped when it ran out of time instead of
 * arriving hours late.</p>
 */
class PendingReloadsTest {

    @Test
    @DisplayName("a request that could not be delivered waits for that server")
    void requestWaits() {
        final PendingReloads pending = new PendingReloads();
        final UUID request = UUID.randomUUID();
        final long now = System.currentTimeMillis();

        pending.remember("survival", request, "lobby", now + 60_000L);

        assertEquals(List.of("survival"), pending.servers(now));
    }

    @Test
    @DisplayName("the waiting request is delivered once, then it is gone")
    void deliveredOnce() {
        final PendingReloads pending = new PendingReloads();
        final UUID request = UUID.randomUUID();
        final long now = System.currentTimeMillis();
        pending.remember("survival", request, "lobby", now + 60_000L);

        final Optional<PendingReloads.Delivery> first = pending.take("survival", now);
        final Optional<PendingReloads.Delivery> second = pending.take("survival", now);

        assertTrue(first.isPresent());
        assertEquals(request, first.get().requestId());
        assertEquals("lobby", first.get().origin());
        assertTrue(second.isEmpty(), "a request is delivered once, not on every join");
    }

    @Test
    @DisplayName("a request that ran out of time is not delivered late")
    void expiredIsNotDelivered() {
        final PendingReloads pending = new PendingReloads();
        final long now = System.currentTimeMillis();
        pending.remember("survival", UUID.randomUUID(), "lobby", now + 1000L);

        assertTrue(pending.take("survival", now + 2000L).isEmpty());
        assertEquals(List.of(), pending.servers(now + 2000L));
    }

    @Test
    @DisplayName("expired requests are really dropped")
    void expiredAreDropped() {
        final PendingReloads pending = new PendingReloads();
        final long now = System.currentTimeMillis();
        pending.remember("survival", UUID.randomUUID(), "lobby", now + 1000L);
        pending.remember("citybuild", UUID.randomUUID(), "lobby", now + 60_000L);

        assertEquals(List.of("survival"), pending.dropExpired(now + 2000L));
        assertEquals(1, pending.size());
        assertEquals(List.of("citybuild"), pending.servers(now + 2000L));
    }

    @Test
    @DisplayName("a newer request replaces the older one for the same server")
    void newerRequestWins() {
        final PendingReloads pending = new PendingReloads();
        final UUID older = UUID.randomUUID();
        final UUID newer = UUID.randomUUID();
        final long now = System.currentTimeMillis();

        pending.remember("survival", older, "lobby", now + 60_000L);
        pending.remember("survival", newer, "lobby", now + 60_000L);

        assertEquals(1, pending.size());
        assertEquals(newer, pending.take("survival", now).orElseThrow().requestId());
    }

    @Test
    @DisplayName("a server nobody ever asked about has nothing waiting")
    void nothingWaitsByDefault() {
        final PendingReloads pending = new PendingReloads();

        assertTrue(pending.take("survival", System.currentTimeMillis()).isEmpty());
        assertEquals(0, pending.size());
    }

    @Test
    @DisplayName("the list of waiting servers cannot grow without a limit")
    void memoryIsBounded() {
        final PendingReloads pending = new PendingReloads();
        final long now = System.currentTimeMillis();

        for (int index = 0; index < PendingReloads.MAX_SERVERS * 2; index++) {
            pending.remember("server-" + index, UUID.randomUUID(), "lobby", now + 60_000L);
        }

        assertEquals(PendingReloads.MAX_SERVERS, pending.size());
    }
}
