package net.managerhub.center.api.velocity;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;

import com.velocitypowered.api.event.EventHandler;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;

/**
 * What a MHCenter2 module may do on Velocity.
 *
 * <p>This is the small platform part of the module API. A module asks for it with
 * {@code context.service(VelocityModuleApi.class)}; on Paper the answer is empty,
 * on the proxy it is this object. Everything here is deliberately close to
 * Velocity itself - a proxy module that may not use Velocity types could not do
 * anything useful.</p>
 *
 * <h2>Cleanup</h2>
 *
 * <p>Everything a module registers <em>through this object</em> is removed again
 * by MHCenter2 when the module is stopped, when it fails while starting and when
 * the proxy shuts down. That covers event handlers and scheduled tasks. What a
 * module registers directly on {@link #proxy()} is not known to MHCenter2 and has
 * to be removed by the module itself with
 * {@code context.registerCleanup(...)}.</p>
 *
 * <h2>What this is good for</h2>
 *
 * <p>The typical proxy module is not a gameplay module. It changes the text a
 * player sees in the multiplayer list, it watches who connects, disconnects or
 * switches server, it looks at which backend servers are there, or it
 * coordinates something across the network. All of that is one event
 * subscription and, if the module also has to talk to the Paper servers, the
 * network of {@code context.network()}.</p>
 *
 * <p>MHCenter2 brings no MOTD module and no routing module of its own. It only
 * makes sure a module can write one.</p>
 */
public interface VelocityModuleApi {

    /**
     * The running proxy.
     *
     * <p>Everything Velocity offers is reachable here. MHCenter2 does not watch
     * what a module does with it, so anything registered directly on the proxy
     * has to be removed by the module itself.</p>
     *
     * @return the proxy MHCenter2 is running on
     */
    ProxyServer proxy();

    /**
     * Subscribes to one Velocity event.
     *
     * <p>This is the way to react to {@code PostLoginEvent},
     * {@code DisconnectEvent}, {@code ServerConnectedEvent},
     * {@code ServerPreConnectEvent}, {@code ProxyPingEvent} and every other
     * Velocity event. MHCenter2 unsubscribes the handler again when the module is
     * stopped.</p>
     *
     * @param eventType the event class
     * @param handler   what happens when the event fires
     * @param <E>       type of the event
     */
    <E> void subscribe(Class<E> eventType, EventHandler<E> handler);

    /**
     * Subscribes to one Velocity event with an explicit order.
     *
     * @param eventType the event class
     * @param order     when this handler runs compared to the other handlers
     * @param handler   what happens when the event fires
     * @param <E>       type of the event
     */
    <E> void subscribe(Class<E> eventType, PostOrder order, EventHandler<E> handler);

    /**
     * Subscribes an object that carries {@code @Subscribe} methods.
     *
     * <p>The same object may only be registered once; MHCenter2 unregisters it
     * again when the module is stopped.</p>
     *
     * @param listener a listener object of the module
     */
    void subscribe(Object listener);

    /**
     * Runs something on the scheduler of the proxy.
     *
     * <p>The task is cancelled when the module is stopped, so a module cannot
     * leave a running task behind.</p>
     *
     * @param task   what to run
     * @param delay  how long to wait before the first run, {@link Duration#ZERO} for right away
     * @param repeat how often to repeat it; {@link Duration#ZERO} runs the task only once
     * @return a handle that cancels the task earlier
     */
    ProxyTask schedule(Runnable task, Duration delay, Duration repeat);

    /** @return every backend server the proxy knows, in the order of its own configuration. */
    Collection<RegisteredServer> servers();

    /**
     * @param name name of the server as the proxy configuration writes it
     * @return the server, or empty if the proxy does not know it
     */
    Optional<RegisteredServer> server(String name);

    /**
     * @return the number of players that are connected to the proxy right now
     */
    int onlinePlayerCount();

    /** One scheduled task of a module. */
    interface ProxyTask {

        /** Stops the task. Calling it twice does nothing. */
        void cancel();

        /** @return {@code true} if the task is still scheduled. */
        boolean active();
    }
}
