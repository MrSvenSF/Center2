package net.managerhub.center.common.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The two rules that keep a network wide reload from turning into a circle.
 *
 * <p>One: a reload that arrived from the network is never handed on. Two: every
 * node carries out one request id exactly once. Without both, Paper A would tell
 * the proxy, the proxy would tell Paper B, Paper B would tell the proxy, and it
 * would never end.</p>
 */
class NetworkReloadTest {

    private static final long LIFETIME = 60_000L;

    @Test
    @DisplayName("a reload an administrator asked for is spread, one that arrived is not")
    void onlyALocalRequestSpreads() {
        assertTrue(ReloadOrigin.LOCAL_USER_REQUEST.spreads());
        assertFalse(ReloadOrigin.REMOTE_REQUEST.spreads(),
                "handing a received reload on again is exactly how the circle would start");
    }

    @Test
    @DisplayName("every node carries out one request exactly once")
    void oneRequestIsClaimedOnce() {
        final ProcessedRequests node = new ProcessedRequests(LIFETIME);
        final UUID request = UUID.randomUUID();
        final long now = System.currentTimeMillis();

        assertTrue(node.claim(request, now), "the first time it is really carried out");
        assertFalse(node.claim(request, now), "the second time it is not");
        assertFalse(node.claim(request, now + 1000L));
    }

    @Test
    @DisplayName("Paper A, the proxy and Paper B each reload once and nobody twice")
    void everyNodeReloadsOnce() {
        final ProcessedRequests paperA = new ProcessedRequests(LIFETIME);
        final ProcessedRequests proxy = new ProcessedRequests(LIFETIME);
        final ProcessedRequests paperB = new ProcessedRequests(LIFETIME);
        final UUID request = UUID.randomUUID();
        final long now = System.currentTimeMillis();

        // Paper A: the administrator asked here, so it claims its own request.
        assertTrue(paperA.claim(request, now));
        // The proxy hears about it and reloads.
        assertTrue(proxy.claim(request, now));
        // Paper B hears about it through the proxy and reloads.
        assertTrue(paperB.claim(request, now));

        // Now the echo: the same request arriving a second time anywhere.
        assertFalse(paperA.claim(request, now), "the origin does not reload a second time");
        assertFalse(proxy.claim(request, now), "the proxy does not reload a second time");
        assertFalse(paperB.claim(request, now), "no node reloads a second time");
    }

    @Test
    @DisplayName("a new request is a new reload")
    void aSecondRequestIsCarriedOut() {
        final ProcessedRequests node = new ProcessedRequests(LIFETIME);
        final long now = System.currentTimeMillis();

        assertTrue(node.claim(UUID.randomUUID(), now));
        assertTrue(node.claim(UUID.randomUUID(), now));
    }

    @Test
    @DisplayName("old request ids are forgotten instead of piling up")
    void oldIdsAreForgotten() {
        final ProcessedRequests node = new ProcessedRequests(1000L);
        final UUID request = UUID.randomUUID();
        final long now = System.currentTimeMillis();
        node.claim(request, now);

        assertEquals(1, node.size());
        assertFalse(node.known(request, now + 2000L), "an id older than the lifetime is dropped");
        assertEquals(0, node.size());
    }

    @Test
    @DisplayName("a flood of requests cannot fill the memory")
    void memoryIsBounded() {
        final ProcessedRequests node = new ProcessedRequests(LIFETIME);
        final long now = System.currentTimeMillis();

        for (int index = 0; index < ProcessedRequests.MAX_ENTRIES * 3; index++) {
            node.claim(UUID.randomUUID(), now);
        }

        assertEquals(ProcessedRequests.MAX_ENTRIES, node.size());
    }

    @Test
    @DisplayName("a reload request survives the way through a plugin message unchanged")
    void requestSurvivesEncoding() {
        final ReloadMessage request =
                new ReloadMessage(UUID.randomUUID(), "lobby", System.currentTimeMillis() + LIFETIME);

        final ReloadMessage read = NetworkMessages
                .readReload(NetworkMessages.RELOAD_REQUEST,
                        NetworkMessages.reload(NetworkMessages.RELOAD_REQUEST, request))
                .orElseThrow();

        assertEquals(request, read);
    }

    @Test
    @DisplayName("a reload order is not read as a reload request")
    void typesAreNotMixedUp() {
        final ReloadMessage order =
                new ReloadMessage(UUID.randomUUID(), "lobby", System.currentTimeMillis() + LIFETIME);
        final byte[] message = NetworkMessages.reload(NetworkMessages.RELOAD_EXECUTE, order);

        assertTrue(NetworkMessages.readReload(NetworkMessages.RELOAD_REQUEST, message).isEmpty());
        assertTrue(NetworkMessages.readReload(NetworkMessages.RELOAD_EXECUTE, message).isPresent());
    }

    @Test
    @DisplayName("a request that ran out of time is not valid any more")
    void expiredRequest() {
        final long now = System.currentTimeMillis();
        final ReloadMessage request = new ReloadMessage(UUID.randomUUID(), "lobby", now + 1000L);

        assertTrue(request.valid(now));
        assertFalse(request.valid(now + 1001L));
    }

    @Test
    @DisplayName("the report of the proxy survives the way back unchanged")
    void reportSurvivesEncoding() {
        final UUID request = UUID.randomUUID();
        final Map<String, NetworkReloadStatus> results = Map.of(
                "proxy", NetworkReloadStatus.SUCCESS,
                "survival", NetworkReloadStatus.PENDING,
                "citybuild", NetworkReloadStatus.FAILED);

        final NetworkMessages.ReloadReport read =
                NetworkMessages.readReloadReport(NetworkMessages.reloadReport(request, results)).orElseThrow();

        assertEquals(request, read.requestId());
        assertEquals(results, read.results());
    }

    @Test
    @DisplayName("the answer of one Paper server survives the way to the proxy unchanged")
    void resultSurvivesEncoding() {
        final UUID request = UUID.randomUUID();

        final NetworkMessages.ReloadResult read = NetworkMessages
                .readReloadResult(NetworkMessages.reloadResult(request, NetworkReloadStatus.FAILED))
                .orElseThrow();

        assertEquals(request, read.requestId());
        assertEquals(NetworkReloadStatus.FAILED, read.status());
    }

    @Test
    @DisplayName("a broken message is read as nothing instead of as a reload")
    void brokenMessageIsIgnored() {
        assertTrue(NetworkMessages.readReload(NetworkMessages.RELOAD_REQUEST, new byte[] {1, 2, 3}).isEmpty());
        assertTrue(NetworkMessages.readReloadReport(new byte[] {1, 2, 3}).isEmpty());
        assertTrue(NetworkMessages.readReloadResult(new byte[0]).isEmpty());
    }
}
