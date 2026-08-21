# Installation

## Voraussetzungen

| Sache | Anforderung |
|-------|-------------|
| Java | **25** oder neuer |
| Paper | **1.21.11** (`api-version: '1.21.11'`) |
| Velocity | **3.5.x** |
| Buildsystem (nur zum Selberbauen) | Maven |

MHCenter2 braucht sonst nichts: kein Datenbankserver, kein Redis, kein
Permission-Plugin und keine Internetverbindung. Ein Permission-Plugin ist nur
nötig, wenn Spieler (also nicht die Konsole) Adminfunktionen benutzen sollen.

## MHCenter2 herunterladen

Es gibt genau **eine** JAR für beide Plattformen: `MHCenter2-<version>.jar`.

Lade sie aus dem GitHub-Releases-Bereich dieses Repositories herunter. Solange
dort noch kein Release veröffentlicht ist, baust du sie selbst:

```bash
cd MHCenter2
mvn clean package
```

Ergebnis: `target/MHCenter2-<version>.jar`

## Wechsel von der Beta

Die öffentliche Beta verwendete noch den Namen `Center2`. Wer bestehende
Konfigurationen oder Modulzustände übernehmen möchte, stoppt Paper und Velocity
und benennt vor dem ersten Start der stabilen Version den Ordner
`plugins/Center2/` in `plugins/MHCenter2/` um. Danach wird ausschließlich
`MHCenter2-1.0.1.jar` verwendet; die alte Beta-JAR muss aus `plugins/`
entfernt bleiben.

## Paper-Installation

1. Server stoppen.
2. `MHCenter2-<version>.jar` nach `plugins/` kopieren.
3. Server starten.

MHCenter2 legt beim ersten Start diese Struktur an:

```
plugins/MHCenter2/
├── MainConfig.yml
├── Commands.yml
├── Permissions.yml
├── Language/
│   ├── DE.yml
│   └── EN.yml
├── Menus/
│   ├── CenterInfo.yml
│   ├── CenterAdmin.yml
│   └── CenterServerStatus.yml
├── DB/
│   └── Center.db
└── Modules/
    ├── Jars/      (hier kommen Modul-JARs hinein)
    └── Configs/   (hier legen Module ihre eigenen Dateien an)
```

## Velocity-Installation

1. Proxy stoppen.
2. **Dieselbe** `MHCenter2-<version>.jar` nach `plugins/` kopieren.
3. Proxy starten.

Auf dem Proxy entsteht:

```
plugins/MHCenter2/
├── MainConfig.yml
├── Language/
│   ├── DE.yml
│   └── EN.yml
├── DB/
│   └── Center.db
└── Modules/
    ├── Jars/
    └── Configs/
```

Velocity hat kein Menü und keine konfigurierbaren Commands, deshalb entstehen
dort weder `Commands.yml` noch `Permissions.yml` noch `Menus/`. Die
Modulverwaltungscommands gibt es trotzdem, siehe
[Getting Started](Getting-Started.md).

Der Datenordner heißt auf beiden Seiten `MHCenter2`, obwohl die Velocity-Plugin-ID
`mhcenter2` lauten muss.

## Gemeinsames Netzwerk

MHCenter2 auf Paper und MHCenter2 auf Velocity reden über Plugin-Messaging auf dem
Kanal `mhcenter2:network` miteinander. Dafür ist nichts zu konfigurieren; es
funktioniert, sobald beide Seiten MHCenter2 haben und ein Spieler verbunden ist.

Damit die Netzwerkübersicht im Admin-Menü etwas anzeigt, muss MHCenter2 also auf
dem Proxy **und** auf den Backend-Servern installiert sein. MHCenter2 funktioniert
aber auch allein auf einem einzelnen Paper-Server.

## Erster Start prüfen

In der Serverkonsole steht bei Erfolg:

```
[MHCenter2] MHCenter2 <version> auf Paper aktiviert. Registrierte Commands: 1.
[MHCenter2] MHCenter2 prüft Module gegen Minecraft 1.21.11.
[MHCenter2] 0 Module installiert, 0 davon aktiv.
```

Auf dem Proxy:

```
[mhcenter2]: MHCenter2 <version> auf Velocity aktiviert.
[mhcenter2]: 0 Module installiert, 0 davon aktiv.
```

Zusätzlich:

* `/center` in der Konsole zeigt die Commandübersicht.
* `plugins/MHCenter2/` existiert mit den oben genannten Dateien.

Startet MHCenter2 nicht, steht der Grund als konkrete Meldung in der Konsole, siehe
[Troubleshooting](Troubleshooting.md).

## Update

1. Server stoppen.
2. Alte `MHCenter2-<alt>.jar` aus `plugins/` löschen, neue hineinlegen.
3. Server starten.

Konfigurationsdateien müssen **nicht** gelöscht werden. MHCenter2 ergänzt fehlende
neue Standardeinträge selbst und lässt bestehende Werte unverändert, siehe
[Configuration](Configuration.md).

Fehlt in einer älteren `MainConfig.yml` der Abschnitt `remote`, ergänzt MHCenter2
ihn mit `enabled: false`: Ein Update versucht nie von selbst, eine Datenbank zu
benutzen, die niemand eingerichtet hat. Beim Wechsel auf 1.0.1 bleiben bereits
vorhandene Einstellungen erhalten.

## Optional: die gemeinsame MariaDB

MHCenter2 braucht sie **nicht**. Sie fügt genau eine Sache hinzu: einen
MHCenter2-Knoten erreichen, auf dem gerade niemand online ist – zum Beispiel für
einen netzwerkweiten `/center reload` auf einen leeren Server.

Kurzfassung:

1. Datenbank und Benutzer anlegen.
2. Auf jedem Knoten in `MainConfig.yml` den Abschnitt `remote` ausfüllen,
   **jeder Knoten mit einer eigenen `server-id`**.
3. Neu starten.

Der ausführliche Weg mit SQL, Rechten und Fehlersuche steht unter
[Remote-Datenbank](Network-Remote.md).

Ohne diesen Schritt läuft alles wie bisher: lokale Configs, lokale Datenbank,
lokale Module, Menüs, Commands, und das Plugin-Messaging zwischen Paper und
Velocity.
