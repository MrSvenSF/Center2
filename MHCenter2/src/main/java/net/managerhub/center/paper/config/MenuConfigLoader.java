package net.managerhub.center.paper.config;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import net.managerhub.center.Center;
import net.managerhub.center.common.config.ConfigurationException;
import org.bukkit.Material;

/**
 * Reads and validates the three menu files of MHCenter2.
 *
 * <p>Slots are checked against the menu size and against each other, so two menu
 * entries can never share one slot. Materials must exist and must be usable as an
 * item. Any violation rejects the whole reload.</p>
 */
final class MenuConfigLoader {

    private MenuConfigLoader() {
        throw new AssertionError("No instances.");
    }

    static CenterInfoMenuSettings loadCenterInfo(final Path file) throws ConfigurationException {
        final YamlReader reader = YamlReader.read(file, Center.CENTER_INFO_MENU_FILE);
        final MenuLayout layout = layout(reader);
        final Slots slots = new Slots(reader, layout.size());

        final int creatorSlot = slots.claim("items.creator.slot");
        final int organizationSlot = slots.claim("items.organization.slot");
        final MenuItemSettings admin = item(reader, slots, "items.admin");
        final String adminName = reader.requireMiniMessage("items.admin.name");
        final MenuItemSettings close = item(reader, slots, "items.close");

        return new CenterInfoMenuSettings(layout, creatorSlot, organizationSlot, admin, adminName, close);
    }

    static CenterAdminMenuSettings loadCenterAdmin(final Path file) throws ConfigurationException {
        final YamlReader reader = YamlReader.read(file, Center.CENTER_ADMIN_MENU_FILE);
        final MenuLayout layout = layout(reader);
        final Slots slots = new Slots(reader, layout.size());

        return new CenterAdminMenuSettings(layout,
                item(reader, slots, "items.status"),
                item(reader, slots, "items.server-status"),
                item(reader, slots, "items.modules"),
                item(reader, slots, "items.back"));
    }

    static ServerStatusMenuSettings loadServerStatus(final Path file) throws ConfigurationException {
        final YamlReader reader = YamlReader.read(file, Center.SERVER_STATUS_MENU_FILE);
        final MenuLayout layout = layout(reader);
        final Slots slots = new Slots(reader, layout.size());

        return new ServerStatusMenuSettings(layout,
                item(reader, slots, "items.velocity"),
                item(reader, slots, "items.back"));
    }

    private static MenuLayout layout(final YamlReader reader) throws ConfigurationException {
        reader.requireConfigVersion();
        return new MenuLayout(
                reader.requireMiniMessage("title"),
                reader.requireIntInRange("rows", MenuLayout.MIN_ROWS, MenuLayout.MAX_ROWS),
                reader.requireBoolean("filler.enabled"),
                reader.requireMaterial("filler.material"));
    }

    private static MenuItemSettings item(final YamlReader reader,
                                         final Slots slots,
                                         final String path) throws ConfigurationException {
        final int slot = slots.claim(path + ".slot");
        return new MenuItemSettings(slot, reader.requireMaterial(path + ".material"));
    }

    /** Reads slot numbers and makes sure that no slot is used twice. */
    private static final class Slots {

        private final YamlReader reader;
        private final int size;
        private final Map<Integer, String> used = new LinkedHashMap<>();

        private Slots(final YamlReader reader, final int size) {
            this.reader = reader;
            this.size = size;
        }

        private int claim(final String path) throws ConfigurationException {
            final int slot = reader.requireInt(path);
            if (slot < 0 || slot >= size) {
                throw reader.error("'" + path + "' is " + slot + ", but the menu only has the slots 0 to "
                        + (size - 1) + ". Increase 'rows' or choose another slot.");
            }
            final String owner = used.putIfAbsent(slot, path);
            if (owner != null) {
                throw reader.error("'" + path + "' uses slot " + slot + ", which is already used by '" + owner + "'.");
            }
            return slot;
        }
    }
}
