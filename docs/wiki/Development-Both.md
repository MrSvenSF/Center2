# BOTH-Module

`platform=BOTH` heißt: dieselbe JAR läuft auf Paper **und** auf Velocity. Sie
wird auf beiden Seiten installiert und dort jeweils vollständig geladen.

```properties
platform=BOTH
center-min-version=1.0.0
center-max-version=1.0.0
minecraft-min-version=1.21.4
minecraft-max-version=1.21.11
```

Der Minecraft-Bereich ist Pflicht, weil das Modul auch auf Paper läuft. Auf dem
Proxy wird er nicht geprüft – dort gibt es keine einzelne Minecraft-Version.

## Wo bin ich?

```java
@Override
public void onLoad(final ModuleContext context) {
    this.context = context;
}

@Override
public void onEnable() {
    switch (context.platform()) {
        case PAPER -> startOnPaper();
        case VELOCITY -> startOnProxy();
        case BOTH -> throw new IllegalStateException("kommt nie vor");
    }
}
```

`context.platform()` antwortet immer mit der Plattform, auf der das Modul
gerade wirklich läuft: `PAPER` oder `VELOCITY`, **nie** `BOTH`.

## Die eine Regel, die wirklich zählt

**Keine Klasse der einen Plattform darf beim Laden der anderen gebraucht
werden.**

Java lädt eine Klasse erst, wenn sie tatsächlich benutzt wird – aber es lädt sie
komplett, sobald eine Methode deiner Klasse darauf verweist. Steht ein
Bukkit-Aufruf im selben Methodenrumpf wie deine Proxy-Logik, versucht der
ClassLoader beim ersten Aufruf dieser Methode, `org.bukkit.Bukkit` zu finden – auf
dem Proxy gibt es die Klasse nicht, und das Modul fliegt mit einem
`NoClassDefFoundError` in `ERROR`.

### So nicht

```java
@Override
public void onEnable() {
    if (context.platform() == ModulePlatform.PAPER) {
        // Bukkit steht im selben Methodenrumpf: die Methode selbst wird
        // beim Verifizieren bereits gegen org.bukkit aufgeloest.
        Bukkit.getScheduler().runTask(...);
    }
}
```

### So

```java
@Override
public void onEnable() {
    if (context.platform() == ModulePlatform.PAPER) {
        // Erst hier wird PaperPart geladen - und nur auf Paper.
        new PaperPart().start(context);
    } else {
        new ProxyPart().start(context);
    }
}
```

`PaperPart` ist eine eigene Klasse und darf drinnen alles von Bukkit benutzen.
`ProxyPart` ist eine eigene Klasse und darf drinnen alles von Velocity benutzen.
Keine der beiden wird auf der falschen Seite je geladen.

Dasselbe gilt für Felder, Methodensignaturen, Rückgabetypen und Lambdas: sobald
eine Klasse deines Moduls einen Typ der Plattform in ihrer Signatur trägt, muss
sie eine eigene Klasse sein.

## Die Velocity-API im BOTH-Modul

Nach demselben Muster:

```java
// Neutral, laedt nichts von Velocity: die Signatur kennt nur Class und Optional.
context.service(VelocityModuleApi.class).ifPresent(proxy -> new ProxyPart().start(context, proxy));
```

Auf Paper antwortet `service(...)` leer, der Lambda-Rumpf läuft nicht, und
`ProxyPart` wird nie geladen. Auf dem Proxy bekommst du die API.

> Der Zugriff auf `VelocityModuleApi.class` selbst ist unbedenklich – ein
> `Class`-Literal lädt die Klasse zwar, aber deren Methodensignaturen werden
> dabei nicht aufgelöst. Alles, was mit dem *Ergebnis* arbeitet, gehört
> trotzdem in `ProxyPart`.

## Was auf beiden Seiten gleich ist

Der ganze neutrale Teil der API funktioniert überall identisch:

| | |
|---|---|
| `context.moduleId()` | die eigene ID |
| `context.configDirectory()` | der eigene Ordner |
| `context.logger()` | das eigene Log |
| `context.platform()` | wo du bist |
| `context.registerCleanup(...)` | aufräumen |
| `context.registerCommand(...)` | eigene Commands |
| `context.network()` | Remote-Storage und Remote-Actions |
| `onLoad` / `onEnable` / `onReload` / `onDisable` | der Lebenszyklus |

Ein Modul, das nur damit auskommt, braucht keine Fallunterscheidung. Genau so
ist das `VelocityTestModule` gebaut, bis auf den Teil, der bewusst die
Proxy-API benutzt.

## Ein sinnvoller Zuschnitt

Eine typische Arbeitsteilung, am Beispiel eines Transfers:

```
Velocity                      Paper
--------                      -----
Wechsel erkennen              Spielerdaten lesen
Herkunft und Ziel kennen      Spielerdaten schreiben
Transfer koordinieren         Inventar wiederherstellen
Remote-Action erzeugen
```

Velocity hat die Bukkit-Spielerdaten nicht und darf nie so behandelt werden, als
hätte es sie. Mehr dazu unter [Netzwerk für Module](Development-Network.md).

## Installieren

Ein BOTH-Modul kommt in **beide** Ordner:

```
Paper:    plugins/MHCenter2/Modules/Jars/MyModule.jar
Velocity: plugins/MHCenter2/Modules/Jars/MyModule.jar
```

Beide Seiten laden es getrennt, mit eigener Instanz, eigenem Konfigurationsordner
und eigenem Ein-/Aus-Zustand. `/center modules disable MyModule` auf Paper
schaltet es auf dem Proxy **nicht** ab.

## Bauen

Das `pom.xml` braucht drei `provided`-Abhängigkeiten: die MHCenter2-API, die
Paper-API und die Velocity-API. Keine davon landet in deiner JAR.

## Siehe auch

* [Paper-Module](Development-Paper.md)
* [Velocity-Module](Development-Velocity.md)
* [Metadaten](Development-Metadata.md)
* [Netzwerk für Module](Development-Network.md)
