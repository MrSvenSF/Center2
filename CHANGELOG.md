# Changelog

## 1.0.0-beta.1 – 21. August 2026

Erste öffentliche Beta von Center2 für Paper und Velocity.

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
