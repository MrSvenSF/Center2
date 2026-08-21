# Module installieren

Ein MHCenter2-Modul ist eine eigene JAR-Datei. Es ist **kein** Paper-Plugin und
gehört deshalb nicht nach `plugins/`.

## Wohin die JAR gehört

```
plugins/MHCenter2/Modules/Jars/MeinModul-1.0.jar
```

Auf dem Proxy genauso:

```
plugins/MHCenter2/Modules/Jars/MeinProxyModul-1.0.jar
```

Der Ordner entsteht beim ersten Start von MHCenter2.

## Modul hinzufügen

Zwei Wege:

1. **Server neu starten** – MHCenter2 liest den Ordner beim Start.
2. **Ohne Neustart:**

```
/center modules reload
```

MHCenter2 prüft dabei für jede neue JAR in dieser Reihenfolge:

1. Metadaten lesen,
2. Modul-ID prüfen,
3. doppelte ID prüfen,
4. Plattform prüfen,
5. MHCenter2-Version prüfen,
6. Minecraft-Version prüfen (nur auf Paper),
7. laden,
8. Commands registrieren,
9. aktivieren.

Danach steht das Modul in `/center modules` und im Admin-Menü.

## Was das Modul mitbringt

Braucht ein Modul eigene Dateien, legt es sie selbst unter
`Modules/Configs/<modul-id>/` an. MHCenter2 erzeugt dort nichts.

Ein Modul darf eigene Commands mitbringen. Die stehen nie in der `Commands.yml`
des Cores; wie sie konfiguriert werden, entscheidet das Modul.

## Status ansehen

```
/center modules
```

Oder im Admin-Menü über die Modulreihe. Die Bedeutung der Zustände steht unter
[Modulstatus](Modules-Status.md).

## Aktivieren und deaktivieren

```
/center modules enable <module-id>
/center modules disable <module-id>
```

oder über **Aktivieren** / **Deaktivieren** in der Modul-Detailansicht.

Beim Deaktivieren stoppt MHCenter2 das Modul, entfernt seine Commands und führt das
Aufräumen aus, das das Modul angemeldet hat. Danach stellt es keine Funktion mehr
bereit.

### Die Entscheidung bleibt erhalten

Ein abgeschaltetes Modul bleibt auch nach einem Serverneustart abgeschaltet.
MHCenter2 merkt sich das in seiner lokalen Datenbank; die Modul-JAR wird dafür nie
verändert.

Kann MHCenter2 diesen Zustand ausnahmsweise nicht speichern, gilt deine
Entscheidung trotzdem für die laufende Sitzung, und die Konsole warnt
ausdrücklich, dass ein Neustart sie möglicherweise nicht übernimmt.

## Kompatibilität

Jedes Modul gibt an, mit welchen MHCenter2-Versionen es läuft. Ein Modul für Paper
gibt zusätzlich die unterstützten Minecraft-Versionen an.

Passt die laufende Version nicht, bleibt das Modul **sichtbar**, aber
deaktiviert, mit dem Zustand `INCOMPATIBLE_CENTER` oder
`INCOMPATIBLE_MINECRAFT`. Es lässt sich dann auch nicht von Hand aktivieren; ein
Force-Enable gibt es bewusst nicht.

Das ist Absicht: eine falsche Serverversion kann zu Abstürzen und beschädigten
Zuständen führen, und neuere Versionen sind nicht automatisch kompatibel.

Welches Modul auf welche Plattform gehört, steht unter
[Paper- und Velocity-Module](Modules-Platforms.md).

## Modul aktualisieren

**Ein Modulupdate braucht einen Serverneustart.**

Sobald ein Modul in dieser Serverlaufzeit einmal Klassen geladen hat, bleibt
diese Modulidentität bis zum Neustart bestehen. Eine geänderte, ersetzte oder
gelöschte JAR wird deshalb **nicht** live übernommen; MHCenter2 meldet in der
Konsole, dass ein Neustart nötig ist.

Der Grund: Java garantiert nicht, dass alle Klassen, statischen Werte, Threads,
Tasks und Listener der alten Datei wirklich verschwunden sind. Ein
Binary-Hot-Swap wäre nicht verlässlich, deshalb gibt es ihn nicht.

Richtiges Vorgehen:

1. Server stoppen.
2. Alte Modul-JAR ersetzen.
3. Server starten.

Ein Modul, von dem noch **nie** Klassen geladen wurden – etwa ein inkompatibles –
darf dagegen neu eingelesen werden. Dort ist nichts auszutauschen.

## Modul entfernen

1. Server stoppen.
2. JAR aus `Modules/Jars/` löschen.
3. Server starten.

Der Ordner unter `Modules/Configs/<modul-id>/` bleibt erhalten. Du kannst ihn von
Hand löschen, wenn du die Daten des Moduls nicht mehr brauchst.

## Wo Fehler stehen

Im Menü steht bei einem kaputten Modul nur **Modul Error**. Die vollständige
Ursache mit Modulname, ID, Version, Lebenszyklusschritt und Stacktrace steht in
der **Serverkonsole** beziehungsweise im Logfile des Servers.
