package net.managerhub.center.common.language;

/**
 * Every text of Center2 that is shown to a user or to an administrator.
 *
 * <p>The enum is the single source of truth for the language files: a language
 * file must contain exactly these keys, no more and no less. Messages that are
 * sent into the chat use Adventure MiniMessage, messages that are written into a
 * log are plain text.</p>
 *
 * <p>Startup and configuration error messages are intentionally not part of this
 * enum. They report why a configuration file - including the language file
 * itself - could not be read, so they must not depend on a loaded language.
 * Everything that happens after a configuration was loaded successfully belongs
 * here.</p>
 */
public enum MessageKey {

    /** Log line when the plugin is ready on Paper. Placeholders: product, version, platform, commands. */
    PLUGIN_ENABLED("plugin.enabled"),
    /** Log line when the plugin is ready on the proxy. Placeholders: product, version, platform. */
    PLUGIN_ENABLED_PROXY("plugin.enabled-proxy"),
    /** Log line when the plugin shuts down. Placeholders: product, version, platform. */
    PLUGIN_DISABLED("plugin.disabled"),

    /** Chat answer of a successful reload. Placeholders: product, commands. */
    RELOAD_SUCCESS("reload.success"),
    /** Chat answer of a failed reload. Placeholders: reason. */
    RELOAD_FAILED("reload.failed"),
    /** Chat note that the previous configuration is still running. */
    RELOAD_PREVIOUS_ACTIVE("reload.previous-active"),
    /** Log line of a successful reload. Placeholders: product, commands. */
    RELOAD_LOG_SUCCESS("reload.log-success"),
    /** Log line of a failed reload. Placeholders: product, reason. */
    RELOAD_LOG_FAILED("reload.log-failed"),

    /** Chat answer while the network reload travels through the remote database. */
    RELOAD_NETWORK_TRANSPORT_REMOTE("reload.network.transport-remote"),
    /** Chat answer while the network reload travels through the proxy. */
    RELOAD_NETWORK_TRANSPORT_MESSAGING("reload.network.transport-messaging"),
    /** Chat note that the remote database was not available and the proxy is used instead. */
    RELOAD_NETWORK_REMOTE_DOWN("reload.network.remote-down"),
    /** Chat answer when nothing can reach the network right now. Placeholders: product. */
    RELOAD_NETWORK_NO_TRANSPORT("reload.network.no-transport"),
    /** First line of the network reload result. Placeholders: product. */
    RELOAD_NETWORK_HEADER("reload.network.header"),
    /** One line of the network reload result. Placeholders: node, status. */
    RELOAD_NETWORK_ENTRY("reload.network.entry"),
    /** Chat answer when the network reload itself failed. Placeholders: reason. */
    RELOAD_NETWORK_FAILED("reload.network.failed"),
    /** State of a node that reloaded. */
    RELOAD_NETWORK_STATUS_SUCCESS("reload.network.status.success"),
    /** State of a node whose reload failed. */
    RELOAD_NETWORK_STATUS_FAILED("reload.network.status.failed"),
    /** State of a node that has not answered yet. */
    RELOAD_NETWORK_STATUS_PENDING("reload.network.status.pending"),
    /** State of a node nothing can reach right now. */
    RELOAD_NETWORK_STATUS_UNREACHABLE("reload.network.status.unreachable"),
    /** State of a node that saw the request too late. */
    RELOAD_NETWORK_STATUS_EXPIRED("reload.network.status.expired"),
    /** Log line when a reload arrived from the network. Placeholders: origin. */
    RELOAD_NETWORK_RECEIVED("reload.network.received"),
    /** Log line with what every node answered. Placeholders: product, result. */
    RELOAD_NETWORK_LOG_RESULT("reload.network.log-result"),

    /** Log line when the remote system was switched off. Placeholders: product, file. */
    REMOTE_DISABLED("remote.disabled"),
    /** Log line for a remote configuration that cannot be used. Placeholders: product, reason. */
    REMOTE_INVALID("remote.invalid"),
    /** Log line when the remote database is connected. Placeholders: database, server. */
    REMOTE_CONNECTED("remote.connected"),
    /** Log line for an unreachable remote database. Placeholders: product, database, reason, seconds, attempt. */
    REMOTE_UNREACHABLE("remote.unreachable"),
    /** Log line when two nodes use the same server-id. Placeholders: product, server, file. */
    REMOTE_DUPLICATE_ID("remote.duplicate-id"),
    /** Log line after expired rows were removed. Placeholders: rows. */
    REMOTE_PURGED("remote.purged"),
    /** Log line when a background task of the remote system failed. Placeholders: product, task. */
    REMOTE_TASK_FAILED("remote.task-failed"),
    /** Log line for an action that was not carried out. Placeholders: action, namespace, reason. */
    REMOTE_ACTION_REJECTED("remote.action-rejected"),
    /** Log line for an action that failed. Placeholders: action, namespace. */
    REMOTE_ACTION_FAILED("remote.action-failed"),

    /** Chat answer for a missing permission. */
    COMMAND_NO_PERMISSION("command.no-permission"),
    /** Chat answer for an unknown command path. Placeholders: path. */
    COMMAND_UNKNOWN("command.unknown"),
    /** First line of the command overview. Placeholders: product, version. */
    COMMAND_OVERVIEW_HEADER("command.overview-header"),
    /** One line of the command overview. Placeholders: path, description. */
    COMMAND_OVERVIEW_ENTRY("command.overview-entry"),
    /** Command overview without a single usable command. */
    COMMAND_OVERVIEW_EMPTY("command.overview-empty"),
    /** Description of the Center-Info command. Placeholders: product. */
    COMMAND_DESCRIPTION_CENTER_INFO("command.description.center-info"),
    /** Description of the reload system command. Placeholders: product. */
    COMMAND_DESCRIPTION_RELOAD("command.description.reload"),
    /** Description of the module overview command. */
    COMMAND_DESCRIPTION_MODULES("command.description.modules"),
    /** Description of the module reload command. */
    COMMAND_DESCRIPTION_MODULES_RELOAD("command.description.modules-reload"),
    /** Description of the module enable command. */
    COMMAND_DESCRIPTION_MODULES_ENABLE("command.description.modules-enable"),
    /** Description of the module disable command. */
    COMMAND_DESCRIPTION_MODULES_DISABLE("command.description.modules-disable"),

    /** Chat answer when a menu is switched off. Placeholders: product. */
    MENU_DISABLED("menu.disabled"),
    /** Item name of the back button. */
    MENU_BACK_NAME("menu.back.name"),
    /** Lore of the back button. */
    MENU_BACK_LORE("menu.back.lore"),

    /** Item name of the fixed creator entry. */
    MENU_CREATOR_NAME("menu.center-info.creator.name"),
    /** Creator name in the lore of the creator entry. Placeholders: creator. */
    MENU_CREATOR_PLAYER("menu.center-info.creator.player"),
    /** Description in the lore of the creator entry. Placeholders: product. */
    MENU_CREATOR_DESCRIPTION("menu.center-info.creator.description"),
    /** Item name of the fixed organization entry. Placeholders: organization. */
    MENU_ORGANIZATION_NAME("menu.center-info.organization.name"),
    /** First description line of the organization entry. Placeholders: product, organization. */
    MENU_ORGANIZATION_DESCRIPTION_1("menu.center-info.organization.description-1"),
    /** Second description line of the organization entry. Placeholders: website. */
    MENU_ORGANIZATION_DESCRIPTION_2("menu.center-info.organization.description-2"),
    /** Clickable chat message that opens the website. Placeholders: website. */
    MENU_ORGANIZATION_OPEN("menu.center-info.organization.open"),
    /** Lore of the admin button. */
    MENU_ADMIN_LORE("menu.center-info.admin.lore"),
    /** Item name of the close button. */
    MENU_CLOSE_NAME("menu.center-info.close.name"),
    /** Lore of the close button. */
    MENU_CLOSE_LORE("menu.center-info.close.lore"),

    /** Item name of the status entry in the admin menu. */
    MENU_ADMIN_STATUS_NAME("menu.center-admin.status.name"),
    /** Item name of the server status entry in the admin menu. */
    MENU_ADMIN_SERVER_STATUS_NAME("menu.center-admin.server-status.name"),
    /** Lore of the server status entry in the admin menu. Placeholders: product. */
    MENU_ADMIN_SERVER_STATUS_LORE("menu.center-admin.server-status.lore"),
    /** Item name of one module in the list. Placeholders: module. */
    MENU_MODULES_ENTRY_NAME("menu.modules.entry.name"),
    /** Version line of one module in the list. Placeholders: version. */
    MENU_MODULES_ENTRY_VERSION("menu.modules.entry.version"),
    /** State line of one module in the list. Placeholders: status. */
    MENU_MODULES_ENTRY_STATUS("menu.modules.entry.status"),
    /** Hint line of one module in the list. */
    MENU_MODULES_ENTRY_OPEN("menu.modules.entry.open"),
    /** Item name shown when no module is installed. */
    MENU_MODULES_EMPTY_NAME("menu.modules.empty.name"),
    /** Lore shown when no module is installed. */
    MENU_MODULES_EMPTY_LORE("menu.modules.empty.lore"),
    /** Log line when more modules are installed than the row can show. Placeholders: shown, installed. */
    MENU_MODULES_TOO_MANY("menu.modules.too-many"),

    /** Title of the module detail menu. Placeholders: module. */
    MENU_MODULE_TITLE("menu.module.title"),
    /** Item name of the module detail entry. Placeholders: module. */
    MENU_MODULE_INFO_NAME("menu.module.info.name"),
    /** Id line of the module detail entry. Placeholders: id. */
    MENU_MODULE_INFO_ID("menu.module.info.id"),
    /** Version line of the module detail entry. Placeholders: version. */
    MENU_MODULE_INFO_VERSION("menu.module.info.version"),
    /** Author line of the module detail entry. Placeholders: author. */
    MENU_MODULE_INFO_AUTHOR("menu.module.info.author"),
    /** State line of the module detail entry. Placeholders: status. */
    MENU_MODULE_INFO_STATUS("menu.module.info.status"),
    /** Platform line of the module detail entry. Placeholders: platform. */
    MENU_MODULE_INFO_PLATFORM("menu.module.info.platform"),
    /** Supported Center2 versions of the module. Placeholders: product, range. */
    MENU_MODULE_INFO_CENTER("menu.module.info.center"),
    /** Supported Minecraft versions of the module. Placeholders: range. */
    MENU_MODULE_INFO_MINECRAFT("menu.module.info.minecraft"),
    /** Item name of the enable button. */
    MENU_MODULE_ENABLE_NAME("menu.module.enable.name"),
    /** Lore of the enable button. */
    MENU_MODULE_ENABLE_LORE("menu.module.enable.lore"),
    /** Item name of the disable button. */
    MENU_MODULE_DISABLE_NAME("menu.module.disable.name"),
    /** Lore of the disable button. */
    MENU_MODULE_DISABLE_LORE("menu.module.disable.lore"),

    /** Item name of the proxy entry. */
    MENU_SERVER_STATUS_VELOCITY_NAME("menu.server-status.velocity.name"),
    /** Item name of one Paper server entry. Placeholders: server. */
    MENU_SERVER_STATUS_SERVER_NAME("menu.server-status.server.name"),
    /** Lore line that carries the state. Placeholders: state. */
    MENU_SERVER_STATUS_STATE_LINE("menu.server-status.state.line"),
    /** State of a verified and reachable instance. */
    MENU_SERVER_STATUS_CONNECTED("menu.server-status.state.connected"),
    /** State of an instance that does not answer. */
    MENU_SERVER_STATUS_UNREACHABLE("menu.server-status.state.unreachable"),
    /** State of an instance that was not verified yet. */
    MENU_SERVER_STATUS_UNKNOWN("menu.server-status.state.unknown"),

    /** First line of the status report. Placeholders: product. */
    STATUS_HEADER("status.header"),
    /** One chat line of the status report. Placeholders: label, value. */
    STATUS_LINE("status.line"),
    /** One lore line of the status report. Placeholders: label, value. */
    STATUS_LORE_LINE("status.lore-line"),
    /** Label of the version line. */
    STATUS_LABEL_VERSION("status.label.version"),
    /** Label of the platform line. */
    STATUS_LABEL_PLATFORM("status.label.platform"),
    /** Label of the Minecraft version line. */
    STATUS_LABEL_MINECRAFT("status.label.minecraft"),
    /** Label of the uptime line. */
    STATUS_LABEL_UPTIME("status.label.uptime"),

    /** Log line with the number of installed and running modules. Placeholders: installed, enabled. */
    MODULE_LOADED("module.loaded"),
    /** Log line for a jar that is no usable module. Placeholders: module, reason. */
    MODULE_SKIPPED("module.skipped"),
    /** Log line when a command path of a module is already used. Placeholders: module, path. */
    MODULE_COMMAND_CONFLICT("module.command-conflict"),
    /** Log line of a module failure. Placeholders: module, id, version, step, reason. */
    MODULE_ERROR("module.error"),
    /** Log line for an unsupported Center2 version. Placeholders: module, id, version, required, running. */
    MODULE_INCOMPATIBLE_CENTER("module.incompatible-center"),
    /** Log line for an unsupported Minecraft version. Placeholders: module, id, version, required, running. */
    MODULE_INCOMPATIBLE_MINECRAFT("module.incompatible-minecraft"),
    /** Log line for a module an administrator switched off. Placeholders: module, id. */
    MODULE_ADMIN_DISABLED("module.admin-disabled"),
    /** Log line with the number of modules that applied a reload. Placeholders: modules. */
    MODULE_RELOADED("module.reloaded"),
    /** Log line when the jar of a loaded module was replaced. Placeholders: module, id, file. */
    MODULE_JAR_CHANGED("module.jar-changed"),
    /** Log line with the Minecraft version the module system compares against. Placeholders: product, version. */
    MODULE_ENVIRONMENT("module.environment"),
    /** Log line when the Minecraft version could not be read. Placeholders: product, version. */
    MODULE_ENVIRONMENT_UNKNOWN("module.environment-unknown"),
    /** Log line when the module folder could not be read. Placeholders: directory, reason. */
    MODULE_SCAN_FAILED("module.scan-failed"),
    /** Log line when the stored module state could not be read. Placeholders: reason. */
    MODULE_STATE_UNREADABLE("module.state-unreadable"),
    /** Log line when switching a module off could not be stored. Placeholders: module, id, reason. */
    MODULE_STATE_DISABLE_NOT_STORED("module.state-disable-not-stored"),
    /** Log line when switching a module on could not be stored. Placeholders: module, id, reason. */
    MODULE_STATE_ENABLE_NOT_STORED("module.state-enable-not-stored"),

    /** Visible name of a module for the Paper platform. */
    MODULE_PLATFORM_PAPER("module.platform.paper"),
    /** Visible name of a module for the Velocity platform. */
    MODULE_PLATFORM_VELOCITY("module.platform.velocity"),
    /** Visible name of a module for both platforms. */
    MODULE_PLATFORM_BOTH("module.platform.both"),

    /** Visible state of a running module. */
    MODULE_STATUS_ENABLED("module.status.enabled"),
    /** Visible state of a module that is switched off. */
    MODULE_STATUS_DISABLED("module.status.disabled"),
    /** Visible state of a module that does not fit this server. */
    MODULE_STATUS_INCOMPATIBLE("module.status.incompatible"),
    /** Visible state of a module that failed. Never carries a technical detail. */
    MODULE_STATUS_ERROR("module.status.error"),
    /** Short reason for an unsupported Center2 version. Placeholders: product. */
    MODULE_REASON_CENTER("module.reason.center-version"),
    /** Short reason for an unsupported Minecraft version. */
    MODULE_REASON_MINECRAFT("module.reason.minecraft-version"),

    /** First line of the module overview. Placeholders: product. */
    MODULES_LIST_HEADER("modules.list-header"),
    /** One line of the module overview. Placeholders: module, id, version, status. */
    MODULES_LIST_ENTRY("modules.list-entry"),
    /** Module overview without a single installed module. */
    MODULES_LIST_EMPTY("modules.list-empty"),
    /** Chat answer when the module id is missing. Placeholders: path. */
    MODULES_USAGE("modules.usage"),
    /** Chat answer for an unknown module id. Placeholders: module. */
    MODULES_UNKNOWN("modules.unknown"),
    /** Chat answer after a module was started. Placeholders: module. */
    MODULES_ENABLED("modules.enabled"),
    /** Chat answer after a module was stopped. Placeholders: module. */
    MODULES_DISABLED("modules.disabled"),
    /** Chat answer when the module is already running. Placeholders: module. */
    MODULES_ALREADY_ENABLED("modules.already-enabled"),
    /** Chat answer when the module is already switched off. Placeholders: module. */
    MODULES_ALREADY_DISABLED("modules.already-disabled"),
    /** Chat answer when a module cannot run on this server. Placeholders: module, reason. */
    MODULES_INCOMPATIBLE("modules.incompatible"),
    /** Chat answer when a module failed. Points at the console, never at a stack trace. Placeholders: module. */
    MODULES_ERROR("modules.error"),
    /** Chat answer when the module system could not be started at all. Placeholders: product. */
    MODULES_UNAVAILABLE("modules.unavailable"),
    /** Chat answer after the module folder was read again. Placeholders: installed, enabled. */
    MODULES_RELOADED("modules.reloaded"),

    /** Log line when another plugin already uses a command name. Placeholders: command, namespace. */
    REGISTRY_NAME_TAKEN("registry.name-taken"),
    /** Log line when one old command name survived. Placeholders: command. */
    REGISTRY_REMOVE_FAILED("registry.remove-failed"),
    /** Log line when old command names survived. Placeholders: commands. */
    REGISTRY_REMOVE_LEFTOVERS("registry.remove-leftovers"),


    /** Log line after the command was switched off automatically. Placeholders: command, menu, file. */
    COMMANDS_FILE_DISABLED("commands-file.disabled"),
    /** Log line when the automatic switch off could not be written. Placeholders: command, file, reason. */
    COMMANDS_FILE_DISABLE_FAILED("commands-file.disable-failed");

    private final String path;

    MessageKey(final String path) {
        this.path = path;
    }

    /** @return the path of this message inside a language file. */
    public String path() {
        return path;
    }
}
