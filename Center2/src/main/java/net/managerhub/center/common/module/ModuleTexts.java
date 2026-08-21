package net.managerhub.center.common.module;

import java.util.Optional;

import net.managerhub.center.Center;
import net.managerhub.center.common.language.Language;
import net.managerhub.center.common.language.MessageKey;
import net.managerhub.center.api.ModulePlatform;

/**
 * The visible texts of a module state.
 *
 * <p>Everything an administrator sees in a menu or in the chat is built here, so
 * a state always reads the same way. A technical detail - an exception, a class
 * name, a file path or a stack trace - never appears in one of these texts; it
 * belongs into the server console.</p>
 */
public final class ModuleTexts {

    private ModuleTexts() {
        throw new AssertionError("No instances.");
    }

    /**
     * @param language texts of the active configuration
     * @param status   state of the module
     * @return the visible name of the state
     */
    public static String status(final Language language, final ModuleStatus status) {
        return language.get(switch (status) {
            case ENABLED -> MessageKey.MODULE_STATUS_ENABLED;
            case DISABLED -> MessageKey.MODULE_STATUS_DISABLED;
            case INCOMPATIBLE_CENTER, INCOMPATIBLE_MINECRAFT -> MessageKey.MODULE_STATUS_INCOMPATIBLE;
            case ERROR -> MessageKey.MODULE_STATUS_ERROR;
        });
    }

    /**
     * @param language texts of the active configuration
     * @param platform the platform the module was built for
     * @return the visible name of the platform, for example {@code Paper & Velocity}
     */
    public static String platform(final Language language, final ModulePlatform platform) {
        return language.get(switch (platform) {
            case PAPER -> MessageKey.MODULE_PLATFORM_PAPER;
            case VELOCITY -> MessageKey.MODULE_PLATFORM_VELOCITY;
            case BOTH -> MessageKey.MODULE_PLATFORM_BOTH;
        });
    }

    /**
     * @param language texts of the active configuration
     * @param status   state of the module
     * @return the short reason of an incompatible module, empty for every other state
     */
    public static Optional<String> reason(final Language language, final ModuleStatus status) {
        return switch (status) {
            case INCOMPATIBLE_CENTER -> Optional.of(
                    language.get(MessageKey.MODULE_REASON_CENTER, "product", Center.PRODUCT_NAME));
            case INCOMPATIBLE_MINECRAFT -> Optional.of(language.get(MessageKey.MODULE_REASON_MINECRAFT));
            default -> Optional.empty();
        };
    }
}
