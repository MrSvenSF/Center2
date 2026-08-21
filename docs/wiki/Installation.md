# Installation

## Voraussetzungen

| Sache | Anforderung |
|-------|-------------|
| Java | **25** oder neuer |
| Paper | **1.21.11** (`api-version: '1.21.11'`) |
| Velocity | **3.5.x** |
| Buildsystem (nur zum Selberbauen) | Maven |

Center2 braucht sonst nichts: kein Datenbankserver, kein Redis, kein
Permission-Plugin und keine Internetverbindung. Ein Permission-Plugin ist nur
nötig, wenn Spieler (also nicht die Konsole) Adminfunktionen benutzen sollen.

## Center2 herunterladen

Es gibt genau **eine** JAR für beide Plattformen: `Center2-<version>.jar`.

Lade sie aus dem GitHub-Releases-Bereich dieses Repositories herunter. Solange
dort noch kein Release veröffentlicht ist, baust du sie selbst:

```bash
cd Center2
mvn clean package
```

Ergebnis: `target/Center2-<version>.jar`

## Paper-Installation

1. Server stoppen.
2. `Center2-<version>.jar` nach `plugins/` kopieren.
3. Server starten.

Center2 legt beim ersten Start diese Struktur an:

```
plugins/Center2/
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
2. **Dieselbe** `Center2-<version>.jar` nach `plugins/` kopieren.
3. Proxy starten.

Auf dem Proxy entsteht:

```
plugins/Center2/
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

Der Datenordner heißt auf beiden Seiten `Center2`, obwohl die Velocity-Plugin-ID
`center2` lauten muss.

## Gemeinsames Netzwerk

Center2 auf Paper und Center2 auf Velocity reden über Plugin-Messaging auf dem
Kanal `center2:network` miteinander. Dafür ist nichts zu konfigurieren; es
funktioniert, sobald beide Seiten Center2 haben und ein Spieler verbunden ist.

Damit die Netzwerkübersicht im Admin-Menü etwas anzeigt, muss Center2 also auf
dem Proxy **und** auf den Backend-Servern installiert sein. Center2 funktioniert
aber auch allein auf einem einzelnen Paper-Server.

## Erster Start prüfen

In der Serverkonsole steht bei Erfolg:

```
[Center2] Center2 <version> auf Paper aktiviert. Registrierte Commands: 1.
[Center2] Center2 prüft Module gegen Minecraft 1.21.11.
[Center2] 0 Module installiert, 0 davon aktiv.
```

Auf dem Proxy:

```
[center2]: Center2 <version> auf Velocity aktiviert.
[center2]: 0 Module installiert, 0 davon aktiv.
```

Zusätzlich:

* `/center` in der Konsole zeigt die Commandübersicht.
* `plugins/Center2/` existiert mit den oben genannten Dateien.

Startet Center2 nicht, steht der Grund als konkrete Meldung in der Konsole, siehe
[Troubleshooting](Troubleshooting.md).

## Update

1. Server stoppen.
2. Alte `Center2-<alt>.jar` aus `plugins/` löschen, neue hineinlegen.
3. Server starten.

Konfigurationsdateien müssen **nicht** gelöscht werden. Center2 ergänzt fehlende
neue Standardeinträge selbst und lässt bestehende Werte unverändert, siehe
[Configuration](Configuration.md).

Beim Update von 0.3.0 auf 0.4.0 kommt der Abschnitt `remote` in
`MainConfig.yml` dazu. Er wird mit `enabled: false` ergänzt: ein Update versucht
nie von selbst, eine Datenbank zu benutzen, die niemand eingerichtet hat.

## Optional: die gemeinsame MariaDB

Center2 braucht sie **nicht**. Sie fügt genau eine Sache hinzu: einen
Center2-Knoten erreichen, auf dem gerade niemand online ist – zum Beispiel für
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
