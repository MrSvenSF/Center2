package net.managerhub.center.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UptimeTrackerTest {

    @Test
    @DisplayName("the uptime is formatted without empty leading units")
    void formatsUptime() {
        assertEquals("0s", UptimeTracker.format(Duration.ZERO));
        assertEquals("45s", UptimeTracker.format(Duration.ofSeconds(45)));
        assertEquals("1m 5s", UptimeTracker.format(Duration.ofSeconds(65)));
        assertEquals("1h 0m 0s", UptimeTracker.format(Duration.ofHours(1)));
        assertEquals("1d 1h 1m 1s", UptimeTracker.format(Duration.ofSeconds(90_061)));
    }

    @Test
    @DisplayName("a negative duration is treated as zero")
    void handlesNegativeDuration() {
        assertEquals("0s", UptimeTracker.format(Duration.ofSeconds(-10)));
    }
}
