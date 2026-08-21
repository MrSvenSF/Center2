# FAQ

### Brauche ich Center2 auf Paper und auf Velocity?

Nein, nicht zwingend. Center2 läuft auf einem einzelnen Paper-Server genauso wie
allein auf einem Proxy.

Beides brauchst du, wenn du die Netzwerkübersicht im Admin-Menü nutzen willst
oder wenn du Module auf beiden Seiten betreiben möchtest. Es ist dieselbe JAR.

### Können Module auf Velocity laufen?

Ja. Ein Modul mit `platform=VELOCITY` ist ein vollwertiges Center2-Modul: es wird
erkannt, geprüft, geladen, gestartet, gestoppt, kann eigene Proxy-Commands
mitbringen, wird bei Fehlern isoliert und lässt sich zur Laufzeit verwalten.

### Kann ein Modul auf beiden Plattformen laufen?

Ja, mit `platform=BOTH`. Center2 sorgt für Lebenszyklus, Kontext und
Kompatibilitätsprüfung. Dass plattformspezifischer Code sauber getrennt ist, muss
der Modulautor selbst sicherstellen, siehe [BOTH-Module](Development-Both.md).

### Kann ich ein Modul ohne Neustart hinzufügen?

Ja. Modul-JAR nach `plugins/Center2/Modules/Jars/` legen und
`/center modules reload` ausführen.

### Kann ich ein bereits geladenes Modul-JAR live aktualisieren?

Nein. Sobald ein Modul in dieser Serverlaufzeit Klassen geladen hat, wird seine
Binary nicht mehr ausgetauscht. Für ein Update: Server stoppen, JAR ersetzen,
Server starten.

Der Grund ist technisch: Java garantiert nicht, dass alle Klassen, statischen
Werte, Threads, Tasks und Listener der alten Datei wirklich verschwunden sind.

### Sind Center2-Module sicher isoliert?

**Nein.** Ein Modul ist normaler Java-Code im selben Prozess wie der Server und
hat dieselben Möglichkeiten wie jeder andere Code dort. Center2 verwaltet den
Lebenszyklus, es ist keine Sandbox. Die Cleanup-API ist Lifecycle-Verwaltung,
keine Sicherheitsisolation.

Installiere Module nur aus vertrauenswürdigen Quellen.

### Was Center2 trotzdem tut

Es isoliert **Fehler**, nicht Rechte: ein abstürzendes Modul reißt weder den Core
noch andere Module mit, bekommt den Zustand `ERROR`, verliert seine Commands und
sein registriertes Aufräumen wird ausgeführt.

### Brauche ich LuckPerms?

Nein. Center2 hängt von keinem Permission-Plugin ab; die Nodes werden einfach an
das Permissionsystem des Servers gereicht. Ein Permission-Plugin brauchst du nur,
wenn Spieler (nicht die Konsole) Adminfunktionen benutzen sollen. LuckPerms ist in
dieser Dokumentation nur ein Beispiel.

### Reicht OP für die Adminfunktionen?

Nein, standardmäßig nicht. Alle Center2-Permissions stehen auf `op: false`, das
heißt die Node muss wirklich vergeben sein. Wer das anders will, setzt in
`Permissions.yml` `op: true`.

### Muss ich meine Konfigurationsdateien nach einem Update löschen?

Nein. Center2 ergänzt fehlende neue Standardeinträge selbst, hebt
`config-version` an und lässt alles unverändert, was du geändert hast. Was
ergänzt wurde, steht in der Konsole.

### Kann ich Commands umbenennen?

Ja, in `Commands.yml`: `command` und `aliases` sind frei, `enabled` schaltet den
Command ab. Was ein Command tut, steht fest im Java-Code.

Nicht umbenennbar sind die festen Systemcommands `/center reload` und die
Modulübersicht `/center modules`.

### Kann ich eigene Commands in YAML definieren?

Nein. Es gibt bewusst kein `action:`, `type:`, `script:` oder `handler:`. Neue
Funktionen kommen über Module, nicht über Konfiguration.

### Darf ich eigene Module entwickeln?

Ja, ausdrücklich. Der Einstieg steht unter [Development](Development.md), die
offiziell unterstützte Schnittstelle unter [API-Referenz](Development-API.md).

### Wo finde ich die technische Ursache eines Modulfehlers?

In der Serverkonsole beziehungsweise in `logs/latest.log`. Im Menü steht bewusst
nur **Modul Error**.

### Warum prüft Velocity keine Minecraft-Version?

Ein Proxy hat keine einzelne Minecraft-Spielversion; er vermittelt Verbindungen
verschiedener Versionen. Center2 erfindet dort deshalb keine Version. Der
Center2-Versionsbereich gilt aber auf beiden Plattformen.

### Kann ich mehr als neun Module verwalten?

Ja. Das Admin-Menü zeigt in seiner Modulreihe bis zu neun Module; alle weiteren
bleiben installiert, behalten ihren Zustand und sind über `/center modules`
erreichbar. Eine Seitensteuerung gibt es in diesem Stand noch nicht.

### Gibt es einen Marktplatz oder automatische Updates?

Nein, und beides ist für diesen Stand auch nicht geplant. Module werden von Hand
installiert.

### Brauche ich die MariaDB?

Nein. Center2 funktioniert ohne sie vollständig: Konfiguration, Menüs, Commands,
Module, lokale Datenbank und das Plugin-Messaging zwischen Paper und Velocity.
Sie fügt eine Sache hinzu – einen Knoten erreichen, auf dem niemand online ist.

### Was passiert, wenn die MariaDB ausfällt?

Center2 läuft lokal weiter. Der Knoten geht offline, versucht es mit wachsender
Pause erneut, schreibt dabei wenige statt tausend Logzeilen und meldet sich von
selbst zurück. `/center reload` fällt auf den Weg über den Proxy zurück und sagt
dir das.

### Wird meine lokale Datenbank in die MariaDB gespiegelt?

Nein. `DB/Center.db` bleibt lokal. In der MariaDB liegen nur Knotenstatus,
Netzwerkaktionen und kurzlebige Moduldaten.

### Kann jemand über die MariaDB Befehle auf meinen Servern ausführen?

Nein. Center2 liest von dort niemals einen Text und führt ihn als Konsolenbefehl
aus. Es gibt genau einen Aktionstyp des Cores (`CENTER_RELOAD`), und Module
bekommen ausschließlich ihre eigenen, namensraumgebundenen Aktionen. Siehe
[Sicherheit](Security.md).

### Gibt es eine Web-Oberfläche oder eine REST-API?

Nein. Center2 öffnet keinen Port und bringt keinen HTTP-Dienst mit. Die einzige
ausgehende Verbindung ist die zur optionalen MariaDB.

### Lädt `/center reload` auch geänderte Modul-JARs?

Nein. Ein Reload ist ein Konfigurations-Reload. Eine ausgetauschte JAR braucht
einen Serverneustart, und Center2 sagt das auch, statt so zu tun, als sei die
neue Version aktiv.

### Muss ich `/center reload` auf jedem Server einzeln ausführen?

Nein, ein Aufruf reicht: er erreicht den Proxy und die anderen Paper-Server.
Siehe [Netzwerk-Reload](Network-Reload.md).

### Kann ein Velocity-Modul den MOTD ändern?

Ja. Ein Proxy-Modul kann den `ProxyPingEvent` abonnieren und den Text im
Multiplayer-Menü setzen. Center2 selbst bringt kein MOTD-Modul mit, es macht es
nur möglich. Siehe [Velocity-Module](Development-Velocity.md).

### Gibt es ein Inventory-Sync-Modul?

Nein, und Center2 wird auch keines mitbringen. Die Infrastruktur dafür ist
vorhanden – Remote-Storage mit atomarem `take()` und Remote-Actions –, damit ein
Drittanbieter-Modul das sauber umsetzen kann. Siehe
[Netzwerk für Module](Development-Network.md).
