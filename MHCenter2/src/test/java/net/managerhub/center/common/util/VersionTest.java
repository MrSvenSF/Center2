package net.managerhub.center.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VersionTest {

    @Test
    @DisplayName("a plain version is read")
    void readsPlainVersion() {
        assertEquals("0.2.0", version("0.2.0").display());
        assertEquals("1.21", version("1.21").display());
    }

    @Test
    @DisplayName("everything behind the numbers is a build name and is cut off")
    void readsVersionWithSuffix() {
        assertEquals("1.21.11", version("1.21.11-R0.1-SNAPSHOT").display());
        assertEquals("1.21.4", version(" 1.21.4-pre1 ").display());
        assertEquals("1.0.0", version("1.0.0-beta.1").display());
    }

    @ParameterizedTest
    @DisplayName("a text without a usable number is refused")
    @ValueSource(strings = {"", "   ", "latest", ".1.2", "1.2.", "1..2", "-1", "1.2.3.4.5", "1234567890"})
    void refusesUnusableVersions(final String raw) {
        assertTrue(Version.of(raw).isEmpty(), raw);
    }

    @Test
    @DisplayName("a missing text is refused")
    void refusesNull() {
        assertTrue(Version.of(null).isEmpty());
    }

    @Test
    @DisplayName("versions are compared by number, never as text")
    void comparesByNumber() {
        // As text "1.21.9" would be greater than "1.21.11", which is exactly the
        // mistake a version check must not make.
        assertTrue(version("1.21.9").isBefore(version("1.21.11")));
        assertTrue(version("1.21.11").isAfter(version("1.21.9")));
        assertTrue(version("0.2.0").isAfter(version("0.1.99")));
        assertTrue(version("2.0").isAfter(version("1.99.99")));
    }

    @Test
    @DisplayName("a missing part counts as zero")
    void treatsMissingPartsAsZero() {
        assertEquals(version("1.21"), version("1.21.0"));
        assertEquals(version("1.21").hashCode(), version("1.21.0").hashCode());
        assertFalse(version("1.21").isBefore(version("1.21.0")));
        assertTrue(version("1.21").isBefore(version("1.21.1")));
    }

    @Test
    @DisplayName("the same version is neither before nor after itself")
    void comparesEqualVersions() {
        assertFalse(version("0.2.0").isBefore(version("0.2.0")));
        assertFalse(version("0.2.0").isAfter(version("0.2.0")));
        assertEquals(0, version("0.2.0").compareTo(version("0.2.0")));
    }

    private static Version version(final String raw) {
        return Version.of(raw).orElseThrow();
    }
}
