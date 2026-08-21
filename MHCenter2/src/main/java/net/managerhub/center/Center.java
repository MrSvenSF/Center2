package net.managerhub.center;

/**
 * Fixed product constants of MHCenter2.
 *
 * <p>None of these values is configurable. They are shared by the Paper and the
 * Velocity side of the plugin.</p>
 */
public final class Center {

    /** Visible product name. Always "MHCenter2". */
    public static final String PRODUCT_NAME = "MHCenter2";

    /** Product version. */
    public static final String VERSION = "1.0.0";

    /** Organization behind MHCenter2. */
    public static final String ORGANIZATION = "Manager Hub";

    /** Visible website of the organization. */
    public static final String WEBSITE = "ManagerHub.net";

    /** Address the website link of the organization points to. */
    public static final String WEBSITE_URL = "https://managerhub.net";

    /** Minecraft name of the creator. */
    public static final String CREATOR = "MrSvenSF";

    /** File name of the main configuration. PascalCase by convention. */
    public static final String MAIN_CONFIG_FILE = "MainConfig.yml";

    /** File name of the command configuration. PascalCase by convention. */
    public static final String COMMANDS_FILE = "Commands.yml";

    /** File name of the permission configuration. PascalCase by convention. */
    public static final String PERMISSIONS_FILE = "Permissions.yml";

    /** Directory of the language files. PascalCase by convention. */
    public static final String LANGUAGE_DIRECTORY = "Language";

    /** Directory of the menu files. PascalCase by convention. */
    public static final String MENUS_DIRECTORY = "Menus";

    /** File name of the Center-Info menu. PascalCase by convention. */
    public static final String CENTER_INFO_MENU_FILE = "CenterInfo.yml";

    /** File name of the Center-Admin menu. PascalCase by convention. */
    public static final String CENTER_ADMIN_MENU_FILE = "CenterAdmin.yml";

    /** File name of the server status menu. PascalCase by convention. */
    public static final String SERVER_STATUS_MENU_FILE = "CenterServerStatus.yml";

    /** Directory of the module system. PascalCase by convention. */
    public static final String MODULES_DIRECTORY = "Modules";

    /** Directory below {@link #MODULES_DIRECTORY} that holds the module jars. */
    public static final String MODULE_JARS_DIRECTORY = "Jars";

    /** Directory below {@link #MODULES_DIRECTORY} that holds the data of the modules. */
    public static final String MODULE_CONFIGS_DIRECTORY = "Configs";

    /** Name of the metadata file every module jar has to carry. */
    public static final String MODULE_DESCRIPTOR_FILE = "center-module.properties";

    /** Directory of the local database. PascalCase by convention. */
    public static final String DATABASE_DIRECTORY = "DB";

    /** File name of the local SQLite database. PascalCase by convention. */
    public static final String DATABASE_FILE = "Center.db";

    /** Configuration version every MHCenter2 YAML file must declare. */
    public static final int CONFIG_VERSION = 3;

    /** Database schema version stored in {@code center_meta}. */
    public static final int SCHEMA_VERSION = 2;

    /** Schema version of the optional remote database, stored in {@code center_remote_meta}. */
    public static final int REMOTE_SCHEMA_VERSION = 1;

    /** Namespace every action of the core itself carries in the remote database. */
    public static final String CORE_NAMESPACE = "center";

    private Center() {
        throw new AssertionError("No instances.");
    }
}
