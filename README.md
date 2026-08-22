<div align="center">

# 🧩 MHCenter2

### Gemeinsamer Minecraft-Server-Core für Paper & Velocity

**Eine JAR • Zwei Plattformen • Modulare API • Netzwerkkommunikation**

[![Version](https://img.shields.io/badge/Version-1.0.1-2F81F7?style=for-the-badge)](https://github.com/MrSvenSF/MHCenter2/releases)
[![Java](https://img.shields.io/badge/Java-25-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Maven](https://img.shields.io/badge/Maven-Build-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue?style=for-the-badge)](LICENSE)

<br>

[![Wiki](https://img.shields.io/badge/📖_GitHub_Wiki-Dokumentation-2F81F7?style=for-the-badge)](https://github.com/MrSvenSF/MHCenter2/wiki)
[![Releases](https://img.shields.io/badge/📦_Releases-Downloads-238636?style=for-the-badge)](https://github.com/MrSvenSF/MHCenter2/releases)
[![Issues](https://img.shields.io/badge/🐛_Issues-Fehler_melden-D73A49?style=for-the-badge)](https://github.com/MrSvenSF/MHCenter2/issues)

</div>

---

## 👋 Was ist MHCenter2?

**MHCenter2** ist ein gemeinsamer Minecraft-Server-Core für
**Paper 1.21.11** und **Velocity 3.5.x**.

Dieselbe JAR wird auf beiden Plattformen verwendet und stellt unter anderem
Konfiguration, Administration, Netzwerkkommunikation und eine öffentliche
Java-Modul-API bereit.

> **Aktueller Stand:** `1.0.1` • Java 25 • Maven
>
> 📖 Die vollständige Benutzer- und Entwicklerdokumentation findest du im
> **[offiziellen MHCenter2-Wiki](https://github.com/MrSvenSF/MHCenter2/wiki)**.

> **Transparenzhinweis:**  
> MHCenter2 wurde mit Unterstützung künstlicher Intelligenz entwickelt.
> Architektur, Auswahl, Prüfung, Tests und Veröffentlichung liegen in der
> Verantwortung des Projektinhabers.

---

## ✨ Funktionen

- 🔗 gemeinsame Core-JAR für **Paper und Velocity**
- ⌨️ `/center info`, `/center reload` und Modulverwaltung
- 🔐 konfigurierbare Commands und Permissions
- 🖥️ Info- und Admin-Menüs auf Paper
- 🌍 deutsche und englische Texte
- 🗄️ lokale SQLite-Datenhaltung
- 🌐 optionale MariaDB für netzwerkweite Kommunikation
- 📡 Plugin Messaging als Fallback
- 🧩 Module für `PAPER`, `VELOCITY` oder `BOTH`
- 🔄 transaktionale Reloads
- 🛠️ automatische Config-Migrationen
- ✅ Versions- und Kompatibilitätsprüfung
- ☕ öffentliche Java-Modul-API
- 🧪 umfangreiche automatisierte Tests

MHCenter2 ist **kein Webdienst**.

Es gibt keinen integrierten HTTP-Server, keine REST-API und keinen
WebSocket-Dienst.

Mit „API“ ist ausschließlich die Java-Schnittstelle für externe
MHCenter2-Module gemeint.

---

## 📖 Dokumentation

Die vollständige Dokumentation befindet sich im GitHub-Wiki:

### 👉 [MHCenter2 Wiki öffnen](https://github.com/MrSvenSF/MHCenter2/wiki)

Dort findest du unter anderem:

- 🚀 Installation und erste Schritte
- ⚙️ Konfiguration
- 🔐 Permissions
- 🌐 Netzwerk und MariaDB
- 🔄 Netzwerk-Reload
- 🧩 Module
- ☕ Modulentwicklung und Java-API
- 🧪 Teststand
- 🔒 Sicherheit
- 🛠️ Troubleshooting
- ❓ FAQ

Die Markdown-Quellen bleiben zusätzlich unter
[`docs/wiki/`](docs/wiki/) im Repository erhalten.

So bleibt die Dokumentation gemeinsam mit dem Sourcecode versioniert,
während das GitHub-Wiki eine übersichtliche Oberfläche zum Lesen bietet.

---

## 🚀 Installation

### Voraussetzungen

- **Java 25**
- **Paper 1.21.11** und/oder **Velocity 3.5.x**
- optional eine gemeinsame **MariaDB**

### Installation

1. Lade die aktuelle MHCenter2-JAR aus den
   [Releases](https://github.com/MrSvenSF/MHCenter2/releases) herunter.
2. Lege dieselbe JAR in den jeweiligen `plugins/`-Ordner.
3. Starte Paper beziehungsweise Velocity einmal.
4. Prüfe die erzeugte Konfiguration unter `plugins/MHCenter2/`.
5. Verwende nach Änderungen `/center reload` oder starte die Instanz neu.

📖 **Ausführliche Anleitung:**  
[Installation im Wiki](https://github.com/MrSvenSF/MHCenter2/wiki/Installation)

### Update von `1.0.0-beta.1`

Server und Proxy zuerst stoppen.

Anschließend die alte Beta-JAR entfernen und:

```text
plugins/Center2/
```

in:

```text
plugins/MHCenter2/
```

umbenennen.

Dadurch bleiben Konfigurationen, Modulzustände und lokale SQLite-Daten erhalten.

---

## 🌐 Netzwerk & Datenbanken

MHCenter2 trennt lokale und gemeinsame Daten bewusst voneinander.

| Bereich | Technik | Zweck |
|---|---|---|
| 🗄️ Lokaler Zustand | SQLite | MHCenter2-Metadaten und Modulzustände |
| 🌐 Gemeinsame Daten | optionale MariaDB | Heartbeats, Actions, Quittungen und kurzlebiger Storage |
| 📡 Fallback | Plugin Messaging | Reloads und Modul-Actions über Spielerverbindungen |

### 🗄️ SQLite

Jede MHCenter2-Instanz besitzt ihre eigene lokale SQLite-Datenbank.

SQLite wird **niemals als scheinbar gemeinsamer Netzwerk-Speicher verwendet**.

### 📡 Plugin Messaging

Plugin Messaging ist der normale direkte Kommunikationsweg zwischen
Paper und Velocity.

Für die Übertragung zwischen beiden Plattformen wird eine Spielerverbindung
benötigt.

### 🌐 MariaDB

Die MariaDB ist **optional** und standardmäßig deaktiviert.

Mit aktivierter MariaDB sind unter anderem möglich:

- Heartbeats aller MHCenter2-Knoten
- Kommunikation ohne Spieler
- zuverlässige Modul-Actions
- Quittungen
- kurzlebiger gemeinsamer Modul-Storage
- atomare Datenübergaben

Fällt MariaDB aus, funktionieren lokale Core-Funktionen weiterhin.

📖 [Mehr zum Netzwerk und zur MariaDB](https://github.com/MrSvenSF/MHCenter2/wiki/Network-Remote)

---

## ⌨️ Commands

Die wichtigsten Standard-Commands:

```text
/center info
/center reload
/center modules
/center modules reload
/center modules enable <modul-id>
/center modules disable <modul-id>
```

Command-Pfade und Aliase können weitgehend über `Commands.yml`
konfiguriert werden.

---

## 🔐 Permissions

Administrative Zugriffe werden zentral über `Permissions.yml` geprüft.

Die Master-Permission lautet:

```text
center.admin.*
```

MHCenter2 besitzt ein bewusstes Permission-Gate für administrative Aktionen.

📖 [Permissions im Wiki](https://github.com/MrSvenSF/MHCenter2/wiki/Permissions)

---

## 🧩 Module

Modul-JARs werden hier abgelegt:

```text
plugins/MHCenter2/Modules/Jars/
```

Die Konfiguration eines Moduls liegt unter:

```text
plugins/MHCenter2/Modules/Configs/<modul-id>/
```

### Plattformen

Ein Modul kann für folgende Plattformen entwickelt werden:

```text
PAPER
VELOCITY
BOTH
```

Module deklarieren unter anderem:

- eindeutige Modul-ID
- Modulversion
- Zielplattform
- kompatible MHCenter2-Versionen
- auf Paper zusätzlich kompatible Minecraft-Versionen

Nicht kompatible Module werden kontrolliert blockiert.

---

## 🔄 Modul-Lifecycle

Der unterstützte Lifecycle besteht aus:

```text
onLoad()
onEnable()
onReload()
onDisable()
```

`onReload()` dient dem Reload von Konfiguration und Laufzeitzustand.

Es handelt sich **nicht** um einen automatischen Austausch einer bereits
geladenen Modul-JAR.

---

## ☕ Java-Modul-API

Die offiziell unterstützte API liegt ausschließlich in:

```text
net.managerhub.center.api
net.managerhub.center.api.velocity
```

Module können unter anderem:

- eigene Commands registrieren
- eigene Konfigurationsordner verwenden
- Cleanup-Ressourcen registrieren
- Netzwerk-Actions senden und empfangen
- Remote-Storage verwenden
- auf Velocity Proxy-Funktionen nutzen

📖 **Entwicklerdokumentation:**  
[Modulentwicklung](https://github.com/MrSvenSF/MHCenter2/wiki/Development)

📖 **API-Referenz:**  
[Development API](https://github.com/MrSvenSF/MHCenter2/wiki/Development-API)

---

## ⚠️ Sicherheit bei Modulen

MHCenter2-Module sind **keine Sandbox**.

Ein Modul ist normaler Java-Code und läuft im selben Prozess wie Paper oder
Velocity.

Installiere externe Module deshalb nur aus Quellen, denen du vertraust.

📖 [Sicherheitsdokumentation](https://github.com/MrSvenSF/MHCenter2/wiki/Security)

---

## 🧪 Tests

Der aktuelle Core-Build umfasst **356 automatisierte Tests**.

Getestet werden unter anderem:

- Commands und Aliase
- Permissions
- Konfiguration und Migrationen
- Reload-Verhalten
- Modul-Metadaten
- Modul-Lifecycle
- SQLite-Zustände
- Netzwerkprotokoll
- Remote-Actions
- Heartbeats
- Quittungen
- MariaDB-Storage
- Paper-Modul-API
- Velocity-Modul-API

Zusätzlich wurde MHCenter2 praktisch in einem lokalen Netzwerk getestet:

```text
Velocity
   │
   ├── Paper
   │
   └── Paper2
```

Der funktional identische Stand `1.0.0` wurde außerdem gegen eine echte
**MariaDB-11.8-Instanz in Docker** getestet.

Dabei wurden unter anderem erfolgreich geprüft:

- Verbindung aller drei Knoten
- Netzwerk-Reload mit Spieler
- Netzwerk-Reload ohne Spieler
- Modul-Action Paper → Paper2 ohne Spieler
- Remote-Storage mit `put()` und atomarem `take()`
- einmalige Verarbeitung übertragener Daten

Version `1.0.1` enthält gegenüber diesem Laufzeitstand keine
Funktionsänderungen.

Für `1.0.1` wurden der Core vollständig neu gebaut und alle automatisierten
Tests erneut ausgeführt.

📖 [Vollständiger Teststand](https://github.com/MrSvenSF/MHCenter2/wiki/Testing)

---

## 📁 Projektstruktur

| Pfad | Inhalt |
|---|---|
| `MHCenter2/` | Core für Paper und Velocity |
| `testing/PaperTestModule/` | Paper-Beispielmodul |
| `testing/VelocityTestModule/` | Velocity-Beispielmodul |
| `docs/wiki/` | versionierte Wiki-Dokumentation |
| `README.md` | Projektübersicht |
| `CHANGELOG.md` | Versionshistorie |
| `CONTRIBUTING.md` | Hinweise für Beiträge |
| `SECURITY.md` | Sicherheitsrichtlinie |
| `LICENSE` | Apache License 2.0 |
| `THIRD-PARTY-NOTICES.md` | Drittanbieter-Lizenzen |

Lokale Minecraft-Testserver, Welten, Zugangsdaten, IDE-Dateien,
Maven-Ausgaben, fertige lokale JARs und Arbeitsdateien werden nicht
veröffentlicht.

---

## 🔨 Selbst bauen

MHCenter2 verwendet **Maven**.

### Core

```bash
cd MHCenter2
mvn clean install
```

Die fertige JAR befindet sich anschließend unter:

```text
MHCenter2/target/MHCenter2-1.0.1.jar
```

### Paper-Testmodul

```bash
cd testing/PaperTestModule
mvn clean package
```

### Velocity-Testmodul

```bash
cd testing/VelocityTestModule
mvn clean package
```

Paper- und Velocity-API werden nur zum Kompilieren verwendet und nicht in die
Core-JAR eingebettet.

Die JDBC-Treiber für SQLite und MariaDB werden dagegen in die veröffentlichte
Core-JAR integriert.

---

## 🤝 Mitwirken

Beiträge, Fehlermeldungen und Verbesserungsvorschläge sind willkommen.

Bitte lies vor größeren Änderungen:

👉 [CONTRIBUTING.md](CONTRIBUTING.md)

Fehler und konkrete Aufgaben können über GitHub Issues gemeldet werden:

👉 [GitHub Issues](https://github.com/MrSvenSF/MHCenter2/issues)

Bei größeren Änderungen oder neuen API-Ideen sollte möglichst zuerst ein Issue
erstellt werden.

---

## 🔐 Sicherheitslücken melden

Sicherheitsrelevante Probleme sollten nicht mit vollständigen Details als
öffentliches Issue veröffentlicht werden.

Weitere Informationen:

👉 [SECURITY.md](SECURITY.md)

---

## 📋 Changelog

Alle Änderungen zwischen den Versionen findest du hier:

👉 [CHANGELOG.md](CHANGELOG.md)

---

## 📄 Lizenz

MHCenter2 wird unter der **Apache License 2.0** veröffentlicht.

Copyright 2026 Manager Hub.

👉 [LICENSE](LICENSE)

Die veröffentlichte JAR enthält zusätzlich:

### Xerial SQLite JDBC `3.46.1.3`

- Apache License 2.0
- BSD-2-Clause-Bestandteile

### MariaDB Connector/J `3.5.6`

- LGPL-2.1-or-later

Diese Drittanbieterkomponenten behalten ihre jeweiligen eigenen Lizenzen.

Weitere Informationen, Lizenztexte und Quellcodeverweise:

👉 [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)

Die relevanten Lizenzinformationen werden zusätzlich direkt in der
veröffentlichten JAR unter `META-INF/` mitgeliefert.

---

<div align="center">

## 🧩 MHCenter2

**Paper • Velocity • Java 25 • Maven**

<br>

[![Wiki](https://img.shields.io/badge/📖_Wiki-Dokumentation-2F81F7?style=flat-square)](https://github.com/MrSvenSF/MHCenter2/wiki)
[![Release](https://img.shields.io/badge/📦_Release-1.0.1-238636?style=flat-square)](https://github.com/MrSvenSF/MHCenter2/releases)
[![Issues](https://img.shields.io/badge/🐛_Issues-GitHub-D73A49?style=flat-square)](https://github.com/MrSvenSF/MHCenter2/issues)

</div>
