# Center2

Center2 ist ein gemeinsamer Minecraft-Server-Core für **Paper 1.21.11** und
**Velocity 3.5.x**. Dieselbe JAR läuft auf beiden Plattformen und stellt
Konfiguration, Administration, Netzwerkkommunikation und eine öffentliche
Modul-API bereit.

> **Aktueller Entwicklungsstand:** 0.4.0 · Java 25 · Maven
>
> **Transparenzhinweis:** Center2 wurde mit Unterstützung künstlicher
> Intelligenz entwickelt. Architektur, Auswahl, Prüfung, Tests und
> Veröffentlichung liegen in der Verantwortung des Projektinhabers.

## Funktionen

- gemeinsame Core-JAR für Paper und Velocity
- `/center info`, `/center reload` und Modulverwaltung
- konfigurierbare Commands, Permissions, Menüs sowie deutsche und englische Texte
- lokale SQLite-Datenhaltung für Center2-Zustände
- optionale MariaDB für zuverlässige netzwerkweite Aktionen und kurzlebige
  gemeinsame Moduldaten – auch ohne Spieler
- Plugin Messaging als Fallback für Netzwerk-Reloads und Modul-Actions
- externe Module für `PAPER`, `VELOCITY` oder `BOTH`
- Versionsprüfung für Center2 und bei Paper zusätzlich für Minecraft
- transaktionale Reloads und automatische Config-Migrationen

Center2 ist kein Webdienst und stellt keine REST- oder HTTP-API bereit. Mit
„API“ ist die Java-Schnittstelle gemeint, gegen die externe Center2-Module
gebaut werden.

## Installation

Voraussetzungen:

- Java 25
- Paper 1.21.11 und/oder Velocity 3.5.x
- optional eine gemeinsame MariaDB

Installation auf Paper und Velocity:

1. `Center2-0.4.0.jar` in den jeweiligen Ordner `plugins/` legen.
2. Server beziehungsweise Proxy einmal starten.
3. Die erzeugte Konfiguration unter `plugins/Center2/` prüfen.
4. Nach Änderungen `/center reload` verwenden oder die Instanz neu starten.

Die MariaDB ist standardmäßig deaktiviert. Ohne sie funktionieren alle lokalen
Core-Funktionen weiter. Netzwerkfunktionen verwenden dann Plugin Messaging und
benötigen dafür eine Spielerverbindung.

## Netzwerk und Datenbanken

Center2 trennt lokale und gemeinsame Daten bewusst:

| Bereich | Technik | Zweck |
|---|---|---|
| lokaler Zustand | SQLite | Center2-Metadaten und Aktivierungszustände der Module |
| zuverlässige gemeinsame Daten | optionale MariaDB | Heartbeats, Actions, Quittungen und kurzlebiger Modul-Storage |
| Fallback-Kommunikation | Plugin Messaging | Reloads und Modul-Actions über Spielerverbindungen |

SQLite wird niemals als scheinbar gemeinsamer Speicher benutzt. Besonders
Inventare oder andere Übergabedaten werden dadurch nicht unbemerkt nur auf einem
einzelnen Server abgelegt.

Modul-Actions verwenden bevorzugt MariaDB. Ist sie nicht verfügbar, nutzt
Center2 Plugin Messaging. Wartet ein Zielserver noch auf eine Spielerverbindung,
hält Velocity die Action bis zum Join oder bis zum Ablauf ihrer Laufzeit im
Arbeitsspeicher. MariaDB bleibt für garantierte Zustellung ohne Spieler und für
atomaren gemeinsamen Storage der zuverlässige Weg.

## Commands und Permissions

```text
/center info
/center reload
/center modules
/center modules reload
/center modules enable <modul-id>
/center modules disable <modul-id>
```

Command-Pfade und Aliase sind weitgehend über `Commands.yml` konfigurierbar.
Administrative Zugriffe werden zentral über `Permissions.yml` geprüft. Die
Master-Permission lautet `center.admin.*`.

## Module

Modul-JARs werden hier abgelegt:

```text
plugins/Center2/Modules/Jars/
```

Ihre Konfigurationen liegen getrennt unter:

```text
plugins/Center2/Modules/Configs/<modul-id>/
```

Ein Modul deklariert seine eindeutige ID, Version, Plattform und kompatiblen
Center2-Versionen; auf Paper zusätzlich die kompatiblen Minecraft-Versionen.
Passt eine Version nicht, wird es kontrolliert blockiert. Gleiche Mindest- und
Höchstversion binden ein Modul exakt an eine Version.

Der Lebenszyklus besteht aus `onLoad`, `onEnable`, `onReload` und `onDisable`.
Die offiziell unterstützte Java-API liegt ausschließlich in:

```text
net.managerhub.center.api
net.managerhub.center.api.velocity
```

Module sind keine Sandbox. Sie laufen als normaler Java-Code im Serverprozess
und sollten nur aus vertrauenswürdigen Quellen installiert werden.

## Projektstruktur

| Pfad | Inhalt |
|---|---|
| `Center2/` | Core für Paper und Velocity |
| `testing/PaperTestModule/` | Paper-Beispielmodul |
| `testing/VelocityTestModule/` | Velocity-Beispielmodul |
| `docs/wiki/` | versionierte Quellen der GitHub-Wiki |

Lokale Minecraft-Testserver, Serverdaten, Secrets, IDE-Dateien, Maven-Ausgaben,
fertige JARs und KI-Arbeitsdateien werden nicht veröffentlicht.

## Selbst bauen

Alle drei Bestandteile sind eigenständige Maven-Projekte:

```bash
cd Center2
mvn clean install

cd ../testing/PaperTestModule
mvn clean package

cd ../VelocityTestModule
mvn clean package
```

Die Core-JAR entsteht unter `Center2/target/Center2-0.4.0.jar`. Paper- und
Velocity-API werden nur zum Kompilieren verwendet; SQLite- und MariaDB-Treiber
werden in die fertige Core-JAR eingebunden.

Der aktuelle Stand wurde mit einem sauberen Maven-Build und **356 automatischen
Tests** geprüft. Beide Beispielmodule bauen ebenfalls erfolgreich.

## Dokumentation

Die vollständige Dokumentation ist kostenlos direkt im Repository lesbar:

- [Dokumentationsübersicht](docs/wiki/README.md)
- [Installation](docs/wiki/Installation.md) und
  [erste Schritte](docs/wiki/Getting-Started.md)
- [Konfiguration](docs/wiki/Configuration.md),
  [Permissions](docs/wiki/Permissions.md) und
  [Netzwerk-Reload](docs/wiki/Network-Reload.md)
- [optionale MariaDB](docs/wiki/Network-Remote.md) und
  [Sicherheit](docs/wiki/Security.md)
- [Modulentwicklung](docs/wiki/Development.md),
  [API-Referenz](docs/wiki/Development-API.md) und
  [Versionsregeln](docs/wiki/Development-Versioning.md)
- [Troubleshooting](docs/wiki/Troubleshooting.md) und [FAQ](docs/wiki/FAQ.md)

Damit bleiben Dokumentation und Code gemeinsam versioniert und benötigen keinen
kostenpflichtigen GitHub-Wiki-Reiter.

## Support

- **Issues:** reproduzierbare Fehler und konkrete Aufgaben
- **Discussions:** Fragen und Austausch, sobald im Repository aktiviert
- **Dokumentation:** Installation, Bedienung und Entwicklung unter `docs/wiki/`

Bitte veröffentliche in Issues oder Logs niemals Datenbankpasswörter,
Zugangsdaten, Inventarinhalte oder andere sensible Serverdaten.
