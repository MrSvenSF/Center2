package net.managerhub.center.paper.status;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.Component;
import net.managerhub.center.Center;
import net.managerhub.center.common.language.Language;
import net.managerhub.center.common.language.MessageKey;
import net.managerhub.center.common.util.UptimeTracker;
import net.managerhub.center.paper.text.Text;
import org.bukkit.Bukkit;

/**
 * Collects the administrative status report of Center2 on Paper.
 *
 * <p>The report contains version, platform, Minecraft version and uptime. It
 * never contains absolute file paths, credentials or other sensitive values, so
 * it is safe to show it in the menu and in the chat of an administrator.</p>
 */
public final class StatusService {

    /** One line of the status report. */
    public record Entry(String label, String value) {
    }

    private final UptimeTracker uptime;

    public StatusService(final UptimeTracker uptime) {
        this.uptime = uptime;
    }

    /**
     * @param language texts of the active configuration
     * @return every status line as a label/value pair
     */
    public List<Entry> entries(final Language language) {
        final List<Entry> entries = new ArrayList<>();
        entries.add(new Entry(language.get(MessageKey.STATUS_LABEL_VERSION), Center.VERSION));
        entries.add(new Entry(language.get(MessageKey.STATUS_LABEL_PLATFORM), Bukkit.getName()));
        entries.add(new Entry(language.get(MessageKey.STATUS_LABEL_MINECRAFT), minecraftVersion()));
        entries.add(new Entry(language.get(MessageKey.STATUS_LABEL_UPTIME), uptime.formatted()));
        return List.copyOf(entries);
    }

    /**
     * @param language texts of the active configuration
     * @return the full report, formatted for the chat or the console
     */
    public List<Component> chatReport(final Language language) {
        final List<Component> lines = new ArrayList<>();
        lines.add(Text.of(language.get(MessageKey.STATUS_HEADER, "product", Center.PRODUCT_NAME)));
        for (final Entry entry : entries(language)) {
            lines.add(Text.of(language.get(MessageKey.STATUS_LINE,
                    "label", entry.label(), "value", Text.escape(entry.value()))));
        }
        return List.copyOf(lines);
    }

    /**
     * @param language texts of the active configuration
     * @return the full report, formatted as menu lore
     */
    public List<Component> menuLore(final Language language) {
        final List<Component> lines = new ArrayList<>();
        for (final Entry entry : entries(language)) {
            lines.add(Text.lore(language.get(MessageKey.STATUS_LORE_LINE,
                    "label", entry.label(), "value", Text.escape(entry.value()))));
        }
        return List.copyOf(lines);
    }

    private String minecraftVersion() {
        final String bukkitVersion = Bukkit.getBukkitVersion();
        final int separator = bukkitVersion.indexOf("-R");
        return separator > 0 ? bukkitVersion.substring(0, separator) : bukkitVersion;
    }
}
