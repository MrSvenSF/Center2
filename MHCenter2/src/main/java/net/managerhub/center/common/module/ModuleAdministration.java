package net.managerhub.center.common.module;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import net.managerhub.center.Center;
import net.managerhub.center.common.language.Language;
import net.managerhub.center.common.language.MessageKey;

/**
 * The module administration itself, without any platform.
 *
 * <p>Paper and Velocity only supply where the modules are ({@link Modules}), how
 * their own message format escapes foreign text and where an answer goes. The
 * decisions and the wording live here, so both platforms really behave the
 * same.</p>
 *
 * <p>No answer ever carries a technical detail. A broken module says "module
 * error" and points at the console; the cause is logged there.</p>
 */
public final class ModuleAdministration {

    /** What the platform offers of its module system. */
    public interface Modules {

        /**
         * @return {@code false} if the module system could not be started at all,
         *         which is a different situation from "no module is installed"
         */
        boolean available();

        /** @return every installed module with its state. */
        List<ModuleLoader.InstalledModule> installed();

        /** @return the number of running modules. */
        int enabledCount();

        /** Reads the module folder again. */
        void reload();

        /**
         * @param moduleId id of the module
         * @return the state afterwards, empty if no such module is installed
         */
        Optional<ModuleStatus> enable(String moduleId);

        /**
         * @param moduleId id of the module
         * @return the state afterwards, empty if no such module is installed
         */
        Optional<ModuleStatus> disable(String moduleId);
    }

    /** How the platform makes text of a module safe for its own message format. */
    @FunctionalInterface
    public interface Escaper {

        /**
         * @param raw text that comes from a module
         * @return the text, safe to put into a message
         */
        String escape(String raw);
    }

    private ModuleAdministration() {
        throw new AssertionError("No instances.");
    }

    /** Sends the overview of every installed module. */
    public static void list(final Modules modules,
                            final Language texts,
                            final Escaper escape,
                            final Consumer<String> out) {
        if (unavailable(modules, texts, out)) {
            return;
        }
        out.accept(texts.get(MessageKey.MODULES_LIST_HEADER, "product", Center.PRODUCT_NAME));
        final List<ModuleLoader.InstalledModule> installed = modules.installed();
        if (installed.isEmpty()) {
            out.accept(texts.get(MessageKey.MODULES_LIST_EMPTY));
            return;
        }
        for (final ModuleLoader.InstalledModule module : installed) {
            out.accept(texts.get(MessageKey.MODULES_LIST_ENTRY,
                    "module", escape.escape(module.descriptor().name()),
                    "id", escape.escape(module.descriptor().id()),
                    "version", escape.escape(module.descriptor().version()),
                    "status", ModuleTexts.status(texts, module.status())));
        }
    }

    /** Reads the module folder again and answers with the new numbers. */
    public static void reload(final Modules modules, final Language texts, final Consumer<String> out) {
        if (unavailable(modules, texts, out)) {
            return;
        }
        modules.reload();
        out.accept(texts.get(MessageKey.MODULES_RELOADED,
                "installed", Integer.toString(modules.installed().size()),
                "enabled", Integer.toString(modules.enabledCount())));
    }

    /**
     * Starts one module.
     *
     * @param moduleId    id the administrator typed, may be empty
     * @param commandPath the path the administrator really used, for the usage hint
     */
    public static void enable(final Modules modules,
                              final Language texts,
                              final Escaper escape,
                              final String moduleId,
                              final String commandPath,
                              final Consumer<String> out) {
        change(modules, texts, escape, moduleId, commandPath, out, true);
    }

    /**
     * Stops one module.
     *
     * @param moduleId    id the administrator typed, may be empty
     * @param commandPath the path the administrator really used, for the usage hint
     */
    public static void disable(final Modules modules,
                               final Language texts,
                               final Escaper escape,
                               final String moduleId,
                               final String commandPath,
                               final Consumer<String> out) {
        change(modules, texts, escape, moduleId, commandPath, out, false);
    }

    /** @return the ids of every installed module, used for tab completion. */
    public static List<String> moduleIds(final Modules modules) {
        return modules.installed().stream().map(module -> module.descriptor().id()).toList();
    }

    private static void change(final Modules modules,
                               final Language texts,
                               final Escaper escape,
                               final String moduleId,
                               final String commandPath,
                               final Consumer<String> out,
                               final boolean enable) {
        if (unavailable(modules, texts, out)) {
            return;
        }
        if (moduleId == null || moduleId.isBlank()) {
            out.accept(texts.get(MessageKey.MODULES_USAGE, "path", commandPath));
            return;
        }
        final Optional<ModuleLoader.InstalledModule> known = modules.installed().stream()
                .filter(module -> module.descriptor().id().equalsIgnoreCase(moduleId))
                .findFirst();
        if (known.isEmpty()) {
            out.accept(texts.get(MessageKey.MODULES_UNKNOWN, "module", escape.escape(moduleId)));
            return;
        }

        final String name = escape.escape(known.get().descriptor().name());
        final ModuleStatus before = known.get().status();
        if (enable && before == ModuleStatus.ENABLED) {
            out.accept(texts.get(MessageKey.MODULES_ALREADY_ENABLED, "module", name));
            return;
        }
        if (!enable && before == ModuleStatus.DISABLED) {
            out.accept(texts.get(MessageKey.MODULES_ALREADY_DISABLED, "module", name));
            return;
        }

        final Optional<ModuleStatus> after = enable ? modules.enable(moduleId) : modules.disable(moduleId);
        if (after.isEmpty()) {
            out.accept(texts.get(MessageKey.MODULES_UNKNOWN, "module", name));
            return;
        }
        out.accept(switch (after.get()) {
            case ENABLED, DISABLED -> texts.get(
                    enable ? MessageKey.MODULES_ENABLED : MessageKey.MODULES_DISABLED, "module", name);
            case INCOMPATIBLE_CENTER, INCOMPATIBLE_MINECRAFT -> texts.get(MessageKey.MODULES_INCOMPATIBLE,
                    "module", name, "reason", ModuleTexts.reason(texts, after.get()).orElse(""));
            case ERROR -> texts.get(MessageKey.MODULES_ERROR, "module", name);
        });
    }

    /**
     * @return {@code true} if the module system is not there at all; the answer
     *         was already sent in that case
     */
    private static boolean unavailable(final Modules modules, final Language texts, final Consumer<String> out) {
        if (modules.available()) {
            return false;
        }
        // "no module is installed" would be a wrong and misleading answer here.
        out.accept(texts.get(MessageKey.MODULES_UNAVAILABLE, "product", Center.PRODUCT_NAME));
        return true;
    }
}
