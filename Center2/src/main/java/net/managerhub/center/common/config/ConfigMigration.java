package net.managerhub.center.common.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adds new default entries to a configuration file an administrator already has.
 *
 * <p>Until now a new entry in a Center2 default meant that the whole file had to
 * be deleted, because Center2 never touches an existing configuration and the
 * loaders demand every known key. That is not acceptable for an update, so this
 * class fills exactly the gap:</p>
 *
 * <ul>
 *   <li>a value the administrator wrote is never changed and never removed,</li>
 *   <li>only entries that are missing completely are added, with their comment
 *       and at the place the default has them,</li>
 *   <li>{@code config-version} is raised to the version of the default when
 *       something was added or the file was older,</li>
 *   <li>a file that declares a <em>newer</em> version than this Center2 knows is
 *       never touched at all.</li>
 * </ul>
 *
 * <p>This is deliberately a small text merge and not a configuration engine. It
 * understands the shape Center2 writes: two space indentation, {@code key: value}
 * and comment lines. If a file does not look like that, nothing is changed and
 * the normal configuration validation reports the problem as before.</p>
 */
public final class ConfigMigration {

    /** What one migration did. */
    public record Result(List<String> added, int fromVersion, int toVersion, boolean skippedNewer) {

        public Result {
            added = List.copyOf(added);
        }

        /** @return {@code true} if the file was really written. */
        public boolean changed() {
            return !skippedNewer && (!added.isEmpty() || fromVersion != toVersion);
        }
    }

    private static final Pattern ENTRY = Pattern.compile("^(\\s*)([A-Za-z0-9_.-]+):(.*)$");
    private static final String VERSION_KEY = "config-version";
    private static final int INDENT = 2;

    private ConfigMigration() {
        throw new AssertionError("No instances.");
    }

    /**
     * Brings one configuration file up to the bundled default.
     *
     * @param file            the file of the administrator
     * @param defaultResource resource path of the bundled default inside the jar
     * @return what was done
     * @throws IOException if the file or the bundled default cannot be read or written
     */
    public static Result apply(final Path file, final String defaultResource) throws IOException {
        if (!Files.exists(file)) {
            return new Result(List.of(), 0, 0, false);
        }
        final List<String> target = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
        final List<String> defaults = bundled(defaultResource);

        final int targetVersion = version(target);
        final int defaultVersion = version(defaults);
        if (targetVersion > defaultVersion) {
            // A file from a newer Center2. Never rewrite what we do not understand.
            return new Result(List.of(), targetVersion, targetVersion, true);
        }

        final List<String> added = new ArrayList<>();
        final Set<String> present = paths(target);
        for (final Entry entry : entries(defaults)) {
            if (VERSION_KEY.equals(entry.path()) || present.contains(entry.path())) {
                continue;
            }
            if (!insert(target, entry, defaults)) {
                continue;
            }
            present.add(entry.path());
            added.add(entry.path());
        }

        final int newVersion = added.isEmpty() && targetVersion == defaultVersion ? targetVersion : defaultVersion;
        if (newVersion != targetVersion) {
            raiseVersion(target, newVersion);
        }
        final Result result = new Result(added, targetVersion, newVersion, false);
        if (result.changed()) {
            Files.write(file, target, StandardCharsets.UTF_8);
        }
        return result;
    }

    /** One {@code key: value} line of the default, with its own path. */
    private record Entry(String path, int line, int indent) {
    }

    private static List<String> bundled(final String resource) throws IOException {
        try (InputStream in = ConfigMigration.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("The bundled default file '" + resource + "' is missing from the plugin jar.");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }
    }

    /** @return every {@code key: value} path of the file, sections included. */
    private static Set<String> paths(final List<String> lines) {
        final Set<String> paths = new LinkedHashSet<>();
        for (final Entry entry : entries(lines)) {
            paths.add(entry.path());
        }
        return paths;
    }

    /** @return every entry of the file in file order, with its path and indentation. */
    private static List<Entry> entries(final List<String> lines) {
        final List<Entry> entries = new ArrayList<>();
        final Deque<String> parents = new ArrayDeque<>();
        for (int index = 0; index < lines.size(); index++) {
            final String line = lines.get(index);
            if (isIgnorable(line)) {
                continue;
            }
            final Matcher matcher = ENTRY.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            final int indent = matcher.group(1).length();
            final int level = indent / INDENT;
            while (parents.size() > level) {
                parents.removeLast();
            }
            if (parents.size() < level) {
                // The file does not use the expected indentation. Leave it alone.
                return List.of();
            }
            parents.addLast(matcher.group(2));
            entries.add(new Entry(String.join(".", parents), index, indent));
        }
        return entries;
    }

    /**
     * Puts one missing entry of the default into the file of the administrator.
     *
     * @return {@code true} if it could be placed
     */
    private static boolean insert(final List<String> target, final Entry entry, final List<String> defaults) {
        final List<String> block = new ArrayList<>();
        for (int index = commentStart(defaults, entry.line()); index <= entry.line(); index++) {
            block.add(defaults.get(index));
        }

        final int at = insertionPoint(target, entry);
        if (at < 0) {
            return false;
        }
        // A block that brings its own comment reads better with an empty line
        // in front of it, exactly like in the bundled default.
        final boolean startsWithComment = block.getFirst().stripLeading().startsWith("#");
        if (at > 0 && !target.get(at - 1).isBlank() && (startsWithComment || entry.indent() == 0)) {
            block.addFirst("");
        }
        target.addAll(at, block);
        return true;
    }

    /** @return the first line of the comment that belongs to this entry. */
    private static int commentStart(final List<String> lines, final int entryLine) {
        int start = entryLine;
        while (start > 0 && lines.get(start - 1).stripLeading().startsWith("#")) {
            start--;
        }
        return start;
    }

    /**
     * Finds where a missing entry belongs.
     *
     * @return the line the entry is inserted before, or {@code -1} if the parent
     *         section does not exist in the file
     */
    private static int insertionPoint(final List<String> target, final Entry entry) {
        final int lastDot = entry.path().lastIndexOf('.');
        if (lastDot < 0) {
            return target.size();
        }
        final String parent = entry.path().substring(0, lastDot);
        final List<Entry> entries = entries(target);
        final Entry parentEntry = entries.stream().filter(known -> known.path().equals(parent)).findFirst()
                .orElse(null);
        if (parentEntry == null) {
            return -1;
        }
        // Behind the last line that still belongs to the parent section.
        int end = parentEntry.line();
        for (int index = parentEntry.line() + 1; index < target.size(); index++) {
            final String line = target.get(index);
            if (line.isBlank() || line.stripLeading().startsWith("#")) {
                continue;
            }
            final int indent = indentOf(line);
            if (indent <= parentEntry.indent()) {
                break;
            }
            end = index;
        }
        return end + 1;
    }

    private static void raiseVersion(final List<String> lines, final int version) {
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).startsWith(VERSION_KEY + ":")) {
                lines.set(index, VERSION_KEY + ": " + version);
                return;
            }
        }
        lines.addFirst(VERSION_KEY + ": " + version);
    }

    /** @return the declared configuration version, or {@code 0} if there is none. */
    private static int version(final List<String> lines) {
        for (final String line : lines) {
            if (!line.startsWith(VERSION_KEY + ":")) {
                continue;
            }
            try {
                return Integer.parseInt(line.substring(VERSION_KEY.length() + 1).trim());
            } catch (final NumberFormatException unusable) {
                return 0;
            }
        }
        return 0;
    }

    private static boolean isIgnorable(final String line) {
        final String trimmed = line.stripLeading();
        return trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("-");
    }

    private static int indentOf(final String line) {
        int indent = 0;
        while (indent < line.length() && line.charAt(indent) == ' ') {
            indent++;
        }
        return indent;
    }
}
