package net.managerhub.center.common.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One version number, compared by its numeric parts.
 *
 * <p>Only the leading numeric part of a text is used, so
 * {@code 1.21.11-R0.1-SNAPSHOT} is read as {@code 1.21.11}. Everything behind the
 * numbers describes a build and never decides whether a version is newer.</p>
 *
 * <p>Comparison is done part by part and a missing part counts as zero, so
 * {@code 1.21} and {@code 1.21.0} are the same version and {@code 1.21.9} is
 * newer than {@code 1.21.11} is <em>not</em> true - the parts are numbers, not
 * text. That is exactly why a plain string comparison must never be used for
 * version checks.</p>
 */
public final class Version implements Comparable<Version> {

    /** Largest number of parts a version may have. */
    private static final int MAX_PARTS = 4;

    /** Largest number of digits one part may have, so a part always fits into an int. */
    private static final int MAX_DIGITS = 9;

    private final List<Integer> parts;
    private final String display;

    private Version(final List<Integer> parts, final String display) {
        this.parts = List.copyOf(parts);
        this.display = display;
    }

    /**
     * Reads a version number.
     *
     * @param raw the raw text, for example {@code 0.2.0} or {@code 1.21.11-R0.1-SNAPSHOT}
     * @return the version, or empty if the text does not start with a usable number
     */
    public static Optional<Version> of(final String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        final String trimmed = raw.trim();
        int end = 0;
        while (end < trimmed.length()
                && (Character.isDigit(trimmed.charAt(end)) || trimmed.charAt(end) == '.')) {
            end++;
        }
        final String numeric = trimmed.substring(0, end);
        if (numeric.isEmpty() || numeric.startsWith(".") || numeric.endsWith(".") || numeric.contains("..")) {
            return Optional.empty();
        }

        final String[] rawParts = numeric.split("\\.");
        if (rawParts.length > MAX_PARTS) {
            return Optional.empty();
        }
        final List<Integer> parts = new ArrayList<>(rawParts.length);
        for (final String part : rawParts) {
            if (part.length() > MAX_DIGITS) {
                return Optional.empty();
            }
            parts.add(Integer.parseInt(part));
        }
        return Optional.of(new Version(parts, numeric));
    }

    /** @return the numeric part as it was written, for example {@code 1.21.11}. */
    public String display() {
        return display;
    }

    @Override
    public int compareTo(final Version other) {
        final int length = Math.max(parts.size(), other.parts.size());
        for (int index = 0; index < length; index++) {
            final int result = Integer.compare(part(index), other.part(index));
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    /**
     * @param other version to compare with
     * @return {@code true} if this version is older than the other one
     */
    public boolean isBefore(final Version other) {
        return compareTo(other) < 0;
    }

    /**
     * @param other version to compare with
     * @return {@code true} if this version is newer than the other one
     */
    public boolean isAfter(final Version other) {
        return compareTo(other) > 0;
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof Version version && compareTo(version) == 0;
    }

    @Override
    public int hashCode() {
        // Trailing zeros do not change the version, so they must not change the hash.
        final List<Integer> canonical = new ArrayList<>(parts);
        while (canonical.size() > 1 && canonical.getLast() == 0) {
            canonical.removeLast();
        }
        return Objects.hash(canonical);
    }

    @Override
    public String toString() {
        return display;
    }

    private int part(final int index) {
        return index < parts.size() ? parts.get(index) : 0;
    }
}
