# Versionskompatibilität

Ein Modul sagt selbst, mit welchen Versionen es läuft. Center2 startet es nur
innerhalb dieser Grenzen. Das ist eine Sicherheitsgrenze, kein Hinweis.

## Center2-Versionsbereich

```properties
center-min-version=1.0.0
center-max-version=1.0.0
```

Pflicht für **jedes** Modul, auf beiden Plattformen. Center2 startet das Modul
nur, wenn gilt:

```
center-min-version <= laufende Center2-Version <= center-max-version
```

Damit schützt du dein Modul gegen Änderungen an `ModuleContext`, am Lebenszyklus,
an der Command-Registrierung, am Cleanup und an den Metadaten.

Passt es nicht: Zustand `INCOMPATIBLE_CENTER`.

## Minecraft-Versionsbereich

```properties
minecraft-min-version=1.21.4
minecraft-max-version=1.21.8
```

Pflicht für `platform=PAPER` und `platform=BOTH`. Für `platform=VELOCITY` nicht
nötig, weil ein Proxy keine einzelne Minecraft-Spielversion hat.

Auf Paper vergleicht Center2 die **echte Minecraft-Version** des Servers. Passt
sie nicht: Zustand `INCOMPATIBLE_MINECRAFT`.

Auf Velocity wird dieser Bereich nicht geprüft, auch bei einem `BOTH`-Modul
nicht.

## Grenzen gehören dazu

Der Bereich ist beidseitig inklusiv.

Beispiel `minecraft-min-version=1.21.4`, `minecraft-max-version=1.21.8`:

| Server | Ergebnis |
|--------|----------|
| 1.21.3 | `INCOMPATIBLE_MINECRAFT` |
| 1.21.4 | erlaubt |
| 1.21.6 | erlaubt |
| 1.21.8 | erlaubt |
| 1.21.9 | `INCOMPATIBLE_MINECRAFT` |

Beispiel `center-min-version=0.2.0`, `center-max-version=0.2.9`:

| Center2 | Ergebnis |
|---------|----------|
| 0.1.9 | `INCOMPATIBLE_CENTER` |
| 0.2.0 | erlaubt |
| 0.2.9 | erlaubt |
| 0.3.0 | `INCOMPATIBLE_CENTER` |

## Verglichen wird nach Zahlen

Center2 vergleicht Versionen **semantisch**, nie als Text:

* `1.21.9` ist **älter** als `1.21.11`. Ein Textvergleich würde das falsch
  herum sehen.
* Eine fehlende Stelle zählt als 0: `1.21` und `1.21.0` sind dieselbe Version.
* Alles hinter den Zahlen ist ein Buildname und wird abgeschnitten:
  `1.21.11-R0.1-SNAPSHOT` gilt als `1.21.11`.

Ungültige Angaben wie `latest` oder `newest` lehnen die Metadaten ab.

## Keine Annahme über neuere Versionen

Center2 nimmt **nicht** an, dass ein Modul auf einer neueren Version läuft. Auch
eine neuere Minecraft- oder Center2-Version kann Breaking Changes enthalten.

Wenn du weißt, dass dein Modul eine ganze Reihe unterstützt, sag es ausdrücklich:

```properties
center-min-version=1.0.0
center-max-version=1.0.0
```

## Was ein inkompatibles Modul erlebt

* Es bleibt **sichtbar** in `/center modules` und im Admin-Menü. Es verschwindet
  nicht, sonst würde der Administrator nur „mein Modul ist weg" sehen.
* Es wird nicht geladen: keine Klasse davon kommt in die JVM.
* Es lässt sich nicht von Hand aktivieren. Ein Force-Enable gibt es nicht.
* Die Konsole nennt den geforderten Bereich und die laufende Version.
* Eine normale Inkompatibilität ist kein Fehler und erzeugt keinen Stacktrace.

## Sonderfall Paper ohne erkennbare Minecraft-Version

Kann Center2 auf Paper die Minecraft-Version nicht bestimmen, wird **kein** Modul
geladen, und die Konsole erklärt warum. „Unbekannt" heißt dort „Kompatibilität
nicht bestätigt", nie „keine Prüfung nötig".

## Deine eigene Modulversion

Das Feld `version` ist nur eine Information für Anzeige und Logs. Center2 leitet
daraus nichts ab und vergleicht es mit nichts.
