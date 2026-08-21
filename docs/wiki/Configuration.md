# Konfiguration

Alle MHCenter2-Dateien liegen unter `plugins/MHCenter2/`. Sie verwenden PascalCase.

## Grundregeln

* Eine bestehende Datei wird **nie** überschrieben. Deine Werte bleiben, wie du
  sie geschrieben hast.
* Fehlt MHCenter2 nach einem Update ein neuer Standardeintrag, wird **nur dieser
  Eintrag** ergänzt und `config-version` angehoben. Was du geändert hast, bleibt.
  Die Konsole schreibt genau auf, was ergänzt wurde.
* Eine Datei, die eine **neuere** `config-version` meldet als dieses MHCenter2
  kennt, wird nicht angefasst.
* Eine völlig leere Datei gilt als kaputter Rest eines Schreibvorgangs und wird
  aus dem Standard wiederhergestellt.
* Ist ein Wert ungültig, wird der komplette Reload abgelehnt und die zuletzt
  funktionierende Konfiguration bleibt aktiv.

`config-version` ist die Schemaversion der Datei, aktuell **3**.

## MainConfig.yml

Paper und Velocity.

```yaml
config-version: 3

language: DE

menus:
  center-info:
    enabled: true

remote:
  enabled: false
  server-id: ""
  database:
    host: "127.0.0.1"
    port: 3306
    database: "mhcenter2"
    username: "mhcenter2"
    password: ""
    ssl: true
  polling:
    interval-ms: 1000
    action-ttl-seconds: 60
  heartbeat:
    interval-seconds: 10
```

| Eintrag | Bedeutung |
|---------|-----------|
| `language` | Sprache aus `Language/`. Unterstützt: `DE`, `EN`. |
| `menus.center-info.enabled` | schaltet das Center-Info-Menü an und aus |
| `remote.enabled` | ob dieser Knoten die gemeinsame MariaDB benutzt |
| `remote.server-id` | der Name dieses Knotens im Netzwerk, von dir vergeben |
| `remote.database.*` | wie die MariaDB erreicht wird |
| `remote.polling.interval-ms` | wie oft nach neuen Netzwerkaktionen gesehen wird (250–60000) |
| `remote.polling.action-ttl-seconds` | wie lange eine Netzwerkaktion gültig bleibt (5–3600) |
| `remote.heartbeat.interval-seconds` | wie oft dieser Knoten meldet, dass er lebt (1–300) |

Die Velocity-Variante enthält `config-version`, `language` und denselben
`remote`-Abschnitt – nur die Menüs fehlen.

### Zum remote-Abschnitt

Er wird immer vollständig gelesen, auch mit `enabled: false`: ein Tippfehler soll
nicht erst auffallen, wenn jemand das Remote-System einschaltet. Ob die Werte
zueinander passen, wird erst geprüft, wenn er eingeschaltet ist.

Mit `enabled: true` müssen `server-id`, `host`, `database` und `username` gefüllt
sein. Fehlt etwas, startet das Remote-System **nicht**, sagt im Log warum, und
MHCenter2 läuft lokal ganz normal weiter.

Mit `enabled: false` wird nie eine Verbindung aufgebaut – kein Versuch, kein
Timeout, keine Logzeile. Bei einem Update bleibt `enabled` auf `false`: ein
Update darf nicht plötzlich eine Datenbank benutzen wollen, die niemand
eingerichtet hat.

Alles Weitere unter [Remote-Datenbank](Network-Remote.md).

Steht `menus.center-info.enabled` auf `false`, lässt sich das Menü nicht mehr
öffnen, der zugehörige Command wird nicht registriert und MHCenter2 trägt in
`Commands.yml` beim Command `center-info` automatisch `enabled: false` ein.
Umgekehrt passiert das nie: `enabled: true` wird niemals automatisch geschrieben.

## Commands.yml

Nur Paper.

```yaml
config-version: 3

commands:
  center-info:
    enabled: true
    command: "center info"
    aliases:

  modules-reload:
    enabled: true
    command: "center modules reload"
    aliases:

  modules-enable:
    enabled: true
    command: "center modules enable"
    aliases:

  modules-disable:
    enabled: true
    command: "center modules disable"
    aliases:
```

Konfigurierbar sind **ausschließlich**:

| Eintrag | Bedeutung |
|---------|-----------|
| `enabled` | ob der Command überhaupt existiert |
| `command` | der vollständige Commandpfad, ohne führenden Slash |
| `aliases` | optionale Liste weiterer vollständiger Pfade |

**Was ein Command tut, steht fest im Java-Code** und hängt am internen Schlüssel
(`center-info`, `modules-reload`, …). Es gibt bewusst kein `action:`, `type:`,
`script:` oder `handler:`; Commands sind nicht frei programmierbar.

Weitere Regeln:

* Ein Pfad darf mehrteilig sein. Der erste Teil ist der Command, der auf dem
  Server registriert wird, jeder weitere Teil ist ein festes Argument.
* Groß- und Kleinschreibung gilt als derselbe Pfad.
* Ein doppelt vergebener Pfad lehnt den Reload ab.
* `aliases` darf fehlen, leer bleiben oder mehrere Pfade enthalten.

Nicht in dieser Datei stehen die festen Systemcommands `/center reload` und die
Modulübersicht `/center modules`. Ihre Pfade sind geschützt: eine Konfiguration,
die einen davon belegen würde, wird abgelehnt.

## Permissions.yml

Nur Paper. Siehe [Permissions](Permissions.md) für die vollständige Erklärung.

```yaml
config-version: 3

permissions:
  admin-all:
    permission: "center.admin.*"
    op: false

  admin:
    permission: "center.admin"
    op: false
  # ... reload, modules, modules-reload, modules-enable, modules-disable
```

Pro Eintrag gibt es genau zwei Werte: die Permission-Node und die OP-Regel.

## Language/DE.yml und Language/EN.yml

Paper und Velocity.

Beide Dateien besitzen immer exakt dieselben Schlüssel. Fehlt ein Text, ist er
leer oder ist ein unbekannter Schlüssel vorhanden, wird der komplette Reload
abgelehnt.

* Chat- und Menütexte verwenden Adventure MiniMessage (`<green>`, `<white>`, …).
* Logtexte sind reiner Text.
* Platzhalter werden als `{name}` geschrieben und von MHCenter2 eingesetzt.
* Meldungen, die MHCenter2 schreibt, **bevor** eine Konfiguration gelesen werden
  konnte, sind immer englisch. Dazu gehören Startfehler und die Meldungen der
  Konfigurationsergänzung.

Technische Fehlermeldungen und Stacktraces stehen **nie** in den Sprachdateien.

## Menus/

Nur Paper: `CenterInfo.yml`, `CenterAdmin.yml`, `CenterServerStatus.yml`.

Konfigurierbar sind Titel, Zeilenanzahl, Hintergrund sowie Slot und Material der
Einträge. Die sichtbaren Namen kommen aus den Sprachdateien; einzige Ausnahme ist
der Admin-Button, der seinen Namen direkt in `Menus/CenterInfo.yml` trägt.

Ein Slot darf nur einmal vergeben sein, das Material muss existieren und als Item
verwendbar sein.

In `Menus/CenterAdmin.yml` ist der Eintrag `modules` **kein Knopf**, sondern der
Anker der Modulreihe: MHCenter2 benutzt die ganze Inventarreihe, zu der dieser Slot
gehört. Slot 31 bedeutet also die Slots 27 bis 35 und damit bis zu neun Module
direkt im Admin-Menü.

## Modules/Configs/

Paper und Velocity.

Hier legt **jedes Modul** seine eigenen Dateien an, in einem Ordner mit seiner
Modul-ID. MHCenter2 erzeugt dort nichts und liest dort nichts: Format und Inhalt
bestimmt allein das Modul.

Beispiel für das mitgelieferte TestModule:

```
Modules/Configs/TestModule/
├── MainConfig.yml
└── Commands.yml
```

## DB/Center.db

Paper und Velocity, jeweils eine eigene Datei. SQLite, immer aktiv, nicht
abschaltbar. MHCenter2 speichert dort die Schemaversion und welche Module ein
Administrator abgeschaltet hat. Diese Datei bearbeitet man nicht von Hand.
