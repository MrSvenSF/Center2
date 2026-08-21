package net.managerhub.center.common.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.managerhub.center.api.ModulePlatform;
import net.managerhub.center.common.util.Version;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VersionRangeTest {

    private static final VersionRange RANGE = range("1.21.4", "1.21.8");

    @Test
    @DisplayName("a version below the minimum is outside")
    void refusesVersionBelowMinimum() {
        assertFalse(RANGE.includes(version("1.21.3")));
        assertFalse(RANGE.includes(version("1.20.9")));
    }

    @Test
    @DisplayName("a version above the maximum is outside")
    void refusesVersionAboveMaximum() {
        assertFalse(RANGE.includes(version("1.21.9")));
        assertFalse(RANGE.includes(version("1.22")));
    }

    @Test
    @DisplayName("a version inside the range and both borders are included")
    void acceptsVersionInsideAndBorders() {
        assertTrue(RANGE.includes(version("1.21.4")));
        assertTrue(RANGE.includes(version("1.21.6")));
        assertTrue(RANGE.includes(version("1.21.8")));
    }

    @Test
    @DisplayName("a range of exactly one version accepts only that version")
    void acceptsSingleVersionRange() {
        final VersionRange single = range("0.2.0", "0.2.0");

        assertTrue(single.includes(version("0.2.0")));
        assertFalse(single.includes(version("0.2.1")));
        assertFalse(single.includes(version("0.1.9")));
    }

    @Test
    @DisplayName("a minimum newer than the maximum is refused")
    void refusesInvertedRange() {
        assertThrows(IllegalArgumentException.class, () -> range("0.3.0", "0.2.0"));
    }

    @Test
    @DisplayName("the range is shown as minimum and maximum")
    void showsBothBorders() {
        assertEquals("1.21.4 - 1.21.8", RANGE.display());
    }

    private static VersionRange range(final String minimum, final String maximum) {
        return new VersionRange(version(minimum), version(maximum));
    }

    private static Version version(final String raw) {
        return Version.of(raw).orElseThrow();
    }
}
