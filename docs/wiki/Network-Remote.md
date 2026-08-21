# Remote-Datenbank

Die gemeinsame MariaDB ist **optional**. Sie ist ab Werk aus, und Center2
funktioniert ohne sie vollständig.

Sie fügt genau eine Sache hinzu, die Plugin-Nachrichten nicht können: einen
Center2-Knoten erreichen, auf dem **niemand online** ist.

## Was drin liegt – und was nicht

Drin liegt:

* welcher Knoten gerade lebt (Heartbeats),
* Netzwerk-Actions, zum Beispiel der netzwerkweite Reload,
* kurzlebige Daten von Modulen mit Ablaufzeit.

Nicht drin liegt: die lokale `DB/Center.db`. Jeder Knoten behält seine eigene
SQLite-Datenbank, und Center2 spiegelt sie **nicht** in die MariaDB. Es gibt
keine Vollsynchronisierung.

## Einrichten

### 1. Datenbank und Benutzer anlegen

```sql
CREATE DATABASE center2 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'center2'@'%' IDENTIFIED BY 'ein-langes-passwort';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE ON center2.* TO 'center2'@'%';
FLUSH PRIVILEGES;
```

`CREATE` braucht der Benutzer nur beim ersten Start: Center2 legt seine Tabellen
selbst an. Danach kannst du das Recht wieder entziehen. Mehr Rechte braucht
Center2 nicht – kein `DROP`, kein `SUPER`, kein Zugriff auf andere Schemata.

### 2. `MainConfig.yml` auf jedem Knoten

Auf **jedem** Paper-Server und auf dem Proxy:

```yaml
remote:
  enabled: true
  server-id: "lobby"        # auf jedem Knoten anders!
  database:
    host: "10.0.0.5"
    port: 3306
    database: "center2"
    username: "center2"
    password: "ein-langes-passwort"
    ssl: true
  polling:
    interval-ms: 1000
    action-ttl-seconds: 60
  heartbeat:
    interval-seconds: 10
```

### 3. Neu starten

Der `remote`-Abschnitt wird beim Start gelesen und bei jedem `/center reload`
erneut. Ein Neustart ist trotzdem der saubere Weg für die Erstinbetriebnahme.

## Die server-id

Jeder Knoten braucht seinen eigenen Namen im Netzwerk. Du vergibst ihn, Center2
leitet ihn **nicht** aus einem Hostnamen ab.

```
velocity   lobby   survival   citybuild
```

Erlaubt sind bis zu 64 Zeichen aus `a-z`, `0-9`, `_` und `-`; Groß- und
Kleinschreibung sind dasselbe. Mit `remote.enabled: true` darf die ID **nicht
leer** sein – ohne sie startet das Remote-System nicht, und Center2 sagt das im
Log.

> **Tipp:** Vergib auf einem Paper-Server dieselbe ID, unter der der Proxy den
> Server kennt (`velocity.toml`). Dann kann die Serverstatus-Anzeige den
> Heartbeat verwenden und erkennt den Server auch ohne Spieler als erreichbar.

### Zwei Knoten mit derselben ID

Wenn ein zweiter Knoten mit einer bereits belegten ID startet, sind die beiden im
Netzwerk nicht auseinanderzuhalten. Center2 erkennt das über die Runtime-ID, die
jeder Start neu vergibt, und schaltet das Remote-System **auf dem zweiten Knoten**
ab. Im Log steht eine klare Meldung. Lokal läuft dieser Knoten normal weiter:
Configs, Commands, Menüs, Module, lokale Datenbank.

Der Zustand bleibt bestehen, bis die Konfiguration korrigiert und der Knoten neu
gestartet oder neu geladen wurde.

## Heartbeats

Jeder Knoten schreibt alle `heartbeat.interval-seconds` eine Zeile mit:

* server-id,
* Runtime-ID dieses Laufs,
* Plattform (`PAPER` oder `VELOCITY`),
* Center2-Version,
* Minecraft-Version (auf Paper; der Proxy hat keine und erfindet auch keine),
* Zeitpunkt.

Ein Knoten gilt als offline, sobald er **drei** Intervalle hintereinander nichts
gemeldet hat – bei den Standardwerten also nach 30 Sekunden. Ein einzelner
Aussetzer während einer Lastspitze macht also noch keinen Knoten "tot".

Beim geordneten Herunterfahren trägt sich ein Knoten sofort selbst aus.

Das ist bewusst kein Monitoringsystem. Es beantwortet genau eine Frage: Wen kann
ich gerade erreichen?

## Wenn die Datenbank ausfällt

Nichts Dramatisches:

* Center2 bleibt aktiv, lokal ändert sich nichts.
* Der Knoten geht in den Zustand *offline* und versucht es erneut – die Pause
  zwischen zwei Versuchen verdoppelt sich bis maximal 60 Sekunden.
* Ins Log kommen **wenige** Zeilen, nicht eine pro Sekunde.
* `/center reload` fällt auf den Weg über den Proxy zurück und sagt dir das.
* Sobald die Datenbank wieder da ist, meldet sich der Knoten von selbst zurück.
  Eine Zeile im Log, dann läuft es weiter.

Was währenddessen **nicht** passiert: Remote-Daten eines Moduls werden nicht
ersatzweise lokal gespeichert. Siehe
[Netzwerk für Module](Development-Network.md).

## Polling

Es gibt keinen Message-Broker, also fragt jeder Knoten die Datenbank regelmäßig
nach neuen Actions. Das läuft in einem eigenen Hintergrund-Thread:

* nie auf dem Paper-Mainthread,
* nie auf dem Velocity-Event-Loop,
* genau ein Thread pro Knoten, also nie zwei Poller nebeneinander,
* das Intervall ist konfigurierbar (250 ms bis 60 s).

Die Verbindung kommt aus dem Verbindungspool des MariaDB-Treibers. Für jede
Abfrage eine neue TCP-Verbindung aufzubauen wäre bei einem Sekundentakt Unsinn.

## Aufräumen

Alle 60 Sekunden entfernt jeder Knoten, was abgelaufen ist: alte Actions, deren
Quittungen und abgelaufene Moduldaten. Eine abgebrochene Übertragung kann so
nichts dauerhaft liegen lassen.

## Was Center2 dort **nicht** tut

Die Remote-Datenbank ist **kein Fernsteuerungskanal**. Center2 liest von dort
keine Konsolenbefehle und führt auch keine aus. Es gibt genau einen Aktionstyp
des Cores – `CENTER_RELOAD` – und Module bekommen ausschließlich ihre eigenen,
namensraumgebundenen Actions. Alles Weitere unter [Sicherheit](Security.md).

## Siehe auch

* [Netzwerk-Reload](Network-Reload.md)
* [Sicherheit](Security.md)
* [Netzwerk für Module](Development-Network.md)
* [Konfiguration](Configuration.md)
