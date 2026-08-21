# Changelog

## 1.0.1 – 22. August 2026

Lizenz- und Dokumentationskorrektur ohne Funktionsänderung.

- Apache-2.0-Lizenz von MHCenter2 wird jetzt auch in der fertigen JAR
  mitgeliefert.
- Lizenztexte und Versionshinweise für die eingebetteten JDBC-Treiber werden
  unter `META-INF/licenses/` in der JAR mitgeliefert.
- `THIRD-PARTY-NOTICES.md` trennt die Apache-2.0-Lizenz des eigenen Codes klar
  von SQLite JDBC (Apache-2.0/BSD-2-Clause) und MariaDB Connector/J
  (LGPL-2.1-or-later) und verweist auf den zugehörigen Quellcode.

## 1.0.0 – 21. August 2026

Erste stabile Veröffentlichung von MHCenter2 für Paper und Velocity.

### Neuer Projektname

- Das Projekt, das Plugin, die gemeinsame JAR und der GitHub-Auftritt heißen
  ab dieser stabilen Version **MHCenter2**.
- Technische Modul-API-Pakete sowie die bekannten `/center`-Befehle und
  `center.admin.*`-Permissions bleiben absichtlich kompatibel.
- Beim Wechsel von `1.0.0-beta.1` muss der bisherige Datenordner
  `plugins/Center2/` vor dem ersten Start in `plugins/MHCenter2/` umbenannt
  werden.

### Gegenüber der Beta zusätzlich geprüft

- echte MariaDB-11.8-Instanz in Docker mit Paper, Paper2 und Velocity,
- Heartbeats und gleichzeitige Verbindung aller drei MHCenter2-Knoten,
- netzwerkweiter Reload über MariaDB mit und ohne verbundenen Spieler,
- Modul-Action von Paper zu Paper2 über MariaDB ohne Spieler,
- gemeinsamer Modul-Storage mit `put()` und atomarem `take()` genau einmal,
- Datenbanktabellen, Action-Quittung und Entfernung verbrauchter Übergabedaten.

MariaDB bleibt optional. Ohne MariaDB verwendet MHCenter2 für Reloads und
Modul-Actions weiterhin den dokumentierten Plugin-Messaging-Fallback.

## 1.0.0-beta.1 – 21. August 2026

Erste öffentliche Beta von MHCenter2 für Paper und Velocity.

### Enthalten

- gemeinsamer Core für Paper und Velocity,
- Modul-API mit Plattform- und Versionsprüfung,
- lokaler SQLite-Zustand und optionale MariaDB-Anbindung,
- Plugin-Messaging-Fallback für Netzwerk-Reloads und Modul-Actions,
- Modulverwaltung, Menüs, Commands und konfigurierbare Permissions,
- transaktionale Konfigurations-Reloads,
- deutsche und englische Texte,
- Beispielmodule für Paper und Velocity sowie vollständige Projektdokumentation.

### Geprüft

- 356 automatisierte Core-Tests,
- Builds von Core und beiden Beispielmodulen,
- Laufzeittest mit Velocity und zwei Paper-Servern,
- Serverwechsel und Modul-Action Paper → Velocity → Paper ohne MariaDB,
- netzwerkweiter Reload einschließlich wartender Zustellung beim Spielerwechsel,
- Modul-Deaktivierung, Ressourcenfreigabe, erneute Aktivierung und Reload,
- Menüs, Commands und Permissions.

### Bekannte Einschränkung

Der MariaDB-Code ist automatisiert getestet, wurde für diese Beta aber noch
nicht gegen eine echte externe MariaDB-Instanz im Laufzeitbetrieb geprüft.
MariaDB bleibt optional; ohne sie gelten die dokumentierten Grenzen von Plugin
Messaging.
