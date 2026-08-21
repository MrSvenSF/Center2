# API-Referenz

Diese Seite beschreibt den **offiziell unterstützten** Center2-Modulvertrag.

## Die Grenze

Alles unter

```
net.managerhub.center.api
net.managerhub.center.api.velocity
```

ist die API. Nur diese Klassen darfst du als Modulautor verwenden.

Jedes andere Center2-Paket ist **intern**: Loader, Menü, Datenbank,
Command-Registrierung, Konfiguration, Remote-Implementierung und die
Plattform-Einstiegspunkte. Sie dürfen sich in jeder Version ändern, auch wenn
eine Klasse dort zufällig `public` ist. Ein Modul, das dorthin greift, wird nicht
unterstützt.

Das ist keine automatisch erzeugte JavaDoc aller Center2-Klassen, sondern der
Vertrag.

## Übersicht

| Typ | Zweck |
|-----|-------|
| `CenterModule` | die Hauptklasse deines Moduls |
| `ModuleContext` | alles, was das Modul von Center2 bekommt |
| `ModuleLogger` | das Log des Moduls |
| `ModulePlatform` | auf welcher Plattform es läuft |
| `ModuleCommand` | was ein Modulcommand tut |
| `ModuleCommandSender` | wer den Command benutzt hat |
| `ModuleNetwork` | was das Modul im Center2-Netzwerk darf |
| `ModuleStorage` | kurzlebige Daten in der Remote-Datenbank |
| `ModuleActionTarget` | welche Knoten eine Action erreichen soll |
| `ModuleActionMessage` | eine empfangene Action |
| `ModuleActionListener` | was das Modul mit einer Action tut |
| `ModuleRemoteException` | eine Remote-Operation ging nicht |
| `velocity.VelocityModuleApi` | die Proxy-Fähigkeiten, nur auf Velocity |

## CenterModule

```java
public interface CenterModule {
    void onLoad(ModuleContext context) throws Exception;
    void onEnable() throws Exception;
    default void onReload() throws Exception { }
    void onDisable() throws Exception;
}
```

Die Hauptklasse deines Moduls implementiert dieses Interface und braucht einen
**öffentlichen Konstruktor ohne Argumente**.

Vertrag:

* Center2 ruft die Methoden in dieser Reihenfolge und **nie parallel** auf.
* `onLoad` bekommt den Kontext. Nichts vom Modul ist aktiv; hier den Kontext
  merken und die eigene Konfiguration lesen.
* `onEnable` startet das Modul: Commands, Listener, Tasks.
* `onReload` läuft bei jedem `/center reload` auf jedem laufenden Modul. Die
  Standardimplementierung tut nichts, ältere Module brechen dadurch nicht.
* `onDisable` wird für jedes Modul aufgerufen, das erfolgreich aktiviert war.
* Jede Methode darf werfen. Center2 setzt dann `ERROR`, führt das registrierte
  Cleanup aus und lässt Core und andere Module weiterlaufen.

Kein Aufruf davon lädt eine JAR neu. Siehe [Reload](Development-Reload.md).

## ModuleContext

```java
public interface ModuleContext {
    String moduleId();
    Path configDirectory();
    ModuleLogger logger();
    ModulePlatform platform();
    void registerCleanup(Runnable cleanup);
    boolean registerCommand(String path, ModuleCommand command);
    ModuleNetwork network();
    <T> Optional<T> service(Class<T> type);
}
```

Auf beiden Plattformen identisch. Ein Velocity-Modul lädt darüber nie eine
Paper-Klasse und umgekehrt.

| Methode | Vertrag |
|---------|---------|
| `moduleId()` | die ID aus den Metadaten |
| `configDirectory()` | `Modules/Configs/<id>/`. Center2 legt dort **nichts** an; Ordner und Dateien erzeugt das Modul selbst. |
| `logger()` | das Log dieses Moduls, jede Zeile mit `[<id>]` davor |
| `platform()` | `PAPER` oder `VELOCITY`, **nie** `BOTH` |
| `registerCleanup(Runnable)` | meldet an, wie eine Ressource wieder verschwindet; läuft bei Disable, bei Fehlern und beim Serverende, neueste Aktion zuerst |
| `registerCommand(String, ModuleCommand)` | registriert einen Command; `true` heißt, er ist wirklich aufgenommen |
| `network()` | das Netzwerk dieses Moduls, immer vorhanden |
| `service(Class)` | fragt nach einem Zusatzdienst der Plattform |

`registerCommand` liefert `false`, wenn der Pfad ungültig ist, einem
Center2-Core-Command oder einem seiner Aliase gehört, ein anderes Modul ihn schon
benutzt, oder der Commandname bereits einem **anderen Plugin** der Plattform
gehört. Center2 fragt dafür die Commandliste von Paper beziehungsweise Velocity –
die Antwort ist also keine Vermutung. Der Grund steht im Serverlog. Center2
entfernt einen registrierten Command von allein wieder, wenn das Modul stoppt
oder scheitert.

`service(Class)` ist die einzige Stelle, an der ein Modul den neutralen Teil der
API verlässt. Auf dem Proxy antwortet sie mit `VelocityModuleApi`, auf Paper
antwortet sie leer. Die Signatur enthält keinen Plattformtyp, das Fragen ist also
überall sicher – siehe [BOTH-Module](Development-Both.md).

## ModuleLogger

```java
public interface ModuleLogger {
    void info(String message);
    void warn(String message);
    void error(String message, Throwable failure);
}
```

Schreibt in das Log der Plattform, mit `[<modul-id>]` davor. `failure` darf
`null` sein.

## ModulePlatform

```java
public enum ModulePlatform {
    PAPER, VELOCITY, BOTH;

    boolean supports(ModulePlatform running);
}
```

`BOTH` steht nur in den Metadaten. `ModuleContext.platform()` antwortet zur
Laufzeit immer mit `PAPER` oder `VELOCITY`.

## ModuleCommand

```java
@FunctionalInterface
public interface ModuleCommand {
    void execute(ModuleCommandSender sender);
}
```

Wird über `ModuleContext.registerCommand` übergeben, meist als Lambda.

## ModuleCommandSender

```java
public interface ModuleCommandSender {
    String name();
    boolean isPlayer();
    boolean hasPermission(String permission);
    void sendMessage(String message);
}
```

| Methode | Vertrag |
|---------|---------|
| `name()` | Spielername, oder `CONSOLE` für die Konsole |
| `isPlayer()` | `false` für Konsole und Command-Blöcke |
| `hasPermission(String)` | fragt das Permissionsystem der Plattform |
| `sendMessage(String)` | eine Zeile, gelesen als Adventure MiniMessage |

## ModuleNetwork

```java
public interface ModuleNetwork {
    boolean available();
    String serverId();
    List<String> onlineNodes();
    ModuleStorage storage();
    void onAction(ModuleActionListener listener);
    void send(String type, ModuleActionTarget target, byte[] payload, Duration lifetime)
            throws ModuleRemoteException;
}
```

Immer vorhanden. `available()` ist wahr, wenn MariaDB oder der verifizierte
Plugin-Messaging-Fallback gerade eine Action transportieren kann. Für den
MariaDB-only Storage gilt separat `network.storage().available()`. Details unter
[Netzwerk für Module](Development-Network.md).

## ModuleStorage

```java
public interface ModuleStorage {
    boolean available();
    void put(String key, byte[] payload, Duration ttl) throws ModuleRemoteException;
    Optional<byte[]> get(String key) throws ModuleRemoteException;
    Optional<byte[]> take(String key) throws ModuleRemoteException;
    boolean delete(String key) throws ModuleRemoteException;
}
```

| Regel | |
|-------|---|
| Namensraum | deine Modul-ID; fremde Einträge sind unerreichbar |
| Schlüssel | bis 190 Zeichen |
| Payload | bis 8 MiB, Center2 schaut nicht hinein |
| Ablaufzeit | Pflicht, mindestens eine Sekunde |
| `take()` | liefert die Daten **genau einmal** im ganzen Netzwerk |
| ohne Remote | jede Methode wirft; es gibt **keinen** lokalen Fallback |
| Thread | blockiert; nie auf dem Mainthread oder Event-Loop aufrufen |

## ModuleActionTarget

```java
ModuleActionTarget.ALL
ModuleActionTarget.PAPER
ModuleActionTarget.VELOCITY
ModuleActionTarget.server("survival")
```

## ModuleActionMessage

```java
public record ModuleActionMessage(UUID id, String type, String origin, byte[] payload) { }
```

`origin` ist die `remote.server-id` des Knotens, der die Action geschickt hat.
`payload()` gibt eine Kopie zurück.

## ModuleActionListener

```java
@FunctionalInterface
public interface ModuleActionListener {
    void onAction(ModuleActionMessage action) throws Exception;
}
```

Läuft im Hintergrund-Thread von Center2, nicht auf dem Mainthread. Wirft der
Listener, wird das protokolliert und die Action für diesen Knoten als
fehlgeschlagen vermerkt – sie wird nicht endlos wiederholt.

## VelocityModuleApi

```java
package net.managerhub.center.api.velocity;

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

Nur auf dem Proxy, erreichbar über `context.service(VelocityModuleApi.class)`.
Sie verwendet bewusst Velocity-Typen. Alles, was ein Modul **hierüber**
registriert, entfernt Center2 beim Stoppen wieder; was ein Modul direkt auf
`proxy()` registriert, nicht. Details unter
[Velocity-Module](Development-Velocity.md).

## Metadaten als Teil des Vertrags

`center-module.properties` gehört zur API: die Feldnamen und ihre Bedeutung
ändern sich nicht innerhalb einer Center2-Reihe. Siehe
[Metadaten](Development-Metadata.md).

## Nicht Teil der API

Ausdrücklich **nicht** unterstützt und jederzeit änderbar:

* der Loader und alles rund um Modulzustände intern,
* Menüs, Menükonfiguration und Inventarlogik,
* die SQLite-Datenbank und ihr Schema,
* die Tabellen der Remote-Datenbank und die Klassen unter
  `net.managerhub.center.common.remote`,
* die Command-Registrierung von Center2 selbst,
* Konfigurationsklassen, Sprachdateien und Textschlüssel,
* die Paper- und Velocity-Einstiegspunkte.

Brauchst du etwas Plattformspezifisches, nimm die Paper- beziehungsweise
Velocity-API direkt. Center2 steht dem nicht im Weg, gibt aber nichts von seinem
Innenleben heraus.

## Sicherheitshinweis

Module sind **keine Sandbox**. Ein Modul ist normaler Java-Code im selben Prozess
mit denselben Möglichkeiten wie jeder andere Code dort. Die Cleanup-API ist
Lifecycle-Verwaltung, keine Sicherheitsisolation. Siehe [Sicherheit](Security.md).
