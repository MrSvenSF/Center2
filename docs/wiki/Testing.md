# Teststand

Diese Seite hält fest, was für MHCenter2 1.0.0 tatsächlich geprüft wurde. Sie
trennt automatisierte Tests von echten Laufzeittests und vermeidet dadurch, aus
einem erfolgreichen Build eine nicht geprüfte Produktionsgarantie abzuleiten.

## Automatisierte Tests

Der Core-Build führt **356 Tests** aus. Sie decken unter anderem ab:

* Commands, Aliase, Konflikte und Tab-Completion,
* Konfiguration, Migrationen und transaktionale Reloads,
* Permissions, Sprachen und Menüdaten,
* Modulmetadaten, Versionsbereiche, Lifecycle und Fehlerisolation,
* SQLite-Zustände,
* MariaDB-Store, Heartbeats, Actions, Quittungen, Ausfälle und Wiederverbindung,
* Plugin-Messaging-Protokoll, Größenlimits und wartende Zustellung,
* Paper- und Velocity-spezifische Modul-APIs.

Zusätzlich werden das Paper- und das Velocity-Beispielmodul eigenständig gebaut.

## Laufzeittest ohne MariaDB

Geprüft wurde ein lokales Netzwerk mit Velocity 3.5.1 und zwei Paper-Servern
auf Minecraft 1.21.11:

* dieselbe MHCenter2-JAR auf Paper und Velocity,
* Laden, Aktivieren, Reload und Deaktivieren externer Module,
* Menüs, Commands und Permissions,
* Spielerwechsel von Paper zu Paper2,
* Modul-Action Paper → Velocity → Paper2 über Plugin Messaging,
* netzwerkweiter Reload einschließlich wartender Zustellung beim Serverwechsel.

Dieser Weg ist der Fallback ohne gemeinsame Datenbank und benötigt für die
Übertragung zwischen Paper und Velocity eine Spielerverbindung.

## Laufzeittest mit MariaDB

Für 1.0.0 lief eine echte MariaDB-11.8-Instanz in einem kurzlebigen
Docker-Container. Paper, Paper2 und Velocity waren gleichzeitig als drei
MHCenter2-Knoten verbunden.

Praktisch bestätigt wurden:

* automatische Anlage der MHCenter2-Tabellen,
* Heartbeats aller drei Knoten,
* netzwerkweiter Reload über MariaDB mit Spieler,
* derselbe Reload mit **null Spielern**,
* Modul-Action direkt von Paper zu Paper2 ohne Spieler,
* gemeinsamer Modul-Storage mit `put()` und atomarem `take()`,
* identische Nutzdaten am Ziel und keine zweite Entnahme derselben Übergabe,
* erfolgreiche Action-Quittung und Entfernung der verbrauchten Storage-Daten.

Der Testcontainer und das nur dafür erzeugte Selbsttestmodul wurden danach
entfernt. Zugangsdaten, Datenbankdateien, Serverwelten und Logs gehören nicht
zum veröffentlichten Repository.

## Was daraus nicht folgt

Der Laufzeittest ersetzt nicht den Test in jeder Betreiberumgebung. Firewalls,
TLS-Zertifikate, MariaDB-Rechte, Netzwerklatenz und fremde Plugins unterscheiden
sich von Installation zu Installation. Vor einem produktiven Einsatz sollte
der Betreiber sein eigenes Netzwerk deshalb einmal vollständig durchtesten.

## Siehe auch

* [Installation](Installation.md)
* [Remote-Datenbank](Network-Remote.md)
* [Netzwerk-Reload](Network-Reload.md)
* [Netzwerk für Module](Development-Network.md)
* [Troubleshooting](Troubleshooting.md)
