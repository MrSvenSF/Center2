package net.managerhub.center.common.module;

import net.managerhub.center.common.util.Version;

/**
 * The closed version range a module supports, minimum and maximum included.
 *
 * @param minimum oldest supported version
 * @param maximum newest supported version
 */
public record VersionRange(Version minimum, Version maximum) {

    public VersionRange {
        if (minimum == null || maximum == null) {
            throw new IllegalArgumentException("A version range needs a minimum and a maximum.");
        }
        if (minimum.isAfter(maximum)) {
            throw new IllegalArgumentException("The minimum version must not be newer than the maximum version.");
        }
    }

    /**
     * @param version version to check
     * @return {@code true} if the version lies inside this range, borders included
     */
    public boolean includes(final Version version) {
        return !version.isBefore(minimum) && !version.isAfter(maximum);
    }

    /** @return the range for an administrator, for example {@code 0.2.0 - 0.2.99}. */
    public String display() {
        return minimum.display() + " - " + maximum.display();
    }

    @Override
    public String toString() {
        return display();
    }
}
