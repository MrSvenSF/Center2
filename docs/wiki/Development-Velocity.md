# Velocity-Module

Ein Modul mit `platform=VELOCITY` läuft auf dem Proxy und dort auch wirklich: es
durchläuft denselben Lebenszyklus wie ein Paper-Modul, darf eigene Proxy-Commands
mitbringen und kann den Proxy benutzen.

## Metadaten

```properties
id=MyProxyModule
name=My Proxy Module
version=1.0.0
author=Du
main=com.example.myproxymodule.MyProxyModule
platform=VELOCITY
center-min-version=1.0.0
center-max-version=1.0.0
```

Kein Minecraft-Bereich: der Proxy hat keine einzelne Minecraft-Version, und
MHCenter2 erfindet auch keine. Siehe [Metadaten](Development-Metadata.md).

## Was ein Proxy-Modul typischerweise ist

Fast nie ein Gameplay-Modul. Der Proxy ist die Stelle für:

* den Text im Multiplayer-Menü (MOTD, Serverlisten-Ping),
* Wartungsanzeigen,
* Spielerverbindungen und Trennungen beobachten,
* Serverwechsel erkennen,
* Routing,
* Netzwerkkoordination.

## Die Velocity-API holen

Das neutrale `ModuleContext` bleibt auf beiden Plattformen gleich. Alles, was
wirklich Velocity ist, liegt in einer kleinen eigenen Schnittstelle, die du dir
holst:

```java
import net.managerhub.center.api.velocity.VelocityModuleApi;

final Optional<VelocityModuleApi> proxy = context.service(VelocityModuleApi.class);
if (proxy.isEmpty()) {
    // Auf Paper gaebe es sie nicht. Ein VELOCITY-Modul laeuft aber nur hier.
    return;
}
```

Auf dem Proxy ist die Antwort immer da. Auf Paper ist sie leer – das ist der
Mechanismus, mit dem ein [BOTH-Modul](Development-Both.md) sauber unterscheidet.

Was drin ist:

```java
public interface VelocityModuleApi {

    ProxyServer proxy();

    <E> void subscribe(Class<E> eventType, EventHandler<E> handler);
    <E> void subscribe(Class<E> eventType, PostOrder order, EventHandler<E> handler);
    void subscribe(Object listener);

    ProxyTask schedule(Runnable task, Duration delay, Duration repeat);

    Collection<RegisteredServer> servers();
    Optional<RegisteredServer> server(String name);
    int onlinePlayerCount();
}
```

## Events

```java
import com.velocitypowered.api.event.player.ServerPostConnectEvent;

proxy.subscribe(ServerPostConnectEvent.class, event -> {
    final String player = event.getPlayer().getUsername();
    context.logger().info(player + " hat den Server gewechselt.");
});
```

Alles, was Velocity kennt, geht so: `PostLoginEvent` für Verbindungen,
`DisconnectEvent` für Trennungen, `ServerPreConnectEvent` und
`ServerConnectedEvent` für Serverwechsel, `ProxyPingEvent` für die Serverliste.

Mit `PostOrder` bestimmst du, wann dein Handler dran ist:

```java
proxy.subscribe(ProxyPingEvent.class, PostOrder.LATE, event -> { });
```

Auch ein Listener-Objekt mit `@Subscribe`-Methoden geht:

```java
proxy.subscribe(new MyListener());
```

**MHCenter2 meldet jede dieser Registrierungen wieder ab, wenn das Modul stoppt.**
Du brauchst dafür kein eigenes Cleanup.

## Serverlisten-Ping und MOTD

Der Text, den ein Spieler im Multiplayer-Menü sieht, kommt aus dem
`ProxyPingEvent`. Ein Modul kann ihn ändern:

```java
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.proxy.server.ServerPing;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

proxy.subscribe(ProxyPingEvent.class, event -> {
    final Component motd = MiniMessage.miniMessage()
            .deserialize("<gradient:#55ff55:#00aaaa>Mein Netzwerk</gradient>");
    event.setPing(event.getPing().asBuilder()
            .description(motd)
            .build());
});
```

MHCenter2 selbst bringt **kein** MOTD-Modul mit. Es sorgt nur dafür, dass du eines
schreiben kannst.

## Scheduler

```java
final VelocityModuleApi.ProxyTask task =
        proxy.schedule(this::check, Duration.ofSeconds(10), Duration.ofSeconds(60));
```

* `delay` ist die Wartezeit vor dem ersten Lauf, `Duration.ZERO` heißt sofort.
* `repeat` ist die Wiederholung, `Duration.ZERO` heißt einmalig.
* `task.cancel()` beendet ihn früher, `task.active()` fragt den Zustand ab.

Auch hier gilt: **MHCenter2 bricht den Task ab, wenn das Modul stoppt.**

## Server des Netzwerks

```java
for (final RegisteredServer server : proxy.servers()) {
    context.logger().info(server.getServerInfo().getName()
            + ": " + server.getPlayersConnected().size() + " Spieler");
}

proxy.server("lobby").ifPresent(lobby -> { /* ... */ });
```

## Commands

Genau wie auf Paper, über das neutrale `ModuleContext`:

```java
context.registerCommand("center proxytest",
        sender -> sender.sendMessage("<green>Laeuft."));
```

Der Rückgabewert ist ehrlich: `false` bedeutet, dass der Pfad zum Core gehört,
schon von einem anderen Modul benutzt wird oder dass der Commandname bereits
einem anderen Plugin des Proxys gehört. MHCenter2 prüft das gegen den
`CommandManager` von Velocity, bevor es zusagt. Der Grund steht im Log.

MHCenter2 entfernt Modulcommands beim Stoppen selbst.

## `proxy()` – und die Verantwortung dafür

`proxy()` gibt dir den laufenden `ProxyServer`. Damit geht alles, was Velocity
kann.

Aber: was du **direkt** dort registrierst, kennt MHCenter2 nicht und räumt es auch
nicht auf. Dafür musst du selbst sorgen:

```java
proxy.proxy().getEventManager().register(plugin, listener);
context.registerCleanup(() -> proxy.proxy().getEventManager().unregisterListener(plugin, listener));
```

Einfacher ist es, `subscribe(...)` und `schedule(...)` von MHCenter2 zu benutzen.

## Netzwerk

Ein Proxy-Modul hat dieselbe `context.network()` wie ein Paper-Modul: Actions
laufen bevorzugt über MariaDB und sonst über Plugin Messaging. Der gemeinsame
Storage bleibt MariaDB-only. Der Proxy ist ein vollwertiger Knoten. Siehe
[Netzwerk für Module](Development-Network.md).

## Vollständiges Beispiel

Das mitgelieferte `VelocityTestModule` macht genau das: Command, Event,
Scheduler, Serverliste, Cleanup, `onReload()`.

```java
public final class VelocityTestModule implements CenterModule {

    private final AtomicBoolean resourceOpen = new AtomicBoolean();
    private final AtomicInteger serverSwitches = new AtomicInteger();
    private final AtomicInteger ticks = new AtomicInteger();

    private ModuleContext context;

    @Override
    public void onLoad(final ModuleContext context) {
        this.context = context;
        resourceOpen.set(true);
        context.registerCleanup(() -> {
            resourceOpen.set(false);
            context.logger().info("Ressource wieder freigegeben.");
        });
        context.logger().info("geladen auf " + context.platform() + ".");
    }

    @Override
    public void onEnable() {
        final boolean accepted = context.registerCommand("center proxytest",
                sender -> sender.sendMessage("<green>MHCenter2 Velocity TestModule funktioniert."
                        + " <gray>Aufgerufen von <white>" + sender.name()));
        context.logger().info("Command angenommen: " + accepted + ".");

        final Optional<VelocityModuleApi> proxy = context.service(VelocityModuleApi.class);
        if (proxy.isEmpty()) {
            context.logger().warn("Keine Velocity-API erhalten.");
            return;
        }
        useProxy(proxy.get());
    }

    private void useProxy(final VelocityModuleApi proxy) {
        proxy.subscribe(ServerPostConnectEvent.class, event -> serverSwitches.incrementAndGet());
        proxy.schedule(ticks::incrementAndGet, Duration.ofSeconds(30), Duration.ofSeconds(30));

        final String servers = proxy.servers().stream()
                .map(server -> server.getServerInfo().getName())
                .reduce((left, right) -> left + ", " + right)
                .orElse("-");
        context.logger().info("Bekannte Server: " + servers + ".");
    }

    @Override
    public void onReload() {
        context.logger().info("Reload erhalten, Serverwechsel bisher: " + serverSwitches.get() + ".");
    }

    @Override
    public void onDisable() {
        context.logger().info("deaktiviert, Ressource noch offen: " + resourceOpen.get() + ".");
    }
}
```

Das `pom.xml` braucht dafür zwei `provided`-Abhängigkeiten: die MHCenter2-API und
die Velocity-API.

## Verwalten

Auf dem Proxy gibt es dieselben Modulcommands wie auf Paper:

```
center modules
center modules reload
center modules enable <id>
center modules disable <id>
```

Die Permissions sind fest eingebaut (`center.admin`, `center.admin.modules`,
…), weil der Proxy keine `Permissions.yml` hat.

> Verlass dich für die Netzwerkverwaltung nicht auf Proxy-Commands. Kein
> MHCenter2-Feature setzt voraus, dass sie funktionieren.

## Siehe auch

* [BOTH-Module](Development-Both.md)
* [Netzwerk für Module](Development-Network.md)
* [Reload](Development-Reload.md)
* [Cleanup](Development-Cleanup.md)
