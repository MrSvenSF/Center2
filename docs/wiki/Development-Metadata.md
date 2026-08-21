# Modul-Metadaten

Jede Modul-JAR enthält im Wurzelverzeichnis die Datei
`center-module.properties`. Ohne sie ist die JAR kein Center2-Modul und wird mit
einer Meldung übersprungen.

Bei einem Maven-Projekt liegt sie unter
`src/main/resources/center-module.properties`.

## Encoding

Die Datei wird als **UTF-8** gelesen. Name und Autor dürfen also beliebige
Zeichen enthalten:

```properties
name=Überwachung
author=Müller
```

Speichere die Datei entsprechend als UTF-8 (ohne BOM). Das ist eine bewusste
Abweichung vom Java-Standard für `.properties`-Dateien, der ISO-8859-1 wäre.

## Alle Felder

| Feld | Pflicht | Bedeutung |
|------|---------|-----------|
| `id` | immer | Kurzname, gleichzeitig Name des eigenen Konfigurationsordners |
| `name` | immer | sichtbarer Name in Menü, Übersicht und Logs |
| `version` | immer | Version des Moduls |
| `author` | immer | wer das Modul geschrieben hat |
| `main` | immer | vollqualifizierter Name der Hauptklasse |
| `platform` | immer | `PAPER`, `VELOCITY` oder `BOTH` |
| `center-min-version` | immer | älteste unterstützte Center2-Version |
| `center-max-version` | immer | neueste unterstützte Center2-Version |
| `minecraft-min-version` | bei `PAPER` und `BOTH` | älteste unterstützte Minecraft-Version |
| `minecraft-max-version` | bei `PAPER` und `BOTH` | neueste unterstützte Minecraft-Version |

Andere Felder kennt Center2 nicht.

## id

* Erlaubt sind `a-z`, `A-Z`, `0-9`, `_` und `-`, höchstens 64 Zeichen.
* Keine Punkte, keine Leerzeichen, keine Pfadtrenner: die ID ist auch ein
  Ordnername unter `Modules/Configs/`.
* Groß- und Kleinschreibung zählt als **dieselbe** ID. Zwei Module mit `Demo` und
  `DEMO` sind ein Konflikt.
* Beanspruchen zwei JARs dieselbe ID, wird die neue abgelehnt; ein bereits
  installiertes Modul bleibt unverändert.

## main

Vollqualifizierter Klassenname, zum Beispiel `com.example.meinmodul.MeinModul`.
Die Klasse muss

* `net.managerhub.center.api.CenterModule` implementieren und
* einen öffentlichen Konstruktor ohne Argumente haben.

Sonst bekommt das Modul den Zustand `ERROR`, mit der genauen Ursache in der
Konsole.

## platform

| Wert | Läuft auf | Minecraft-Bereich |
|------|-----------|-------------------|
| `PAPER` | nur Paper | Pflicht |
| `VELOCITY` | nur Velocity | nicht nötig |
| `BOTH` | Paper und Velocity | Pflicht (auf Velocity ignoriert) |

Groß- und Kleinschreibung ist egal, `paper` funktioniert genauso.

Details: [Paper- und Velocity-Module](Modules-Platforms.md).

## Versionsbereiche

Beide Grenzen gehören zum Bereich. Verglichen wird nach Zahlen, nicht als Text.
Vollständig erklärt unter [Versionskompatibilität](Development-Versioning.md).

## Gültige Beispiele

Ein Paper-Modul:

```properties
id=MeinModul
name=Mein Modul
version=1.0.0
author=Dein Name
main=com.example.meinmodul.MeinModul
platform=PAPER
center-min-version=1.0.0
center-max-version=1.0.0
minecraft-min-version=1.21.4
minecraft-max-version=1.21.11
```

Ein Proxy-Modul, ganz ohne Minecraft-Bereich:

```properties
id=MeinProxyModul
name=Mein Proxy Modul
version=1.0.0
author=Dein Name
main=com.example.proxy.MeinProxyModul
platform=VELOCITY
center-min-version=1.0.0
center-max-version=1.0.0
```

Ein Modul für beide Seiten:

```properties
id=MeinBothModul
name=Mein BOTH Modul
version=1.0.0
author=Müller
main=com.example.both.MeinBothModul
platform=BOTH
center-min-version=1.0.0
center-max-version=1.0.0
minecraft-min-version=1.21.4
minecraft-max-version=1.21.11
```

## Was Center2 beim Lesen prüft

1. Alle Pflichtfelder vorhanden und nicht leer.
2. `id` erfüllt das erlaubte Zeichenmuster.
3. `main` sieht wie ein Klassenname aus.
4. `platform` ist einer der drei bekannten Werte.
5. Die Versionsangaben sind Zahlen, und das Minimum ist nicht neuer als das
   Maximum.

Jede Verletzung wird mit Dateiname, Feld und Wert gemeldet, und die JAR wird
übersprungen.
