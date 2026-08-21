# Paper-Module

Ein Modul mit `platform=PAPER` läuft ausschließlich auf einem Paper-Server. Es
darf die Paper-API benutzen.

## Metadaten

```properties
id=MeinPaperModul
name=Mein Paper Modul
version=1.0.1
author=Dein Name
main=com.example.paper.MeinPaperModul
platform=PAPER
center-min-version=1.0.1
center-max-version=1.0.1
minecraft-min-version=1.21.4
minecraft-max-version=1.21.11
```

Der Minecraft-Bereich ist Pflicht.

## Maven

Neben der MHCenter2-API kommt die Paper-API dazu, beide `provided`:

```xml
  <repositories>
    <repository>
      <id>papermc</id>
      <url>https://repo.papermc.io/repository/maven-public/</url>
    </repository>
  </repositories>

  <dependencies>
    <dependency>
      <groupId>net.managerhub</groupId>
      <artifactId>mhcenter2</artifactId>
      <version>1.0.1</version>
      <scope>provided</scope>
    </dependency>
    <dependency>
      <groupId>io.papermc.paper</groupId>
      <artifactId>paper-api</artifactId>
      <version>1.21.11-R0.1-SNAPSHOT</version>
      <scope>provided</scope>
    </dependency>
  </dependencies>
```

## Beispiel

```java
package com.example.paper;

import net.managerhub.center.api.CenterModule;
import net.managerhub.center.api.ModuleContext;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

public final class MeinPaperModul implements CenterModule {

    private ModuleContext context;

    @Override
    public void onLoad(final ModuleContext context) {
        this.context = context;
    }

    @Override
    public void onEnable() {
        final Plugin center = Bukkit.getPluginManager().getPlugin("MHCenter2");
        final Listener listener = new JoinListener();

        Bukkit.getPluginManager().registerEvents(listener, center);
        // MHCenter2 weiss nichts von diesem Listener, also sagen wir gleich,
        // wie er wieder verschwindet.
        context.registerCleanup(() -> HandlerList.unregisterAll(listener));

        context.registerCommand("meinmodul hallo",
                sender -> sender.sendMessage("<green>Hallo, " + sender.name() + "!"));
    }

    @Override
    public void onDisable() {
        context.logger().info("deaktiviert.");
    }

    private static final class JoinListener implements Listener {

        @EventHandler
        public void onJoin(final PlayerJoinEvent event) {
            event.getPlayer().sendMessage("Mein Paper Modul ist aktiv.");
        }
    }
}
```

Für `HandlerList` fehlt oben der Import `org.bukkit.event.HandlerList`; im echten
Projekt gehört er dazu.

## MHCenter2 als Plugin-Instanz

Paper verlangt für Listener und Scheduler eine `Plugin`-Instanz. Ein Modul ist
selbst kein Plugin, benutzt also die MHCenter2-Instanz:

```java
final Plugin center = Bukkit.getPluginManager().getPlugin("MHCenter2");
```

Das ist Paper-API, nicht MHCenter2-API: MHCenter2 gibt nichts von seinem Innenleben
heraus.

Beachte: Wenn MHCenter2 heruntergefahren wird, entfernt Paper alles, was unter
dieser Plugin-Instanz registriert wurde. Beim **Deaktivieren eines einzelnen
Moduls** passiert das nicht automatisch, deshalb ist das Cleanup wichtig, siehe
[Cleanup](Development-Cleanup.md).

## Ein Modul ist kein Plugin

Nicht tun:

* keine Klasse, die `JavaPlugin` erweitert,
* keine `plugin.yml` in der Modul-JAR,
* kein `Bukkit.getPluginManager().enablePlugin(...)`,
* keine Registrierung des Moduls als eigenständiges Plugin.

Ein Modul, das sich selbst als Plugin registriert, entzieht sich der MHCenter2-
Verwaltung: MHCenter2 könnte es nicht mehr sauber stoppen, seinen Zustand nicht
mehr anzeigen und seine Fehler nicht mehr isolieren. Genau das ist der Sinn des
Modulsystems.

## Hauptthread

MHCenter2 ruft `onLoad`, `onEnable`, `onReload` und `onDisable` auf dem Hauptthread
des Servers auf, genau wie die Command-Registrierung. Für alles Weitere gelten
die normalen Paper-Regeln: langlaufende Arbeit gehört asynchron, Welt- und
Spielerzugriffe gehören auf den Hauptthread.

Startest du eigene Tasks oder Threads, melde ihr Ende als Cleanup an.

Zwei Ausnahmen, bei denen du **nicht** auf dem Hauptthread bist:

* der Listener von `context.network().onAction(...)`,
* jeder Aufruf von `context.network().storage()`.

Beides läuft im Hintergrund-Thread von MHCenter2. Umgekehrt gilt: rufe den Storage
**nie** vom Hauptthread aus auf – er blockiert auf einem Datenbankaufruf. Siehe
[Netzwerk für Module](Development-Network.md).

## Reload

`/center reload` ruft `onReload()` auf deinem laufenden Modul auf. Es ist ein
Konfigurations-Reload: deine Instanz bleibt dieselbe, deine Commands bleiben
registriert, und deine JAR wird nicht neu geladen. Siehe
[Reload](Development-Reload.md).
