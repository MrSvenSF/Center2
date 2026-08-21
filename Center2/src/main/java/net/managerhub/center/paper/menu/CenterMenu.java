package net.managerhub.center.paper.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.managerhub.center.Center;
import net.managerhub.center.common.language.Language;
import net.managerhub.center.common.language.MessageKey;
import net.managerhub.center.common.module.ModuleDescriptor;
import net.managerhub.center.common.module.ModuleLoader;
import net.managerhub.center.common.module.ModuleStatus;
import net.managerhub.center.common.module.VersionRange;
import net.managerhub.center.common.network.ServerStatus;
import net.managerhub.center.paper.config.CenterAdminMenuSettings;
import net.managerhub.center.paper.config.CenterConfiguration;
import net.managerhub.center.paper.config.CenterInfoMenuSettings;
import net.managerhub.center.paper.config.MenuItemSettings;
import net.managerhub.center.paper.config.MenuLayout;
import net.managerhub.center.paper.config.ServerStatusMenuSettings;
import net.managerhub.center.paper.module.ModuleService;
import net.managerhub.center.common.module.ModuleTexts;
import net.managerhub.center.paper.network.NetworkStatusClient;
import net.managerhub.center.paper.status.StatusService;
import net.managerhub.center.paper.text.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * The menus of Center2.
 *
 * <p>Every menu is built from the currently active configuration each time it is
 * opened, so a successful reload is visible immediately. The creator and the
 * organization entry are fixed: only their slot comes from the configuration.</p>
 *
 * <p>The admin button and everything behind it need the admin permission of
 * {@code Permissions.yml}. Without it the button is not placed at all, so it is
 * neither visible nor clickable. The module area additionally needs the module
 * permission, and the two buttons of the module detail view need the permission
 * that belongs to them.</p>
 *
 * <p>The installed modules are shown directly in the admin menu: the
 * {@code modules} entry of {@code Menus/CenterAdmin.yml} is the anchor of a whole
 * inventory row, see {@link ModuleRow}. A click on a module opens its detail
 * view, which has no menu file of its own - its size and slots are fixed in the
 * code, its texts come from the language file and its background follows
 * {@code Menus/CenterAdmin.yml}. A broken module only ever shows that it is
 * broken; the cause belongs into the server console.</p>
 */
public final class CenterMenu {

    /** Material of one Paper server entry in the server status menu. */
    private static final Material SERVER_MATERIAL = Material.PAPER;

    /** First slot the dynamic Paper server entries may use. */
    private static final int FIRST_SERVER_SLOT = 9;

    /** Size of the module detail view. */
    private static final int MODULE_DETAIL_SIZE = 27;

    private static final int MODULE_INFO_SLOT = 11;
    private static final int MODULE_ENABLE_SLOT = 13;
    private static final int MODULE_DISABLE_SLOT = 15;
    private static final int MODULE_BACK_SLOT = 22;

    private final Plugin plugin;
    private final Supplier<CenterConfiguration> configuration;
    private final StatusService status;
    private final CreatorHead creatorHead;
    private final NetworkStatusClient network;
    private final ModuleService modules;

    public CenterMenu(final Plugin plugin,
                      final Supplier<CenterConfiguration> configuration,
                      final StatusService status,
                      final CreatorHead creatorHead,
                      final NetworkStatusClient network,
                      final ModuleService modules) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.status = status;
        this.creatorHead = creatorHead;
        this.network = network;
        this.modules = modules;
    }

    /**
     * Opens the Center-Info menu for a player.
     *
     * @param player viewer of the menu
     */
    public void openCenterInfo(final Player player) {
        final CenterConfiguration snapshot = configuration.get();
        final Language language = snapshot.language();
        if (!snapshot.centerInfoMenuEnabled()) {
            player.sendMessage(Text.of(language.get(MessageKey.MENU_DISABLED, "product", Center.PRODUCT_NAME)));
            return;
        }
        player.openInventory(centerInfo(player, snapshot).getInventory());
    }

    /**
     * Opens the administrative menu for a player.
     *
     * @param player viewer of the menu
     */
    public void openAdmin(final Player player) {
        final CenterConfiguration snapshot = configuration.get();
        if (!snapshot.permissions().adminGate().allows(player)) {
            return;
        }
        player.openInventory(admin(snapshot, player).getInventory());
    }

    /**
     * Opens the network status menu and asks the proxy for a fresh picture.
     *
     * @param player viewer of the menu
     */
    public void openServerStatus(final Player player) {
        final CenterConfiguration snapshot = configuration.get();
        if (!snapshot.permissions().adminGate().allows(player)) {
            return;
        }
        network.requestUpdate(player);
        player.openInventory(serverStatus(snapshot).getInventory());
    }

    /**
     * Opens the detail view of one module.
     *
     * @param player   viewer of the menu
     * @param moduleId id of the module
     */
    public void openModule(final Player player, final String moduleId) {
        final CenterConfiguration snapshot = configuration.get();
        if (!snapshot.permissions().modulesGate().allows(player)) {
            return;
        }
        final Optional<ModuleLoader.InstalledModule> module = modules.module(moduleId);
        if (module.isEmpty()) {
            // The module disappeared between opening and clicking, so the admin
            // menu with its current module row is the only correct answer.
            openAdmin(player);
            return;
        }
        player.openInventory(moduleDetail(snapshot, player, module.get()).getInventory());
    }

    /** Fills every open network status menu with the answer that just arrived. */
    public void refreshServerStatusMenus() {
        final CenterConfiguration snapshot = configuration.get();
        for (final Player player : plugin.getServer().getOnlinePlayers()) {
            final Inventory open = player.getOpenInventory().getTopInventory();
            if (open.getHolder() instanceof CenterMenuHolder holder && holder.type() == MenuType.SERVER_STATUS) {
                fillServerStatus(open, snapshot);
            }
        }
    }

    /**
     * Handles a click inside a Center2 menu. The click itself is always cancelled
     * by {@link MenuListener}, this method only reacts to it.
     *
     * @param player clicking player
     * @param holder menu that was clicked
     * @param slot   clicked slot of the menu
     */
    public void handleClick(final Player player, final CenterMenuHolder holder, final int slot) {
        final CenterConfiguration snapshot = configuration.get();
        switch (holder.type()) {
            case CENTER_INFO -> handleCenterInfoClick(player, snapshot, slot);
            case CENTER_ADMIN -> handleAdminClick(player, snapshot, slot);
            case SERVER_STATUS -> handleServerStatusClick(player, snapshot, slot);
            case MODULE_DETAIL -> handleModuleDetailClick(player, snapshot, holder.moduleId(), slot);
        }
    }

    private void handleCenterInfoClick(final Player player, final CenterConfiguration snapshot, final int slot) {
        final CenterInfoMenuSettings settings = snapshot.centerInfoMenu();
        if (slot == settings.close().slot()) {
            later(player, Player::closeInventory);
            return;
        }
        if (slot == settings.organizationSlot()) {
            // The chat is hidden while the menu is open, so the link is only
            // useful once the menu is closed.
            later(player, Player::closeInventory);
            player.sendMessage(Text.of(snapshot.language()
                            .get(MessageKey.MENU_ORGANIZATION_OPEN, "website", Center.WEBSITE))
                    .clickEvent(ClickEvent.openUrl(Center.WEBSITE_URL)));
            return;
        }
        if (slot == settings.admin().slot() && snapshot.permissions().adminGate().allows(player)) {
            later(player, this::openAdmin);
        }
    }

    private void handleAdminClick(final Player player, final CenterConfiguration snapshot, final int slot) {
        if (!snapshot.permissions().adminGate().allows(player)) {
            return;
        }
        final CenterAdminMenuSettings settings = snapshot.centerAdminMenu();
        if (handleModuleRowClick(player, snapshot, slot)) {
            return;
        }
        if (slot == settings.back().slot()) {
            later(player, this::openCenterInfo);
            return;
        }
        if (slot == settings.serverStatus().slot()) {
            later(player, this::openServerStatus);
            return;
        }
        if (slot == settings.status().slot()) {
            later(player, Player::closeInventory);
            status.chatReport(snapshot.language()).forEach(player::sendMessage);
        }
    }

    private void handleServerStatusClick(final Player player, final CenterConfiguration snapshot, final int slot) {
        if (!snapshot.permissions().adminGate().allows(player)) {
            return;
        }
        if (slot == snapshot.serverStatusMenu().back().slot()) {
            later(player, this::openAdmin);
        }
    }

    /**
     * Handles a click on the module row of the admin menu.
     *
     * @return {@code true} if the click belonged to the module row
     */
    private boolean handleModuleRowClick(final Player player,
                                         final CenterConfiguration snapshot,
                                         final int slot) {
        final List<Integer> row = moduleSlots(snapshot);
        final int index = row.indexOf(slot);
        if (index < 0) {
            return false;
        }
        if (!snapshot.permissions().modulesGate().allows(player)) {
            // Without the permission the row is not even filled, so a click on it
            // is nothing but a click on the background.
            return true;
        }
        final List<ModuleLoader.InstalledModule> installed = modules.modules();
        if (index < installed.size()) {
            final String moduleId = installed.get(index).descriptor().id();
            later(player, viewer -> openModule(viewer, moduleId));
        }
        return true;
    }

    private void handleModuleDetailClick(final Player player,
                                         final CenterConfiguration snapshot,
                                         final String moduleId,
                                         final int slot) {
        if (!snapshot.permissions().modulesGate().allows(player)) {
            return;
        }
        if (slot == MODULE_BACK_SLOT) {
            later(player, this::openAdmin);
            return;
        }
        if (slot == MODULE_ENABLE_SLOT && snapshot.permissions().modulesEnableGate().allows(player)) {
            modules.enable(moduleId);
            later(player, viewer -> openModule(viewer, moduleId));
            return;
        }
        if (slot == MODULE_DISABLE_SLOT && snapshot.permissions().modulesDisableGate().allows(player)) {
            modules.disable(moduleId);
            later(player, viewer -> openModule(viewer, moduleId));
        }
    }

    /**
     * The slots of the admin menu that may hold a module.
     *
     * <p>The row never takes a slot another entry of the menu owns, so a module
     * can not overwrite the status, the server status or the back button.</p>
     */
    private static List<Integer> moduleSlots(final CenterConfiguration snapshot) {
        final CenterAdminMenuSettings settings = snapshot.centerAdminMenu();
        return ModuleRow.slots(settings.modules().slot(), settings.layout().size(),
                Set.of(settings.status().slot(), settings.serverStatus().slot(), settings.back().slot()));
    }

    private CenterMenuHolder centerInfo(final Player player, final CenterConfiguration snapshot) {
        final Language language = snapshot.language();
        final CenterInfoMenuSettings settings = snapshot.centerInfoMenu();
        final CenterMenuHolder holder = holder(MenuType.CENTER_INFO, settings.layout());
        final Inventory inventory = holder.getInventory();
        fill(inventory, settings.layout());

        inventory.setItem(settings.creatorSlot(), creatorItem(language));
        inventory.setItem(settings.organizationSlot(), organizationItem(language));
        inventory.setItem(settings.close().slot(), item(settings.close().material(),
                language.get(MessageKey.MENU_CLOSE_NAME),
                List.of(language.get(MessageKey.MENU_CLOSE_LORE))));

        // Without the admin permission the button is not placed at all, so the
        // background of the menu stays where it would have been.
        if (snapshot.permissions().adminGate().allows(player)) {
            inventory.setItem(settings.admin().slot(), item(settings.admin().material(),
                    settings.adminName(), List.of(language.get(MessageKey.MENU_ADMIN_LORE))));
        }
        return holder;
    }

    private CenterMenuHolder admin(final CenterConfiguration snapshot, final Player viewer) {
        final Language language = snapshot.language();
        final CenterAdminMenuSettings settings = snapshot.centerAdminMenu();
        final CenterMenuHolder holder = holder(MenuType.CENTER_ADMIN, settings.layout());
        final Inventory inventory = holder.getInventory();
        fill(inventory, settings.layout());

        inventory.setItem(settings.status().slot(), lored(settings.status().material(),
                language.get(MessageKey.MENU_ADMIN_STATUS_NAME), status.menuLore(language)));
        inventory.setItem(settings.serverStatus().slot(), item(settings.serverStatus().material(),
                language.get(MessageKey.MENU_ADMIN_SERVER_STATUS_NAME),
                List.of(language.get(MessageKey.MENU_ADMIN_SERVER_STATUS_LORE,
                        "product", Center.PRODUCT_NAME))));

        // Without the module permission the row is not filled at all, so a normal
        // administrator never sees the module administration.
        if (snapshot.permissions().modulesGate().allows(viewer)) {
            fillModuleRow(inventory, snapshot);
        }
        inventory.setItem(settings.back().slot(), backItem(settings.back(), language));
        return holder;
    }

    /**
     * Places the installed modules directly into the admin menu.
     *
     * <p>The row holds up to {@link ModuleRow#CAPACITY} modules. If more are
     * installed, the remaining ones are only left out of this row - they stay
     * installed, keep their state and are still listed by the module command.
     * There is deliberately no pagination yet.</p>
     */
    private void fillModuleRow(final Inventory inventory, final CenterConfiguration snapshot) {
        final Language language = snapshot.language();
        final List<Integer> row = moduleSlots(snapshot);
        final List<ModuleLoader.InstalledModule> installed = modules.modules();

        if (installed.isEmpty()) {
            final CenterAdminMenuSettings settings = snapshot.centerAdminMenu();
            inventory.setItem(settings.modules().slot(), item(settings.modules().material(),
                    language.get(MessageKey.MENU_MODULES_EMPTY_NAME),
                    List.of(language.get(MessageKey.MENU_MODULES_EMPTY_LORE))));
            return;
        }
        for (int index = 0; index < row.size() && index < installed.size(); index++) {
            inventory.setItem(row.get(index), moduleItem(language, installed.get(index)));
        }
        if (installed.size() > row.size()) {
            plugin.getLogger().info(language.get(MessageKey.MENU_MODULES_TOO_MANY,
                    "shown", Integer.toString(row.size()),
                    "installed", Integer.toString(installed.size())));
        }
    }

    /** Builds the detail view of one module. */
    private CenterMenuHolder moduleDetail(final CenterConfiguration snapshot,
                                          final Player viewer,
                                          final ModuleLoader.InstalledModule module) {
        final Language language = snapshot.language();
        final CenterAdminMenuSettings settings = snapshot.centerAdminMenu();
        final ModuleDescriptor descriptor = module.descriptor();
        final CenterMenuHolder holder = new CenterMenuHolder(MenuType.MODULE_DETAIL, descriptor.id(),
                MODULE_DETAIL_SIZE, Text.of(language.get(MessageKey.MENU_MODULE_TITLE,
                        "module", Text.escape(descriptor.name()))));
        final Inventory inventory = holder.getInventory();
        fill(inventory, settings.layout(), MODULE_DETAIL_SIZE);

        final List<String> lore = new ArrayList<>();
        lore.add(language.get(MessageKey.MENU_MODULE_INFO_ID, "id", Text.escape(descriptor.id())));
        lore.add(language.get(MessageKey.MENU_MODULE_INFO_VERSION,
                "version", Text.escape(descriptor.version())));
        lore.add(language.get(MessageKey.MENU_MODULE_INFO_AUTHOR, "author", Text.escape(descriptor.author())));
        lore.add(language.get(MessageKey.MENU_MODULE_INFO_STATUS,
                "status", ModuleTexts.status(language, module.status())));
        ModuleTexts.reason(language, module.status()).ifPresent(lore::add);
        // One jar serves Paper and Velocity, so an administrator has to see what
        // a module was actually built for.
        lore.add(language.get(MessageKey.MENU_MODULE_INFO_PLATFORM,
                "platform", ModuleTexts.platform(language, descriptor.platform())));
        lore.add(language.get(MessageKey.MENU_MODULE_INFO_CENTER,
                "product", Center.PRODUCT_NAME, "range", descriptor.centerVersions().display()));
        descriptor.minecraftVersions().map(VersionRange::display).ifPresent(range ->
                lore.add(language.get(MessageKey.MENU_MODULE_INFO_MINECRAFT, "range", range)));

        inventory.setItem(MODULE_INFO_SLOT, item(Material.BOOK,
                language.get(MessageKey.MENU_MODULE_INFO_NAME, "module", Text.escape(descriptor.name())), lore));

        if (snapshot.permissions().modulesEnableGate().allows(viewer)) {
            inventory.setItem(MODULE_ENABLE_SLOT, item(Material.LIME_DYE,
                    language.get(MessageKey.MENU_MODULE_ENABLE_NAME),
                    List.of(language.get(MessageKey.MENU_MODULE_ENABLE_LORE))));
        }
        if (snapshot.permissions().modulesDisableGate().allows(viewer)) {
            inventory.setItem(MODULE_DISABLE_SLOT, item(Material.RED_DYE,
                    language.get(MessageKey.MENU_MODULE_DISABLE_NAME),
                    List.of(language.get(MessageKey.MENU_MODULE_DISABLE_LORE))));
        }
        inventory.setItem(MODULE_BACK_SLOT, backItem(settings.back(), language));
        return holder;
    }

    /** Builds one entry of the module list. */
    private ItemStack moduleItem(final Language language, final ModuleLoader.InstalledModule module) {
        final List<String> lore = new ArrayList<>();
        lore.add(language.get(MessageKey.MENU_MODULES_ENTRY_VERSION,
                "version", Text.escape(module.descriptor().version())));
        lore.add(language.get(MessageKey.MENU_MODULES_ENTRY_STATUS,
                "status", ModuleTexts.status(language, module.status())));
        // A broken module only ever says that it is broken. The cause belongs
        // into the server console and never into a menu.
        ModuleTexts.reason(language, module.status()).ifPresent(lore::add);
        lore.add(language.get(MessageKey.MENU_MODULES_ENTRY_OPEN));

        return item(statusMaterial(module.status()),
                language.get(MessageKey.MENU_MODULES_ENTRY_NAME,
                        "module", Text.escape(module.descriptor().name())),
                lore);
    }

    private static Material statusMaterial(final ModuleStatus status) {
        return switch (status) {
            case ENABLED -> Material.LIME_DYE;
            case DISABLED -> Material.GRAY_DYE;
            case INCOMPATIBLE_CENTER, INCOMPATIBLE_MINECRAFT -> Material.ORANGE_DYE;
            case ERROR -> Material.RED_DYE;
        };
    }

    private CenterMenuHolder serverStatus(final CenterConfiguration snapshot) {
        final CenterMenuHolder holder = holder(MenuType.SERVER_STATUS, snapshot.serverStatusMenu().layout());
        fillServerStatus(holder.getInventory(), snapshot);
        return holder;
    }

    private void fillServerStatus(final Inventory inventory, final CenterConfiguration snapshot) {
        final Language language = snapshot.language();
        final ServerStatusMenuSettings settings = snapshot.serverStatusMenu();
        fill(inventory, settings.layout());

        // The proxy counts as verified as soon as it has answered at least once.
        final ServerStatus proxyState = network.proxyAnswered() ? ServerStatus.CONNECTED : ServerStatus.UNKNOWN;
        inventory.setItem(settings.velocity().slot(), item(settings.velocity().material(),
                language.get(MessageKey.MENU_SERVER_STATUS_VELOCITY_NAME),
                List.of(stateLine(language, proxyState))));
        inventory.setItem(settings.back().slot(), backItem(settings.back(), language));

        int slot = FIRST_SERVER_SLOT;
        for (final Map.Entry<String, ServerStatus> server : network.servers().entrySet()) {
            slot = nextFreeSlot(slot, settings);
            if (slot >= settings.layout().size()) {
                break;
            }
            inventory.setItem(slot, item(SERVER_MATERIAL,
                    language.get(MessageKey.MENU_SERVER_STATUS_SERVER_NAME, "server", Text.escape(server.getKey())),
                    List.of(stateLine(language, server.getValue()))));
            slot++;
        }
    }

    private static int nextFreeSlot(final int from, final ServerStatusMenuSettings settings) {
        int slot = from;
        while (slot == settings.velocity().slot() || slot == settings.back().slot()) {
            slot++;
        }
        return slot;
    }

    private static String stateLine(final Language language, final ServerStatus state) {
        final MessageKey key = switch (state) {
            case CONNECTED -> MessageKey.MENU_SERVER_STATUS_CONNECTED;
            case UNREACHABLE -> MessageKey.MENU_SERVER_STATUS_UNREACHABLE;
            case UNKNOWN -> MessageKey.MENU_SERVER_STATUS_UNKNOWN;
        };
        return language.get(MessageKey.MENU_SERVER_STATUS_STATE_LINE, "state", language.get(key));
    }

    private ItemStack backItem(final MenuItemSettings settings, final Language language) {
        return item(settings.material(), language.get(MessageKey.MENU_BACK_NAME),
                List.of(language.get(MessageKey.MENU_BACK_LORE)));
    }

    private ItemStack creatorItem(final Language language) {
        final ItemStack head = creatorHead.createHead();
        head.editMeta(meta -> {
            meta.displayName(Text.item(language.get(MessageKey.MENU_CREATOR_NAME)));
            meta.lore(List.of(
                    Text.lore(language.get(MessageKey.MENU_CREATOR_PLAYER, "creator", Center.CREATOR)),
                    Text.lore(language.get(MessageKey.MENU_CREATOR_DESCRIPTION, "product", Center.PRODUCT_NAME))));
        });
        return head;
    }

    private ItemStack organizationItem(final Language language) {
        return item(Material.NETHER_STAR,
                language.get(MessageKey.MENU_ORGANIZATION_NAME, "organization", Center.ORGANIZATION),
                List.of(
                        language.get(MessageKey.MENU_ORGANIZATION_DESCRIPTION_1,
                                "product", Center.PRODUCT_NAME, "organization", Center.ORGANIZATION),
                        language.get(MessageKey.MENU_ORGANIZATION_DESCRIPTION_2, "website", Center.WEBSITE)));
    }

    private CenterMenuHolder holder(final MenuType type, final MenuLayout layout) {
        return new CenterMenuHolder(type, layout.size(), Text.of(layout.title()));
    }

    private void fill(final Inventory inventory, final MenuLayout layout) {
        fill(inventory, layout, layout.size());
    }

    /**
     * Fills a menu whose size is fixed in the code and not in a menu file. The
     * background still follows the configuration of the admin menu, so the module
     * menus look like the menu they were opened from.
     */
    private void fill(final Inventory inventory, final MenuLayout layout, final int size) {
        inventory.clear();
        if (!layout.fillerEnabled()) {
            return;
        }
        final ItemStack filler = item(layout.fillerMaterial(), null, List.of());
        for (int slot = 0; slot < size; slot++) {
            inventory.setItem(slot, filler.clone());
        }
    }

    private ItemStack item(final Material material, final String name, final List<String> lore) {
        final List<Component> lines = new ArrayList<>(lore.size());
        lore.forEach(line -> lines.add(Text.lore(line)));
        return lored(material, name, lines);
    }

    private ItemStack lored(final Material material, final String name, final List<Component> lore) {
        final ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(name == null ? Component.empty() : Text.item(name));
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
        });
        return item;
    }

    private void later(final Player player, final Consumer<Player> action) {
        plugin.getServer().getScheduler().runTask(plugin, () -> action.accept(player));
    }
}
