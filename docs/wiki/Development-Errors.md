# Fehlerbehandlung

## Fehlerisolation

Ein Modul, das eine Ausnahme wirft, reißt weder MHCenter2 noch ein anderes Modul
mit. Es bekommt den Zustand `ERROR`, läuft nicht weiter, und alles andere läuft
normal weiter.

Das gilt in jedem Schritt: beim Erzeugen der Hauptklasse, in `onLoad`, in
`onEnable`, in `onDisable` und im Cleanup.

## Fehler in onLoad

```java
@Override
public void onLoad(final ModuleContext context) throws Exception {
    this.context = context;
    Files.createDirectories(context.configDirectory());   // kann werfen
}
```

Wirft `onLoad`:

1. Das Modul bekommt `ERROR`.
2. Der vollständige Fehler wird geloggt.
3. Das bereits registrierte Cleanup läuft.
4. Die Modulinstanz wird verworfen.
5. `onEnable` wird nicht mehr aufgerufen.

## Fehler in onEnable

Genauso, mit dem Schritt `ENABLE` im Log. Wichtig: Alles, was du **vorher** schon
registriert hast, wird über das Cleanup wieder entfernt, siehe
[Cleanup](Development-Cleanup.md).

`onDisable` wird dabei **nicht** aufgerufen: Das Modul hat den aktiven Zustand nie
erreicht. Verlass dich für Ressourcen deshalb nicht auf `onDisable`, sondern melde
Cleanup an.

## Fehler in onDisable

```java
@Override
public void onDisable() {
    speichereDaten();   // kann werfen
}
```

Wirft `onDisable`:

1. Der Fehler wird mit dem Schritt `DISABLE` geloggt.
2. Das Cleanup läuft **trotzdem**.
3. Das Modul endet in `ERROR`, läuft aber auf keinen Fall weiter.
4. Alle anderen Module werden ganz normal weiter gestoppt.

## Fehler im Cleanup

Eine fehlschlagende Aufräumaktion stoppt die übrigen nicht. Sie wird zusätzlich
mit dem Schritt `CLEANUP` geloggt und ersetzt nie den ursprünglichen Fehler.

## Was in der Konsole steht

```
[MHCenter2] Modul 'Mein Modul' (ID MeinModul, Version 1.0.1) ist im Schritt ENABLE
fehlgeschlagen: IllegalStateException: Datenbank nicht erreichbar
java.lang.IllegalStateException: Datenbank nicht erreichbar
        at com.example.meinmodul.MeinModul.onEnable(MeinModul.java:42)
        ...
```

Geloggt werden Modulname, Modul-ID, Modulversion, der Schritt, eine verständliche
Überschrift, die Ausnahme und der vollständige Stacktrace.

Mögliche Schritte: `LOAD`, `ENABLE`, `RELOAD`, `DISABLE`,
`COMMAND_REGISTRATION`, `CLEANUP`.

## Fehler in onReload

Wirft `onReload()`, weiß MHCenter2 nicht mehr, welchen Teil deiner Konfiguration du
schon angewendet hast. Ein Modul in einem unbekannten Zustand ist schlechter als
ein abgeschaltetes, deshalb:

1. Der Fehler wird mit dem Schritt `RELOAD` geloggt.
2. `onDisable()` und dein Cleanup laufen.
3. Das Modul steht auf `ERROR` und läuft nicht mehr.

Die anderen Module sind davon nicht betroffen und bleiben laufen. Nach der
Korrektur startest du es mit `/center modules enable <id>` wieder. Siehe
[Reload](Development-Reload.md).

## Was im Menü steht

Nur:

```
Modul Error
```

Keine Stacktraces, keine Exception-Namen, keine Java-Klassennamen, keine
Dateipfade. Ein Inventar ist zum Verwalten da, nicht zum Debuggen; außerdem sind
interne Details für normale Serveradmins wenig hilfreich und teils sensibel.

Die Konsole enthält die technische Wahrheit.

## Erneutes Aktivieren

Ein Modul in `ERROR` lässt sich erneut aktivieren. Das ist ein kontrollierter
neuer Versuch mit einer frischen Instanz. Tritt derselbe Fehler wieder auf, bleibt
es bei `ERROR`, und der Fehler wird erneut sauber geloggt. MHCenter2 wiederholt das
nicht von allein.

## Empfehlungen für Modulautoren

**Nicht verschlucken.** Ein leerer `catch`-Block versteckt genau die Information,
die der Serverbetreiber braucht:

```java
// so nicht
try {
    riskant();
} catch (Exception ignored) {
}
```

Besser: entweder weiterwerfen, damit MHCenter2 das Modul sauber in `ERROR` schickt,

```java
@Override
public void onEnable() throws Exception {
    riskant();
}
```

oder bewusst behandeln und protokollieren:

```java
try {
    optionalerZusatz();
} catch (Exception failure) {
    context.logger().error("Zusatzfunktion konnte nicht gestartet werden.", failure);
}
```

Faustregel:

* **Das Modul kann ohne diesen Teil nicht sinnvoll arbeiten** → Ausnahme
  weiterwerfen, MHCenter2 setzt `ERROR`.
* **Der Teil ist optional** → fangen, mit `logger().error(...)` melden und
  weiterlaufen.

Nutze für eigene Meldungen `context.logger()`:

| Methode | Wofür |
|---------|-------|
| `info(String)` | normale Information |
| `warn(String)` | etwas, das ein Administrator ansehen sollte |
| `error(String, Throwable)` | Fehler mit Ursache; der Stacktrace landet im Log |

MHCenter2 stellt jeder Zeile automatisch `[<modul-id>]` voran.

## Ein Modul entscheidet nicht selbst, dass es aktiv ist

MHCenter2 kontrolliert Laden, Aktivieren, Deaktivieren und den Fehlerzustand. Melde
also nicht in `onEnable` „erfolgreich aktiviert", bevor die Methode durch ist:
wirft sie danach noch, ist das Modul in `ERROR`, und deine Meldung war falsch.
