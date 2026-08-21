package net.managerhub.center.common.module;

import java.util.function.Supplier;

import net.managerhub.center.api.ModuleLogger;
import net.managerhub.center.common.language.Language;
import net.managerhub.center.common.language.MessageKey;

/**
 * Writes every module problem into the log of the platform.
 *
 * <p>The texts come from the language files, the exception and its stack trace
 * come from the module. Both platforms use this class; they only supply their own
 * {@link ModuleLogger}.</p>
 *
 * <p>The language is read through a supplier, because a successful
 * {@code /center reload} may replace it while the server is running.</p>
 */
public final class LoggingModuleReport implements ModuleReport {

    private final ModuleLogger logger;
    private final Supplier<Language> language;

    /**
     * @param logger   log of MHCenter2 itself, not the log of a module
     * @param language the texts that are currently active
     */
    public LoggingModuleReport(final ModuleLogger logger, final Supplier<Language> language) {
        this.logger = logger;
        this.language = language;
    }

    @Override
    public void skipped(final String source, final String reason) {
        logger.warn(language.get().get(MessageKey.MODULE_SKIPPED, "module", source, "reason", reason));
    }

    @Override
    public void incompatibleCenter(final ModuleDescriptor module, final String running) {
        logger.warn(language.get().get(MessageKey.MODULE_INCOMPATIBLE_CENTER,
                "module", module.name(),
                "id", module.id(),
                "version", module.version(),
                "required", module.centerVersions().display(),
                "running", running));
    }

    @Override
    public void incompatibleMinecraft(final ModuleDescriptor module, final String running) {
        logger.warn(language.get().get(MessageKey.MODULE_INCOMPATIBLE_MINECRAFT,
                "module", module.name(),
                "id", module.id(),
                "version", module.version(),
                "required", module.minecraftVersions().map(VersionRange::display).orElse("-"),
                "running", running));
    }

    @Override
    public void administrativelyDisabled(final ModuleDescriptor module) {
        logger.info(language.get().get(MessageKey.MODULE_ADMIN_DISABLED,
                "module", module.name(), "id", module.id()));
    }

    @Override
    public void jarChanged(final ModuleDescriptor module, final String source) {
        logger.warn(language.get().get(MessageKey.MODULE_JAR_CHANGED,
                "module", module.name(), "id", module.id(), "file", source));
    }

    @Override
    public void error(final ModuleDescriptor module,
                      final ModuleLifecycle step,
                      final String reason,
                      final Throwable failure) {
        logger.error(language.get().get(MessageKey.MODULE_ERROR,
                "module", module.name(),
                "id", module.id(),
                "version", module.version(),
                "step", step.name(),
                "reason", reason), failure);
    }

    @Override
    public void scanFailed(final String directory, final Throwable failure) {
        logger.error(language.get().get(MessageKey.MODULE_SCAN_FAILED,
                "directory", directory, "reason", describe(failure)), failure);
    }

    @Override
    public void stateUnreadable(final Throwable failure) {
        logger.error(language.get().get(MessageKey.MODULE_STATE_UNREADABLE,
                "reason", describe(failure)), failure);
    }

    @Override
    public void statePersistFailed(final ModuleDescriptor module,
                                   final boolean disabled,
                                   final Throwable failure) {
        logger.error(language.get().get(
                disabled ? MessageKey.MODULE_STATE_DISABLE_NOT_STORED : MessageKey.MODULE_STATE_ENABLE_NOT_STORED,
                "module", module.name(),
                "id", module.id(),
                "reason", describe(failure)), failure);
    }

    private static String describe(final Throwable failure) {
        if (failure == null) {
            return "-";
        }
        final String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
