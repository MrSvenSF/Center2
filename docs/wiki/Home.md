# MHCenter2

**MHCenter2** ist ein gemeinsames Minecraft-Plugin für **Paper** und **Velocity**.
Beide Einstiegspunkte liegen in genau einer JAR: dieselbe Datei kommt in den
`plugins`-Ordner des Paper-Servers **und** in den des Velocity-Proxys.

**Aktueller Stand: 1.0.0**

## Was MHCenter2 ist

MHCenter2 ist ein Core-System, kein Gameplay-Plugin. Es liefert:

* ein Infomenü und ein Admin-Menü auf Paper,
* eine Netzwerkübersicht der MHCenter2-Instanzen,
* zentrale, konfigurierbare Commands und Permissions,
* ein Sprachsystem (Deutsch und Englisch),
* eine lokale SQLite-Datenbank pro Instanz,
* einen **netzwerkweiten Reload** über alle MHCenter2-Knoten,
* eine **optionale gemeinsame MariaDB** für Koordination ohne Spieler,
* und ein **Modulsystem**: eigene Funktionen kommen als externe Modul-JARs dazu.

Ein MHCenter2-Modul ist kein Paper-Plugin. MHCenter2 kontrolliert den kompletten
Lebenszyklus: Erkennen, Prüfen, Laden, Starten, Reload, Stoppen,
Fehlerbehandlung. Module laufen auf **Paper**, auf **Velocity** oder auf
**beiden** Plattformen.

## MHCenter2 ist ein Netzwerksystem

Paper und Velocity sind nicht zwei unabhängige Installationen, sondern Knoten
desselben Netzwerks. Es gibt genau zwei Wege zwischen ihnen:

1. **Plugin-Messaging** zwischen Paper und Velocity. Immer vorhanden, braucht aber
   eine Spielerverbindung – eine Plugin-Nachricht reist durch einen Spieler.
2. Die **optionale MariaDB**. Sie erreicht auch einen Server, auf dem niemand
   online ist.

Es gibt **keinen** HTTP-Dienst, keine REST-API, kein WebSocket und keinen eigenen
Serverprozess dazwischen. Ohne MariaDB funktioniert MHCenter2 lokal vollständig
weiter; eingeschränkt sind nur die Funktionen, die zwingend einen anderen Knoten
brauchen.

## Für Serverbetreiber

| Seite | Inhalt |
|-------|--------|
| [Installation](Installation.md) | Voraussetzungen, Download, Paper- und Velocity-Installation |
| [Getting Started](Getting-Started.md) | Menüs, Commands, erste Schritte |
| [Configuration](Configuration.md) | alle Konfigurationsdateien |
| [Permissions](Permissions.md) | alle MHCenter2-Permissions und das Admin-Gate |
| [Netzwerk-Reload](Network-Reload.md) | `/center reload` über das ganze Netzwerk |
| [Remote-Datenbank](Network-Remote.md) | die optionale MariaDB, server-id, Heartbeats |
| [Teststand](Testing.md) | automatisierte Tests und echte Laufzeittests |
| [Sicherheit](Security.md) | Zugangsdaten, Rechte, was Remote-Actions **nicht** sind |
| [Module installieren](Modules-Installation.md) | Modul-JARs verwalten |
| [Modulstatus](Modules-Status.md) | was ENABLED, DISABLED, ERROR & Co. bedeuten |
| [Paper- und Velocity-Module](Modules-Platforms.md) | PAPER, VELOCITY, BOTH |
| [Troubleshooting](Troubleshooting.md) | wenn etwas nicht läuft |
| [FAQ](FAQ.md) | kurze Antworten |

## Für Entwickler

| Seite | Inhalt |
|-------|--------|
| [Development](Development.md) | Einstieg in die Modulentwicklung |
| [Erstes Modul](Development-First-Module.md) | Schritt für Schritt |
| [Metadaten](Development-Metadata.md) | `center-module.properties` |
| [Versionskompatibilität](Development-Versioning.md) | MHCenter2- und Minecraft-Bereiche |
| [Paper-Module](Development-Paper.md) | `platform=PAPER` |
| [Velocity-Module](Development-Velocity.md) | `platform=VELOCITY`, Events, MOTD, Scheduler |
| [BOTH-Module](Development-Both.md) | ein Modul für beide Seiten |
| [Commands](Development-Commands.md) | eigene Modulcommands |
| [Reload](Development-Reload.md) | `onReload()` und was ein Reload **nicht** ist |
| [Netzwerk für Module](Development-Network.md) | Remote-Storage und Remote-Actions |
| [Cleanup](Development-Cleanup.md) | Ressourcen sauber freigeben |
| [Fehlerbehandlung](Development-Errors.md) | ERROR und Isolation |
| [API-Referenz](Development-API.md) | die offiziell unterstützte API |
| [Beispielmodule](Development-Example.md) | die mitgelieferten TestModule |

## Sicherheit

MHCenter2-Module sind **keine Sandbox**. Ein Modul ist normaler Java-Code im selben
Prozess wie der Server und hat dieselben Möglichkeiten wie jeder andere Code dort.
Die Cleanup-API ist Lifecycle-Verwaltung, keine Sicherheitsisolation.

**Installiere Module nur aus vertrauenswürdigen Quellen.**

Die Remote-Datenbank führt niemals beliebigen Text als Konsolenbefehl aus. Alles
dazu steht unter [Sicherheit](Security.md).

## Community / Support

* **Issues** – nachvollziehbare Fehler und konkrete Aufgaben.
* **Discussions** – Fragen, Ideen, Feedback und Austausch über Modulentwicklung,
  sobald die Diskussionen im Repository geöffnet sind.
* **Wiki** – diese Dokumentation.

Diese Seiten sind die Dokumentation, nicht das Frageforum: Fragen gehören in die
Discussions, Fehler in die Issues.
