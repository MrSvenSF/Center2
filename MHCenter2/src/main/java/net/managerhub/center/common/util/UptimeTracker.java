package net.managerhub.center.common.util;

import java.time.Duration;

/**
 * Measures how long the MHCenter2 instance of this platform has been running.
 *
 * <p>Uses a monotonic clock, so a changed system time cannot distort the value.</p>
 */
public final class UptimeTracker {

    private final long startNanos;

    private UptimeTracker(final long startNanos) {
        this.startNanos = startNanos;
    }

    /** @return a tracker that starts counting now. */
    public static UptimeTracker started() {
        return new UptimeTracker(System.nanoTime());
    }

    /** @return the elapsed time since the tracker was started. */
    public Duration uptime() {
        return Duration.ofNanos(System.nanoTime() - startNanos);
    }

    /** @return the elapsed time as a short human readable text, for example {@code 1d 4h 12m 5s}. */
    public String formatted() {
        return format(uptime());
    }

    /**
     * Formats a duration as {@code 1d 4h 12m 5s}, leaving out the leading units that are zero.
     *
     * @param duration duration to format, negative values are treated as zero
     * @return the formatted duration
     */
    public static String format(final Duration duration) {
        long seconds = Math.max(0L, duration.getSeconds());
        final long days = seconds / 86_400L;
        seconds -= days * 86_400L;
        final long hours = seconds / 3_600L;
        seconds -= hours * 3_600L;
        final long minutes = seconds / 60L;
        seconds -= minutes * 60L;

        final StringBuilder text = new StringBuilder();
        if (days > 0L) {
            text.append(days).append("d ");
        }
        if (days > 0L || hours > 0L) {
            text.append(hours).append("h ");
        }
        if (days > 0L || hours > 0L || minutes > 0L) {
            text.append(minutes).append("m ");
        }
        text.append(seconds).append('s');
        return text.toString();
    }
}
