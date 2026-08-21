package net.managerhub.center.testmodule;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import net.managerhub.center.api.CenterModule;
import net.managerhub.center.api.ModuleContext;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * The MHCenter2 test module.
 *
 * <p>It exists to prove that an external jar below {@code Modules/Jars} is
 * found, read, loaded, started and stopped again, that it gets its own folder
 * below {@code Modules/Configs} and that it can bring along its own command.
 * It does nothing else.</p>
 *
 * <p>The module is {@code platform=PAPER}, so it may use the Paper API - here the
 * YAML reader of Bukkit. Everything it gets from MHCenter2 is the platform neutral
 * {@link ModuleContext}.</p>
 */
public final class TestModule implements CenterModule {

    private static final String MAIN_CONFIG = "MainConfig.yml";
    private static final String COMMANDS = "Commands.yml";

    /** What the command answers when the configuration says nothing. */
    private static final String DEFAULT_GREETING = "MHCenter2 TestModule funktioniert.";

    private ModuleContext context;

    /**
     * The answer of the command, read from the own configuration.
     *
     * <p>It is a field on purpose: the command reads it every time it runs, so a
     * changed value really shows up after {@code /center reload} without the
     * command being registered again.</p>
     */
    private volatile String greeting = DEFAULT_GREETING;

    @Override
    public void onLoad(final ModuleContext context) throws IOException {
        this.context = context;
        Files.createDirectories(context.configDirectory());
        installDefault(MAIN_CONFIG);
        installDefault(COMMANDS);
        context.logger().info("geladen.");
    }

    @Override
    public void onEnable() {
        final YamlConfiguration main = config(MAIN_CONFIG);
        if (!main.getBoolean("enabled", false)) {
            context.logger().info("ist in " + MAIN_CONFIG + " ausgeschaltet.");
            return;
        }
        greeting = main.getString("greeting", DEFAULT_GREETING);

        final YamlConfiguration commands = config(COMMANDS);
        if (commands.getBoolean("commands.test.enabled", false)) {
            registerTestCommand(commands);
        }
        context.logger().info("aktiviert.");
    }

    /**
     * Reads the own configuration again after {@code /center reload}.
     *
     * <p>The module keeps running: nothing of it is loaded a second time, it only
     * looks at its own files again. That is exactly what the reload lifecycle is
     * for.</p>
     */
    @Override
    public void onReload() {
        greeting = config(MAIN_CONFIG).getString("greeting", DEFAULT_GREETING);
        context.logger().info("Konfiguration neu gelesen, Text ist jetzt: " + greeting);
    }

    @Override
    public void onDisable() {
        context.logger().info("deaktiviert.");
    }

    private void registerTestCommand(final YamlConfiguration commands) {
        final String path = commands.getString("commands.test.command");
        if (path == null || path.isBlank()) {
            context.logger().warn("'commands.test.command' fehlt in " + COMMANDS + ".");
            return;
        }
        // MHCenter2 removes the command again when the module is stopped, so the
        // module does not have to register a cleanup for it.
        context.registerCommand(path, sender -> sender.sendMessage(greeting));
        for (final String alias : commands.getStringList("commands.test.aliases")) {
            context.registerCommand(alias, sender -> sender.sendMessage(greeting));
        }
    }

    private YamlConfiguration config(final String fileName) {
        return YamlConfiguration.loadConfiguration(context.configDirectory().resolve(fileName).toFile());
    }

    /** Writes a bundled default file, but never over a file that already exists. */
    private void installDefault(final String fileName) throws IOException {
        final Path target = context.configDirectory().resolve(fileName);
        if (Files.exists(target)) {
            return;
        }
        try (InputStream source = TestModule.class.getClassLoader().getResourceAsStream("defaults/" + fileName)) {
            if (source == null) {
                throw new IOException("Die mitgelieferte Datei '" + fileName + "' fehlt im Modul.");
            }
            Files.copy(source, target);
        }
    }
}
