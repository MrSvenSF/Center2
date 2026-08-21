package net.managerhub.center.paper.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ModuleRowTest {

    private static final Set<Integer> DEFAULT_RESERVED = Set.of(12, 14, 40);
    private static final int DEFAULT_SIZE = 45;

    @Test
    @DisplayName("the anchor slot names the whole row it belongs to")
    void findsTheRowOfTheAnchor() {
        assertEquals(27, ModuleRow.startSlot(31));
        assertEquals(27, ModuleRow.startSlot(27));
        assertEquals(27, ModuleRow.startSlot(35));
        assertEquals(0, ModuleRow.startSlot(4));
        assertEquals(36, ModuleRow.startSlot(40));
    }

    @Test
    @DisplayName("the default admin menu offers the slots 27 to 35")
    void offersTheCompleteDefaultRow() {
        final List<Integer> slots = ModuleRow.slots(31, DEFAULT_SIZE, DEFAULT_RESERVED);

        assertEquals(List.of(27, 28, 29, 30, 31, 32, 33, 34, 35), slots);
        assertEquals(ModuleRow.CAPACITY, slots.size());
    }

    @Test
    @DisplayName("a tenth module gets no slot and nothing outside the row is touched")
    void neverGrowsBeyondOneRow() {
        final List<Integer> slots = ModuleRow.slots(31, DEFAULT_SIZE, DEFAULT_RESERVED);

        // Nine modules fit, a tenth one simply has no slot left.
        assertEquals(9, slots.size());
        assertFalse(slots.contains(26), "the row must not reach into the row above");
        assertFalse(slots.contains(36), "the row must not reach into the row below");
        for (final int reserved : DEFAULT_RESERVED) {
            assertFalse(slots.contains(reserved), "slot " + reserved + " belongs to another entry");
        }
        assertTrue(slots.stream().allMatch(slot -> slot < DEFAULT_SIZE), "no slot outside the inventory");
    }

    @Test
    @DisplayName("a slot of another entry is left out of the row")
    void skipsSlotsOfOtherEntries() {
        final List<Integer> slots = ModuleRow.slots(31, DEFAULT_SIZE, Set.of(29, 33, 40));

        assertEquals(List.of(27, 28, 30, 31, 32, 34, 35), slots);
    }

    @Test
    @DisplayName("a row that does not fit into the inventory is cut off")
    void staysInsideTheInventory() {
        // A four row menu has 36 slots, so the row 36 to 44 does not exist at all.
        assertEquals(List.of(), ModuleRow.slots(40, 36, Set.of()));
        assertEquals(List.of(27, 28, 29), ModuleRow.slots(28, 30, Set.of()));
    }
}
