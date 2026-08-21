package net.managerhub.center.common.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Installs bundled default files into a plugin data folder.
 *
 * <p>An existing configuration is never touched, so it can not be overwritten by
 * a plugin update or a restart. The only exception is a completely empty file:
 * such a file carries no configuration at all and can only be a leftover of a
 * failed write, so it is replaced by the bundled default. A file that has content
 * but is wrong stays untouched and is reported by the normal configuration
 * validation instead.</p>
 */
public final class DefaultFiles {

    /** What happened to one default file. */
    public enum Installation {

        /** The file was already there and has content. */
        KEPT,

        /** The file did not exist and was created. */
        CREATED,

        /** The file existed but was empty, so the default was written again. */
        REPAIRED
    }

    /** Above this size a file clearly holds content and is not read at all. */
    private static final long EMPTY_LIMIT_BYTES = 512L;

    /** A file may still be empty if it only holds this invisible character. */
    private static final String BYTE_ORDER_MARK = "\uFEFF";

    private DefaultFiles() {
        throw new AssertionError("No instances.");
    }

    /**
     * Makes sure that a usable file exists at the target path.
     *
     * @param target       destination file inside the plugin data folder
     * @param resourceName resource path inside the plugin jar
     * @return what had to be done
     * @throws IOException if the resource is missing or the file cannot be written
     */
    public static Installation install(final Path target, final String resourceName) throws IOException {
        final boolean existed = Files.exists(target);
        if (existed && !carriesNoContent(target)) {
            return Installation.KEPT;
        }

        final Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (InputStream source = DefaultFiles.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (source == null) {
                throw new IOException("The bundled default file '" + resourceName + "' is missing from the plugin jar.");
            }
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return existed ? Installation.REPAIRED : Installation.CREATED;
    }

    /**
     * Checks whether a file is obviously empty.
     *
     * <p>That is a file of zero bytes and a file that holds nothing but a byte
     * order mark or whitespace. Such a file cannot be a configuration, it can
     * only be the leftover of a write that never finished. A file that holds
     * anything else - even only comments or broken YAML - counts as content and
     * is never touched here.</p>
     *
     * @param file file to look at
     * @return {@code true} if the file holds no content at all
     * @throws IOException if the size of the file cannot be read
     */
    private static boolean carriesNoContent(final Path file) throws IOException {
        final long size = Files.size(file);
        if (size == 0L) {
            return true;
        }
        if (size > EMPTY_LIMIT_BYTES) {
            return false;
        }
        final String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (final IOException unreadable) {
            // Not readable as text, so it is not an obviously empty file.
            return false;
        }
        return content.replace(BYTE_ORDER_MARK, "").isBlank();
    }
}
