# Netzwerk-Reload

`/center reload` lädt nicht nur den Server, auf dem du ihn tippst, sondern das
ganze MHCenter2-Netzwerk.

## Was dabei passiert

1. MHCenter2 prüft deine Permission (`center.admin` + `center.admin.reload`, oder
   die Master-Permission `center.admin.*`).
2. Dieser Server lädt sich selbst neu: `MainConfig.yml`, `Commands.yml`,
   `Permissions.yml`, die Sprachdatei, die Menüs, die Remote-Einstellungen und
   `onReload()` jedes laufenden Moduls.
3. Erst wenn das geklappt hat, geht die Anforderung ins Netzwerk.
4. Velocity lädt seine eigene MHCenter2-Instanz neu.
5. Die anderen Paper-Server laden ihre MHCenter2-Instanz neu.

Neu geladen wird **nur MHCenter2**. Nicht Paper, nicht Velocity, nicht andere
Plugins. Es ist auch kein Plugin-Reload: MHCenter2 liest seine Dateien neu und
sagt seinen Modulen Bescheid, mehr nicht.

## Was ein Reload nicht tut

Ein Reload tauscht **keine JAR** aus – weder die von MHCenter2 noch die eines
Moduls. Die Klassen, die laufen, bleiben genau die, die beim Start geladen
wurden.

> Modul-JAR geändert? **Serverneustart.** `/center reload` hilft dort nicht, und
> MHCenter2 tut auch nicht so, als ob.

## Die zwei Wege durchs Netzwerk

MHCenter2 nimmt den ersten Weg, der verfügbar ist:

| Weg | Braucht | Erreicht |
|-----|---------|----------|
| **Remote-Datenbank** | `remote.enabled: true` und erreichbare MariaDB | jeden Knoten, auch ohne Spieler |
| **Plugin-Messaging** | mindestens einen Spieler auf dem Ursprungsserver | den Proxy und jeden Server, auf dem jemand online ist |

Ist Remote eingeschaltet, aber gerade nicht erreichbar, sagt MHCenter2 das und
nimmt den Weg über den Proxy.

## Rückmeldung

MHCenter2 behauptet nie, ein Server sei neu geladen worden, wenn es dafür keine
Bestätigung gibt. Pro Knoten gibt es einen von fünf Zuständen:

| Zustand | Bedeutung |
|---------|-----------|
| **erfolgreich** | Der Knoten hat neu geladen und es bestätigt. |
| **fehlgeschlagen** | Der Knoten hat es versucht und ist gescheitert. Die Ursache steht in *seinem* Log. |
| **noch offen** | Der Knoten hat die Anforderung, aber noch nicht geantwortet. |
| **nicht erreichbar** | Es gibt gerade keinen Weg dorthin. |
| **abgelaufen** | Die Anforderung war zu alt, als sie ankam, und wurde nicht mehr ausgeführt. |

Über die Remote-Datenbank zeigt MHCenter2 dir direkt eine Zeile pro Knoten. Über
den Proxy steht das Ergebnis im Log des Ursprungsservers.

## Wartende Reloads

Ein Paper-Server ohne Spieler ist per Plugin-Nachricht nicht erreichbar. Der
Proxy merkt sich die Anforderung dann und stellt sie zu, sobald sich dort jemand
verbindet. Dabei gilt:

* pro Server wartet **eine** Anforderung, eine neuere ersetzt die ältere,
* eine Anforderung, deren Laufzeit abgelaufen ist, wird **verworfen** und nicht
  Stunden später noch zugestellt,
* der Proxy merkt sich höchstens 128 Server.

Startet der Proxy neu, sind wartende Anforderungen weg. Das ist richtig so: ein
Server, der danach startet, liest die aktuelle Konfiguration ohnehin. Wer einen
Knoten ohne Spieler zuverlässig erreichen will, schaltet die
[Remote-Datenbank](Network-Remote.md) ein.

## Warum das keine Endlosschleife wird

Zwei Regeln, beide zwingend:

1. **Ein Reload, der aus dem Netzwerk kam, wird nie weitergereicht.** Nur ein
   Reload, den ein Administrator ausgelöst hat, geht raus.
2. **Jeder Knoten führt eine Request-ID genau einmal aus.** Kommt dieselbe ID ein
   zweites Mal an – über den Proxy, über die Datenbank oder über beides –
   passiert nichts mehr.

Ohne beides würde Paper A dem Proxy Bescheid geben, der Proxy Paper B, Paper B
wieder dem Proxy, und es hörte nie auf.

## Reload auf dem Proxy

Auf Velocity gibt es `center reload` ebenfalls. Es ist bewusst nur eine
Bequemlichkeit: **kein** MHCenter2-Netzwerkfeature setzt voraus, dass jemand einen
Command auf dem Proxy ausführen kann. Der Proxy wird über die Plugin-Nachricht
oder über die Remote-Datenbank erreicht, beides ohne Proxy-Konsole.

## Siehe auch

* [Remote-Datenbank](Network-Remote.md)
* [Reload für Module](Development-Reload.md)
* [Troubleshooting](Troubleshooting.md)
