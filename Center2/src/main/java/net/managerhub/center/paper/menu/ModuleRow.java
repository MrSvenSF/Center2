package net.managerhub.center.paper.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The row of the admin menu that shows the installed modules.
 *
 * <p>The {@code modules} entry of {@code Menus/CenterAdmin.yml} is not a button
 * any more, it is the anchor of a whole inventory row: the row the configured
 * slot belongs to. With the default slot 31 that is the row 27 to 35.</p>
 *
 * <p>This is deliberately not a layout engine. It answers exactly one question -
 * which slots may hold a module - and it never returns a slot outside the
 * inventory or a slot another menu entry already owns.</p>
 */
public final class ModuleRow {

    /** Number of slots one inventory row has, and therefore the module capacity. */
    public static final int CAPACITY = 9;

    private ModuleRow() {
        throw new AssertionError("No instances.");
    }

    /**
     * @param anchorSlot the configured slot of the {@code modules} entry
     * @return the first slot of the row that slot belongs to
     */
    public static int startSlot(final int anchorSlot) {
        return (anchorSlot / CAPACITY) * CAPACITY;
    }

    /**
     * The slots that may hold a module, in order.
     *
     * @param anchorSlot    the configured slot of the {@code modules} entry
     * @param inventorySize number of slots the menu has
     * @param reserved      slots that already belong to another entry of the menu
     * @return at most {@link #CAPACITY} free slots inside the inventory
     */
    public static List<Integer> slots(final int anchorSlot, final int inventorySize, final Set<Integer> reserved) {
        final int start = startSlot(anchorSlot);
        final List<Integer> slots = new ArrayList<>(CAPACITY);
        for (int slot = start; slot < start + CAPACITY; slot++) {
            if (slot >= 0 && slot < inventorySize && !reserved.contains(slot)) {
                slots.add(slot);
            }
        }
        return List.copyOf(slots);
    }
}
