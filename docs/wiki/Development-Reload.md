# Reload

Ein Modul darf seine Konfiguration ändern, ohne dass der Server neu startet.
MHCenter2 sagt ihm dafür Bescheid.

## `onReload()`

```java
public interface CenterModule {

    void onLoad(ModuleContext context) throws Exception;

    void onEnable() throws Exception;

    default void onReload() throws Exception {
    }

    void onDisable() throws Exception;
}
```

`onReload()` hat eine Standardimplementierung, die nichts tut. Ein Modul, das
nichts nachzulesen hat, lässt die Methode einfach weg – bestehende Module
brechen dadurch nicht.

MHCenter2 ruft sie auf **jedem laufenden Modul** auf, wenn ein Administrator
`/center reload` benutzt. Das gilt auf **PAPER**, auf **VELOCITY** und für
**BOTH**-Module, und es gilt auch dann, wenn der Reload von einem anderen Server
im Netzwerk kam.

## Was du dort tust

Alles, was von deiner eigenen Konfiguration abhängt:

* Konfigurationsdateien neu einlesen,
* Caches leeren,
* geänderte Einstellungen anwenden.

```java
public final class MyModule implements CenterModule {

    private ModuleContext context;
    private volatile String greeting = "Hallo";

    @Override
    public void onLoad(final ModuleContext context) {
        this.context = context;
    }

    @Override
    public void onEnable() {
        readConfig();
        // Der Command liest das Feld bei jedem Aufruf. Deshalb wirkt eine
        // Aenderung nach dem Reload sofort, ohne neue Registrierung.
        context.registerCommand("center hello", sender -> sender.sendMessage(greeting));
    }

    @Override
    public void onReload() {
        readConfig();
        context.logger().info("Konfiguration neu gelesen.");
    }

    @Override
    public void onDisable() {
    }

    private void readConfig() {
        // ... eigene Datei lesen und Felder setzen
    }
}
```

## Was du dort nicht tust

**Keine Commands neu registrieren.** Deine Commands bleiben registriert; MHCenter2
räumt sie nur auf, wenn das Modul gestoppt wird. Registrierst du denselben Pfad
im Reload erneut, wird er abgelehnt, weil er bereits vergeben ist.

**Keine Listener und Tasks neu anlegen**, ohne die alten vorher zu entfernen.
Sonst hast du nach drei Reloads drei Listener. Lege sie lieber einmal in
`onEnable()` an und lass sie ein Feld lesen, das `onReload()` neu setzt.

## Ein Reload lädt keine JAR neu

Das ist der wichtigste Punkt: `onReload()` ist ein **Konfigurations-Reload**, kein
Neuladen des Binärcodes. Der ClassLoader deines Moduls bleibt derselbe, die
laufende Instanz bleibt dieselbe, `onLoad` und `onEnable` laufen nicht noch
einmal.

Hast du deine Modul-JAR ausgetauscht, hilft nur ein **Serverneustart**. MHCenter2
erkennt das und schreibt es ins Log; es tut nicht so, als hätte der Reload die
neue Version geladen.

## Wenn `onReload()` fehlschlägt

Wirft deine Methode, dann weiß MHCenter2 nicht mehr, welchen Teil deiner
Konfiguration du schon angewendet hast. Ein Modul in einem unbekannten Zustand
ist schlechter als ein abgeschaltetes Modul, deshalb:

1. Der Fehler kommt mit Stacktrace ins Log, Schritt `RELOAD`.
2. `onDisable()` und dein Cleanup laufen.
3. Das Modul steht auf `ERROR` und läuft nicht mehr.

Die anderen Module sind davon nicht betroffen. Nachdem du die Ursache behoben
hast, startest du es mit `/center modules enable <id>` wieder.

## Reihenfolge im Reload

`/center reload` macht in dieser Reihenfolge:

1. alle MHCenter2-Konfigurationsdateien laden und prüfen,
2. die neue Konfiguration aktivieren (Commands, Permissions, Texte, Menüs),
3. die Remote-Einstellungen anwenden,
4. `onReload()` auf allen laufenden Modulen,
5. die Anforderung ins Netzwerk schicken.

Scheitert Schritt 1 oder 2, bleibt die alte Konfiguration aktiv und **nichts**
geht ins Netzwerk.

## Modulcommand gegen Core-Command

Wenn du in `Commands.yml` einen Core-Command oder einen Alias auf einen Pfad
legst, den ein laufendes Modul schon bedient, lehnt MHCenter2 die neue
Command-Konfiguration ab. Der Reload schlägt mit einer klaren Meldung fehl und
der letzte gültige Commandstand bleibt aktiv.

Der Grund: sonst wäre der Reload "erfolgreich" und der Modulcommand danach
kommentarlos verschwunden.

## Siehe auch

* [Netzwerk-Reload](Network-Reload.md)
* [Commands](Development-Commands.md)
* [Cleanup](Development-Cleanup.md)
* [Fehlerbehandlung](Development-Errors.md)
