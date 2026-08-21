# Erste Schritte

Diese Seite richtet sich an Serveradministratoren. Java-Kenntnisse sind nicht
nötig.

## Center-Info-Menü

```
/center info
```

Öffnet auf Paper das Center-Info-Menü. Es zeigt den Ersteller, die Organisation
und, wenn du die Adminberechtigung hast, einen Knopf ins Admin-Menü.

Der Command ist frei konfigurierbar und lässt sich in `Commands.yml` umbenennen
oder abschalten.

## Admin-Menü

Der Admin-Knopf im Info-Menü öffnet das Admin-Menü. Er wird nur gesetzt, wenn du
die Adminberechtigung besitzt. Ohne sie ist er weder sichtbar noch anklickbar.

Im Admin-Menü findest du:

* **Status** – Version, Plattform, Minecraft-Version und Laufzeit,
* **Serverstatus** – die Center2-Instanzen im Netzwerk,
* **Modulreihe** – die installierten Module direkt als eigene Inventarreihe,
* **Zurück**.

Ein Klick auf ein Modul öffnet dessen Detailansicht mit Name, ID, Version, Autor,
Zustand, Plattform und den unterstützten Versionsbereichen, dazu **Aktivieren**,
**Deaktivieren** und **Zurück**.

## Konfiguration neu laden

```
/center reload
```

Liest alle Konfigurationsdateien neu ein und sagt jedem laufenden Modul Bescheid.
Der Reload ist transaktional: ist irgendetwas ungültig, wird **nichts**
übernommen und die zuletzt funktionierende Konfiguration bleibt aktiv. Die
Fehlermeldung nennt Datei, Pfad und Wert.

Danach geht die Anforderung ins **ganze Center2-Netzwerk**: der Proxy und die
anderen Paper-Server laden ihre Center2-Instanz ebenfalls neu. Center2 meldet dir
pro Knoten, was wirklich passiert ist – und behauptet nie „erfolgreich", wenn es
keine Bestätigung gibt.

```
--- Center2 Netzwerk-Reload ---
- lobby: erfolgreich
- velocity: erfolgreich
- survival: noch offen
```

Neu geladen wird ausschließlich Center2, nicht Paper, nicht Velocity und keine
anderen Plugins. Eine ausgetauschte JAR lädt der Reload **nicht** – dafür braucht
es einen Serverneustart.

Alles dazu unter [Netzwerk-Reload](Network-Reload.md).

`/center reload` ist fest eingebaut, steht nicht in `Commands.yml` und lässt sich
nicht umbenennen.

## Module ansehen

```
/center modules
```

Listet jedes erkannte Modul mit Name, ID, Version und Zustand:

```
--- Center2 Module ---
Center2 TestModule (TestModule) Version 0.4.0 - Aktiviert
```

Die möglichen Zustände erklärt [Modulstatus](Modules-Status.md).

## Module aktivieren und deaktivieren

```
/center modules enable <module-id>
/center modules disable <module-id>
```

Die Entscheidung bleibt über einen Serverneustart hinweg erhalten: ein bewusst
abgeschaltetes Modul startet nach dem Neustart nicht wieder von allein.

Dasselbe geht über die Knöpfe in der Modul-Detailansicht des Admin-Menüs.

Ein Modul, das nicht zur laufenden Center2- oder Minecraft-Version passt, lässt
sich **nicht** aktivieren, auch nicht erzwungen. Ein Force-Enable gibt es
bewusst nicht.

## Modulordner neu einlesen

```
/center modules reload
```

Liest `Modules/Jars` erneut. Eine **neue** Modul-JAR wird dabei erkannt, geprüft,
geladen und gestartet, ohne Serverneustart.

Eine bereits geladene JAR wird nicht ausgetauscht. Für ein Modulupdate ist immer
ein Serverneustart nötig, siehe
[Module installieren](Modules-Installation.md).

## Auf Velocity

Auf dem Proxy gibt es dieselben vier Modulcommands:

```
/center modules
/center modules reload
/center modules enable <module-id>
/center modules disable <module-id>
```

Es gibt dort kein Menü und kein `/center info`.

## Berechtigungen

Alle Adminfunktionen brauchen Berechtigungen. **OP allein reicht nicht.** Welche
Berechtigung wofür gilt, steht unter [Permissions](Permissions.md).

Die Serverkonsole darf immer alles. Zum Ausprobieren kannst du jeden Command also
direkt in der Konsole eingeben.
