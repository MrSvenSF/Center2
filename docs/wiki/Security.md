# Sicherheit

Diese Seite sammelt, worauf du beim Betrieb achten solltest – und was MHCenter2
ausdrücklich **nicht** tut.

## Module sind keine Sandbox

Ein MHCenter2-Modul ist normaler Java-Code im selben Prozess wie der Server. Es hat
dieselben Möglichkeiten wie jedes andere Plugin: Dateien, Netzwerk, Reflection,
alles.

Die Cleanup-API ist **Lifecycle-Verwaltung**, keine Sicherheitsisolation. Sie
sorgt dafür, dass ein gestopptes Modul keine Listener und keine Tasks
zurücklässt – sie hindert ein Modul an nichts.

**Installiere Module nur aus Quellen, denen du vertraust.** Genau so, wie du es
mit einem Plugin auch tun würdest.

## Remote-Actions sind keine Konsolenbefehle

Das ist die wichtigste Regel des Remote-Systems.

MHCenter2 liest aus der Datenbank **niemals** einen Text und führt ihn als
Konsolenbefehl aus. Es gibt keinen Remote-Console-Kanal, und es wird auch keinen
geben.

Was es stattdessen gibt:

* **Genau einen Aktionstyp des Cores:** `CENTER_RELOAD`. Ein anderer Typ im
  Namensraum `center` wird protokolliert und **nicht** ausgeführt.
* **Namensraumgebundene Modul-Actions.** Eine Action mit dem Namensraum
  `inventorysync` erreicht ausschließlich das Modul mit der ID `inventorysync`,
  und nur auf Knoten, auf denen dieses Modul läuft. Was dort passiert, entscheidet
  der Code dieses Moduls – nicht der Inhalt der Datenbankzeile.
* Ein Modul kann den Namensraum `center` nicht verwenden.
* Ein Aktionsname ist ein Name: bis zu 64 Zeichen aus `a-z`, `A-Z`, `0-9`, `_`,
  `-` und `.`. `op Spieler` ist kein gültiger Aktionsname.

Der Grund ist einfach: eine kompromittierte Datenbank wäre sonst gleichbedeutend
mit Codeausführung auf allen Servern des Netzwerks.

## Zugangsdaten der Datenbank

* Das Passwort steht in `MainConfig.yml`. Diese Datei gehört **nicht** in ein
  öffentliches Repository und nicht in ein Backup, das jeder lesen kann.
* Setze die Dateirechte so, dass nur der Serverbenutzer die Datei lesen kann.
* MHCenter2 schreibt das Passwort **nie** ins Log. Auch nicht in eine
  Fehlermeldung, auch nicht in die JDBC-URL: die URL enthält es gar nicht, das
  Passwort geht getrennt an den Verbindungspool.
* Eine Meldung über die Datenbank nennt höchstens `host:port/datenbank` und ob
  SSL an ist.

## Rechte des Datenbankbenutzers

Gib dem Benutzer nur, was er braucht, und nur auf dem einen Schema:

```sql
GRANT SELECT, INSERT, UPDATE, DELETE ON mhcenter2.* TO 'mhcenter2'@'%';
```

`CREATE` braucht MHCenter2 nur beim allerersten Start, um seine fünf Tabellen
anzulegen. Danach kannst du es entziehen.

Nicht nötig und nicht erwünscht: `DROP`, `ALTER` auf fremden Schemata, `SUPER`,
`FILE`, `GRANT OPTION`.

## SSL

`remote.database.ssl: true` ist der Standard und die Empfehlung. Die Verbindung
läuft dann mit `sslMode=verify-full`, das Zertifikat der Datenbank wird also
wirklich geprüft.

Schalte SSL nur in einem Netz aus, das du vollständig kontrollierst – und auch
dann eher nicht: die Verbindung überträgt Zugangsdaten und Moduldaten.

## SQL

Jeder Wert – Modul-ID, Spielername, UUID, server-id, Schlüssel, Payload – reist
als Parameter eines `PreparedStatement`. Nichts davon wird in einen SQL-Text
eingesetzt. Die einzigen fest zusammengebauten Teile sind die Tabellennamen, und
die stehen als Konstanten im Code.

## Temporäre Spielerdaten

Der Remote-Storage der Module kann große und sensible Daten enthalten – ein
serialisiertes Inventar zum Beispiel.

MHCenter2 loggt davon **nichts**. Ins Log kommen höchstens:

* die Modul-ID,
* der Schlüssel bzw. die Transfer-ID,
* die Größe,
* der Status,
* eine Fehlermeldung.

Niemals der Inhalt. Ein Modul sollte sich daran halten: Inventar-, NBT- oder
Spielerdaten gehören nicht in eine Logzeile.

Dazu kommt die Ablaufzeit: jeder Eintrag im Remote-Storage braucht eine, und
abgelaufene Einträge werden regelmäßig entfernt. Der Storage ist ein
Übergabepunkt, keine dauerhafte Datenbank.

## Permissions

* `center.admin.*` ist die Master-Permission und deckt allein den kompletten
  Adminbereich ab.
* Jede andere Adminberechtigung zählt nur **zusammen mit** `center.admin`. Eine
  einzelne Unterberechtigung öffnet die allgemeine Adminschranke nie von selbst.
* `op: false` bedeutet: die Permission wird wirklich gebraucht, OP allein reicht
  nicht.

Details unter [Permissions](Permissions.md).

## Was MHCenter2 nicht ist

Kein HTTP-Server, keine REST-API, kein WebSocket, kein Dashboard, kein
Login-Dienst, kein Marketplace, kein Auto-Update. MHCenter2 öffnet keinen Port und
lädt nichts aus dem Internet nach. Die einzigen ausgehenden Verbindungen sind die
zur optionalen MariaDB.

## Siehe auch

* [Remote-Datenbank](Network-Remote.md)
* [Netzwerk für Module](Development-Network.md)
* [Permissions](Permissions.md)
