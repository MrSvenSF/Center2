package net.managerhub.center.velocitytestmodule;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import net.managerhub.center.api.CenterModule;
import net.managerhub.center.api.ModuleContext;
import net.managerhub.center.api.ModulePlatform;
import net.managerhub.center.api.velocity.VelocityModuleApi;

/**
 * The Center2 Velocity test module.
 *
 * <p>It exists to prove that an external jar below {@code Modules/Jars} of a
 * <em>proxy</em> is found, read, loaded, started, reloaded, stopped and started
 * again, that a proxy module can bring along its own command, and that it can
 * really use the proxy: Velocity events, the proxy scheduler and the list of
 * backend servers.</p>
 *
 * <p>It is deliberately not a feature module. It changes nothing about the
 * network; it only writes down what it saw.</p>
 *
 * <p>The everyday part of the module - id, folder, log, cleanup, command,
 * network - comes from the platform neutral Center2 module API. Only
 * {@link VelocityModuleApi}, which it asks for with
 * {@code context.service(...)}, is Velocity specific.</p>
 */
public final class VelocityTestModule implements CenterModule {

    /** Stands for any resource a real module would have to release again. */
    private final AtomicBoolean resourceOpen = new AtomicBoolean();

    /** How often the proxy told this module that somebody changed server. */
    private final AtomicInteger serverSwitches = new AtomicInteger();

    /** How often the scheduled task of this module ran. */
    private final AtomicInteger ticks = new AtomicInteger();

    private ModuleContext context;

    @Override
    public void onLoad(final ModuleContext context) {
        this.context = context;
        resourceOpen.set(true);
        // Center2 runs this when the module is stopped and also when a later step
        // of the start fails.
        context.registerCleanup(() -> {
            resourceOpen.set(false);
            context.logger().info("Ressource wieder freigegeben.");
        });
        context.logger().info("geladen auf " + context.platform() + ".");
    }

    @Override
    public void onEnable() {
        final boolean accepted = context.registerCommand("center proxytest",
                sender -> sender.sendMessage("<green>Center2 Velocity TestModule funktioniert."
                        + " <gray>Aufgerufen von <white>" + sender.name()));
        context.logger().info("Command angenommen: " + accepted + ".");

        // A proxy module asks for the Velocity part of the API. On Paper this
        // would answer empty, which is what makes the same code safe in a BOTH
        // module - as long as it stays inside this branch.
        final Optional<VelocityModuleApi> proxy = context.service(VelocityModuleApi.class);
        if (proxy.isEmpty()) {
            context.logger().warn("Keine Velocity-API erhalten. Auf einem Proxy sollte es sie geben.");
            return;
        }
        useProxy(proxy.get());
        context.logger().info("aktiviert, Netzwerk verfuegbar: " + context.network().available() + ".");
    }

    /**
     * Everything this module does with the proxy.
     *
     * <p>All three registrations go through Center2, so all three are removed
     * again when the module is stopped - the event handler, the scheduled task
     * and the command.</p>
     */
    private void useProxy(final VelocityModuleApi proxy) {
        // A Velocity event. A real module would change something here; this one
        // only counts.
        proxy.subscribe(com.velocitypowered.api.event.player.ServerPostConnectEvent.class,
                event -> serverSwitches.incrementAndGet());

        // The proxy scheduler. Center2 cancels the task when the module stops.
        proxy.schedule(ticks::incrementAndGet, Duration.ofSeconds(30), Duration.ofSeconds(30));

        final String servers = proxy.servers().stream()
                .map(server -> server.getServerInfo().getName())
                .reduce((left, right) -> left + ", " + right)
                .orElse("-");
        context.logger().info("Bekannte Server: " + servers + ".");
    }

    @Override
    public void onReload() {
        // Nothing to read again here, so the module only says that it noticed.
        // A real module would read its own configuration at this point.
        context.logger().info("Reload erhalten, Serverwechsel bisher: " + serverSwitches.get()
                + ", Task-Laeufe: " + ticks.get() + ".");
    }

    @Override
    public void onDisable() {
        context.logger().info("deaktiviert, Ressource noch offen: " + resourceOpen.get()
                + ", Plattform war: " + platformName() + ".");
    }

    private String platformName() {
        final ModulePlatform platform = context.platform();
        return platform == null ? "?" : platform.name();
    }
}
