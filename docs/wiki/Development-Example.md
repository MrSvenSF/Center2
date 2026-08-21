# Beispielmodule

Im Repository liegen zwei kleine Referenzprojekte. Beide sind bewusst
**keine** Gameplay-Module, sondern der technische Nachweis, dass eine wirklich
externe JAR erkannt, geladen, gestartet, neu geladen und wieder gestoppt wird.

| Projekt | Plattform | Beweist |
|---------|-----------|---------|
| `TestModule/` | `PAPER` | eigene Konfiguration, konfigurierbarer Command, `onReload()` |
| `VelocityTestModule/` | `VELOCITY` | Proxy-Command, Velocity-Event, Scheduler, Serverliste, Cleanup |

Beide sind eigenständige Maven-Projekte neben `Center2/`.

## Bauen

Zuerst den Core installieren, dann das Beispiel bauen:

```bash
cd Center2 && mvn clean install
cd ../TestModule && mvn clean package
cd ../VelocityTestModule && mvn clean package
```

Die fertigen JARs kopierst du von Hand nach
`plugins/Center2/Modules/Jars/` – das Paper-Beispiel auf den Server, das
Velocity-Beispiel auf den Proxy.

## TestModule (Paper)

Metadaten:

```properties
id=TestModule
name=Center2 TestModule
version=1.0.0-beta.1
author=Manager Hub
main=net.managerhub.center.testmodule.TestModule
platform=PAPER
center-min-version=1.0.0
center-max-version=1.0.0
minecraft-min-version=1.21.4
minecraft-max-version=1.21.11
```

Was es zeigt:

* **Eigene Konfiguration.** In `onLoad` legt es seinen Ordner unter
  `Modules/Configs/TestModule/` an und schreibt zwei mitgelieferte Standarddateien
  hinein, aber nie über eine vorhandene Datei.
* **Eigener Schalter.** `MainConfig.yml` des Moduls entscheidet, ob es sich
  überhaupt einschaltet.
* **Konfigurierbarer Command.** Der Pfad steht in der `Commands.yml` **des
  Moduls**, nicht in der des Cores:

```yaml
config-version: 1

commands:
  test:
    enabled: true
    command: "center test"
    aliases:
```

* **Reload.** In `MainConfig.yml` steht der Antworttext:

```yaml
enabled: true
greeting: "Center2 TestModule funktioniert."
```

  Änderst du ihn und tippst `/center reload`, antwortet der Command sofort anders
  – ohne Neustart, ohne dass der Command neu registriert wird.
* **Paper-API im Modul.** Es liest seine YAML-Dateien mit Bukkits
  `YamlConfiguration`. Das darf ein `PAPER`-Modul.
* **Kein Cleanup nötig.** Es registriert nur einen Command, und den entfernt
  Center2 selbst wieder.

Der Kern:

```java
public final class TestModule implements CenterModule {

    private ModuleContext context;
    private volatile String greeting = DEFAULT_GREETING;

    @Override
    public void onLoad(final ModuleContext context) throws IOException {
        this.context = context;
        Files.createDirectories(context.configDirectory());
        installDefault("MainConfig.yml");
        installDefault("Commands.yml");
        context.logger().info("geladen.");
    }

    @Override
    public void onEnable() {
        final YamlConfiguration main = config("MainConfig.yml");
        if (!main.getBoolean("enabled", false)) {
            context.logger().info("ist in MainConfig.yml ausgeschaltet.");
            return;
        }
        greeting = main.getString("greeting", DEFAULT_GREETING);

        final YamlConfiguration commands = config("Commands.yml");
        if (commands.getBoolean("commands.test.enabled", false)) {
            registerTestCommand(commands);
        }
        context.logger().info("aktiviert.");
    }

    @Override
    public void onReload() {
        greeting = config("MainConfig.yml").getString("greeting", DEFAULT_GREETING);
        context.logger().info("Konfiguration neu gelesen, Text ist jetzt: " + greeting);
    }

    @Override
    public void onDisable() {
        context.logger().info("deaktiviert.");
    }
}
```

Der Command liest das Feld bei jedem Aufruf:

```java
context.registerCommand(path, sender -> sender.sendMessage(greeting));
```

Ausprobieren:

```
/center modules
/center test
# greeting in Modules/Configs/TestModule/MainConfig.yml aendern
/center reload
/center test          -> neuer Text
/center modules disable TestModule
/center test          -> unbekannter Command
/center modules enable TestModule
/center test          -> funktioniert wieder
```

## VelocityTestModule (Proxy)

Metadaten – ohne Minecraft-Bereich:

```properties
id=VelocityTestModule
name=Center2 Velocity TestModule
version=1.0.0-beta.1
author=Manager Hub
main=net.managerhub.center.velocitytestmodule.VelocityTestModule
platform=VELOCITY
center-min-version=1.0.0
center-max-version=1.0.0
```

Was es zeigt:

* **Eigener Proxy-Command**, registriert über denselben `ModuleContext` wie auf
  Paper.
* **Velocity-Event.** Es abonniert `ServerPostConnectEvent` und zählt
  Serverwechsel.
* **Proxy-Scheduler.** Ein wiederholter Task, den Center2 beim Stoppen abbricht.
* **Serverliste.** Es schreibt die bekannten Backend-Server ins Log.
* **Cleanup**, das eine Ressource wieder freigibt.
* **`onReload()`**, das meldet, was es bisher gesehen hat.

Der Kern:

```java
@Override
public void onEnable() {
    final boolean accepted = context.registerCommand("center proxytest",
            sender -> sender.sendMessage("<green>Center2 Velocity TestModule funktioniert."
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
```

Ausprobieren, in der Proxykonsole:

```
center modules
center proxytest
center modules disable VelocityTestModule
center proxytest      -> nicht mehr vorhanden
center modules enable VelocityTestModule
center proxytest      -> funktioniert wieder
```

Im Log siehst du dabei auch, dass das Cleanup beim Deaktivieren läuft, welche
Server der Proxy kennt und was ein Reload meldet.

Sein `pom.xml` braucht zwei `provided`-Abhängigkeiten: die Center2-API und die
Velocity-API. Letztere nur, um den Eventtyp benennen zu können.

## Als Vorlage benutzen

Beide Projekte sind absichtlich klein. Für ein eigenes Modul:

1. Projekt kopieren.
2. `groupId`, `artifactId`, Paketnamen und `finalName` ändern.
3. `center-module.properties` anpassen: eigene `id`, `name`, `author`, `main` und
   passende Versionsbereiche.
4. Eigene Logik in `onEnable` schreiben.

Der Einstieg mit allen Schritten steht unter
[Erstes Modul](Development-First-Module.md).
