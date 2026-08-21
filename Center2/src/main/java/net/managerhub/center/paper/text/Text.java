package net.managerhub.center.paper.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * Small helper around Adventure MiniMessage.
 *
 * <p>Item names and lore lines are built without the default italic style of
 * Minecraft item text, so configured text looks the same everywhere.</p>
 */
public final class Text {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private Text() {
        throw new AssertionError("No instances.");
    }

    /** @return the parsed MiniMessage text. */
    public static Component of(final String miniMessage) {
        return MINI_MESSAGE.deserialize(miniMessage);
    }

    /** @return the parsed MiniMessage text without the default italic item style. */
    public static Component item(final String miniMessage) {
        return of(miniMessage).decoration(TextDecoration.ITALIC, false);
    }

    /** @return a gray lore line without the default italic item style. */
    public static Component lore(final String miniMessage) {
        return Component.text()
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(of(miniMessage))
                .build();
    }

    /**
     * Escapes MiniMessage tags in text that comes from outside the configuration,
     * for example a sender name used for the {@code {USER}} placeholder.
     *
     * @param raw untrusted text
     * @return the escaped text
     */
    public static String escape(final String raw) {
        return MINI_MESSAGE.escapeTags(raw);
    }
}
