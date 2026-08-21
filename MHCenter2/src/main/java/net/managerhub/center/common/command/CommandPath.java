package net.managerhub.center.common.command;

import java.util.ArrayList;
import java.util.List;

import net.managerhub.center.common.config.ConfigurationException;

/**
 * One complete command path, for example {@code center info}.
 *
 * <p>A path is configured as a single text value in {@code Commands.yml}. The
 * first segment is the command that is registered on the server, every further
 * segment is a fixed argument in front of the actual command. Every segment is
 * validated and normalized by {@link CommandNames}, so {@code Center Info} and
 * {@code center info} can never exist next to each other.</p>
 *
 * @param segments the normalized segments, at least one
 */
public record CommandPath(List<String> segments) {

    public CommandPath {
        segments = List.copyOf(segments);
    }

    /**
     * Validates a configured command path and builds it.
     *
     * @param path configuration path, used for the error message
     * @param raw  raw value from {@code Commands.yml}, segments separated by spaces
     * @return the validated command path
     * @throws ConfigurationException if the value is empty or a segment is invalid
     */
    public static CommandPath of(final String path, final String raw) throws ConfigurationException {
        if (raw == null || raw.isBlank()) {
            throw new ConfigurationException("Commands.yml: '" + path
                    + "' is missing or empty. A command path looks like \"center info\".");
        }
        final List<String> segments = new ArrayList<>();
        for (final String segment : raw.trim().split("\\s+")) {
            segments.add(CommandNames.validate(path, segment));
        }
        return new CommandPath(segments);
    }

    /** @return the segment that is registered as a command on the server. */
    public String rootName() {
        return segments.getFirst();
    }

    /** @return the segments behind the root name, empty for a single segment path. */
    public List<String> tail() {
        return segments.subList(1, segments.size());
    }

    /** @return the path as it is written in the configuration, for example {@code center info}. */
    public String display() {
        return String.join(" ", segments);
    }

    @Override
    public String toString() {
        return display();
    }
}
