# Eigenes MHCenter2-Modul entwickeln

Ein MHCenter2-Modul erweitert MHCenter2 um eigene Funktionen. Es ist eine normale
JAR, die gegen die MHCenter2-Modul-API gebaut wird und unter
`plugins/MHCenter2/Modules/Jars/` liegt.

## Was ein Modul ist – und was nicht

Ein MHCenter2-Modul ist **kein** Paper-Plugin:

* keine `JavaPlugin`-Unterklasse,
* keine `plugin.yml`,
* keine eigene Pluginregistrierung,
* kein eigener Paper- oder Velocity-Lifecycle.

MHCenter2 kontrolliert Laden, Aktivieren, Deaktivieren und den Fehlerzustand. Ein
Modul entscheidet nicht selbst, dass es aktiv ist.

## Der Weg durch die Entwicklerdoku

| Seite | Inhalt |
|-------|--------|
| [Erstes Modul](Development-First-Module.md) | Maven-Projekt, Hauptklasse, JAR bauen, installieren |
| [Metadaten](Development-Metadata.md) | `center-module.properties` vollständig |
| [Versionskompatibilität](Development-Versioning.md) | MHCenter2- und Minecraft-Bereiche |
| [Paper-Module](Development-Paper.md) | `platform=PAPER` |
| [Velocity-Module](Development-Velocity.md) | `platform=VELOCITY`, Events, MOTD, Scheduler |
| [BOTH-Module](Development-Both.md) | ein Modul für beide Seiten |
| [Commands](Development-Commands.md) | eigene Commands registrieren |
| [Reload](Development-Reload.md) | `onReload()` und was ein Reload nicht ist |
| [Netzwerk für Module](Development-Network.md) | Remote-Storage und Remote-Actions |
| [Cleanup](Development-Cleanup.md) | Ressourcen wieder freigeben |
| [Fehlerbehandlung](Development-Errors.md) | ERROR, Isolation, Logs |
| [API-Referenz](Development-API.md) | der offiziell unterstützte Vertrag |
| [Beispielmodule](Development-Example.md) | die mitgelieferten TestModule |

## Der Lebenszyklus

```java
public interface CenterModule {
    void onLoad(ModuleContext context) throws Exception;
    void onEnable() throws Exception;
    default void onReload() throws Exception { }
    void onDisable() throws Exception;
}
```

MHCenter2 ruft sie in dieser Reihenfolge und nie parallel auf:

1. **`onLoad(context)`** – der Kontext wird übergeben. Nichts vom Modul ist aktiv.
   Hier den Kontext merken und die eigene Konfiguration lesen.
2. **`onEnable()`** – das Modul startet. Commands, Listener und Tasks gehören
   hierhin.
3. **`onReload()`** – bei jedem `/center reload`. Eigene Konfiguration neu lesen,
   Caches leeren. Hat eine Standardimplementierung, ist also freiwillig.
4. **`onDisable()`** – das Modul wird gestoppt.

Die Hauptklasse braucht einen **öffentlichen Konstruktor ohne Argumente**.

Kein Schritt davon lädt eine JAR neu: eine geänderte Modul-JAR braucht weiterhin
einen Serverneustart.

## Was ein Modul von MHCenter2 bekommt

```java
context.moduleId();          // die eigene ID
context.configDirectory();   // der eigene Ordner
context.logger();            // das eigene Log
context.platform();          // PAPER oder VELOCITY
context.registerCleanup(r);  // wie eine Ressource wieder verschwindet
context.registerCommand(p, c); // ein eigener Command
context.network();           // Remote-Storage und Remote-Actions
context.service(type);       // Zusatzdienste der Plattform, auf Velocity die Proxy-API
```

Alles davon ist auf beiden Plattformen gleich. Nur `service(...)` antwortet
unterschiedlich – das ist die eine Stelle, an der Plattformcode anfängt.

## Voraussetzungen

* Java 25
* Maven
* MHCenter2 einmal lokal installiert, damit die API als Abhängigkeit verfügbar ist:

```bash
cd MHCenter2
mvn clean install
```

## Wichtig: Module sind keine Sandbox

Ein Modul ist normaler Java-Code im selben Prozess wie der Server, mit denselben
Möglichkeiten wie jeder andere Code dort. MHCenter2 verwaltet den Lebenszyklus, es
isoliert keine Rechte. Was MHCenter2 leistet, ist **Fehlerisolation**: ein
abstürzendes Modul reißt weder den Core noch andere Module mit.

Für Serverbetreiber heißt das: Module nur aus vertrauenswürdigen Quellen
installieren. Für dich als Autor heißt das: Sorgfalt liegt bei dir.
