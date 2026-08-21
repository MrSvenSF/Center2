package net.managerhub.center.paper.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/**
 * Protects the Center2 menu against every kind of item movement.
 *
 * <p>The listener reacts only to inventories that belong to a
 * {@link CenterMenuHolder}, never to a title, and it cancels the whole event
 * before anything is moved. That covers normal clicks, shift clicks, number keys,
 * double clicks, dragging and every attempt to take an item out of the menu or to
 * put one in, so no item can be moved or duplicated.</p>
 */
public final class MenuListener implements Listener {

    private final CenterMenu menu;

    public MenuListener(final CenterMenu menu) {
        this.menu = menu;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!isCenterMenu(event.getInventory())) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        final Inventory clicked = event.getClickedInventory();
        if (clicked == null || !(clicked.getHolder() instanceof CenterMenuHolder holder)) {
            // A click inside the own inventory while the menu is open stays cancelled,
            // but it must not trigger a menu action.
            return;
        }
        menu.handleClick(player, holder, event.getSlot());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (isCenterMenu(event.getInventory())) {
            event.setCancelled(true);
        }
    }

    private boolean isCenterMenu(final Inventory inventory) {
        return inventory.getHolder() instanceof CenterMenuHolder;
    }
}
