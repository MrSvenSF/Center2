package net.managerhub.center.paper.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Owner of a Center2 menu inventory.
 *
 * <p>The holder is the only way a Center2 menu is recognized, and it also carries
 * which of the menus it is. The title is never used for that, because a title can
 * be changed in the menu file and is not a reliable marker.</p>
 *
 * <p>The detail view of a module additionally carries the id of the module it was
 * opened for, so a click does not have to guess which module is meant.</p>
 */
public final class CenterMenuHolder implements InventoryHolder {

    private final MenuType type;
    private final String moduleId;
    private final Inventory inventory;

    public CenterMenuHolder(final MenuType type, final int size, final Component title) {
        this(type, "", size, title);
    }

    public CenterMenuHolder(final MenuType type, final String moduleId, final int size, final Component title) {
        this.type = type;
        this.moduleId = moduleId;
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    /** @return which menu this inventory belongs to. */
    public MenuType type() {
        return type;
    }

    /** @return the module this menu was opened for, empty for every other menu. */
    public String moduleId() {
        return moduleId;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
