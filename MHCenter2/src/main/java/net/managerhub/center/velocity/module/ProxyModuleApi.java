package net.managerhub.center.velocity.module;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;

import com.velocitypowered.api.event.EventHandler;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.TaskStatus;
import net.managerhub.center.api.velocity.VelocityModuleApi;
import net.managerhub.center.common.module.ModuleCleanup;

/**
 * What MHCenter2 hands a proxy module when it asks for
 * {@link VelocityModuleApi}.
 *
 * <p>Every registration goes through MHCenter2 and every registration immediately
 * says how it is removed again. That is the whole difference to a module that
 * would talk to {@code proxy.getEventManager()} on its own: a module that is
 * switched off, that fails while starting or that is still running when the proxy
 * stops leaves no event handler and no task behind.</p>
 *
 * <p>One instance per module. It carries the plugin object Velocity needs for a
 * registration - the MHCenter2 plugin, because a module is not a Velocity plugin
 * and cannot be one - and the cleanup of exactly that module.</p>
 */
public final class ProxyModuleApi implements VelocityModuleApi {

    private final ProxyServer proxy;
    private final Object plugin;
    private final ModuleCleanup cleanup;

    /**
     * @param proxy   the running proxy
     * @param plugin  the MHCenter2 plugin instance, which owns every registration
     * @param cleanup the cleanup of the module this API belongs to
     */
    public ProxyModuleApi(final ProxyServer proxy, final Object plugin, final ModuleCleanup cleanup) {
        this.proxy = proxy;
        this.plugin = plugin;
        this.cleanup = cleanup;
    }

    @Override
    public ProxyServer proxy() {
        return proxy;
    }

    @Override
    public <E> void subscribe(final Class<E> eventType, final EventHandler<E> handler) {
        subscribe(eventType, PostOrder.NORMAL, handler);
    }

    @Override
    public <E> void subscribe(final Class<E> eventType, final PostOrder order, final EventHandler<E> handler) {
        final EventManager events = proxy.getEventManager();
        events.register(plugin, eventType, order, handler);
        cleanup.register(() -> events.unregister(plugin, handler));
    }

    @Override
    public void subscribe(final Object listener) {
        final EventManager events = proxy.getEventManager();
        events.register(plugin, listener);
        cleanup.register(() -> events.unregisterListener(plugin, listener));
    }

    @Override
    public ProxyTask schedule(final Runnable task, final Duration delay, final Duration repeat) {
        var builder = proxy.getScheduler().buildTask(plugin, task);
        if (delay != null && !delay.isZero() && !delay.isNegative()) {
            builder = builder.delay(delay);
        }
        if (repeat != null && !repeat.isZero() && !repeat.isNegative()) {
            builder = builder.repeat(repeat);
        }
        final ScheduledTask scheduled = builder.schedule();
        cleanup.register(scheduled::cancel);
        return new Task(scheduled);
    }

    @Override
    public Collection<RegisteredServer> servers() {
        return proxy.getAllServers();
    }

    @Override
    public Optional<RegisteredServer> server(final String name) {
        return name == null ? Optional.empty() : proxy.getServer(name);
    }

    @Override
    public int onlinePlayerCount() {
        return proxy.getPlayerCount();
    }

    /** One scheduled task of a module, seen through the module API. */
    private record Task(ScheduledTask scheduled) implements ProxyTask {

        @Override
        public void cancel() {
            scheduled.cancel();
        }

        @Override
        public boolean active() {
            return scheduled.status() == TaskStatus.SCHEDULED;
        }
    }
}
