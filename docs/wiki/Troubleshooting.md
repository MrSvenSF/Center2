# Troubleshooting

## Wo die technischen Logs stehen

* **Paper:** Serverkonsole und `logs/latest.log` im Serverordner.
* **Velocity:** Proxykonsole und `logs/latest.log` im Proxyordner.

MHCenter2-Meldungen sind mit `[MHCenter2]` (Paper) beziehungsweise `[mhcenter2]`
(Velocity) markiert. Meldungen eines Moduls tragen zusätzlich die Modul-ID, zum
Beispiel `[MHCenter2] [TestModule] aktiviert.`

Im Minecraft-Menü stehen **nie** technische Details. Die Konsole ist die
technische Wahrheit.

## MHCenter2 startet nicht

**Auf Paper** deaktiviert sich MHCenter2 selbst und schreibt den Grund:

```
MHCenter2 could not start: <konkreter Grund>
No commands and no menu are registered, MHCenter2 is disabled now.
```

**Auf Velocity** wird nur die MHCenter2-Initialisierung abgebrochen, der Proxy
läuft weiter.

Häufige Gründe:

| Meldung enthält | Ursache | Lösung |
|-----------------|---------|--------|
| `config-version` | Eine Datei meldet eine unbekannte Schemaversion. | Bei einer neueren Version: passendes MHCenter2 verwenden. |
| `is missing or is not` | Ein Wert in einer YAML-Datei fehlt oder hat den falschen Typ. | Die genannte Datei und den genannten Pfad korrigieren. |
| `the following texts are missing` | Die Sprachdatei ist unvollständig. | Fehlende Schlüssel ergänzen oder die Datei löschen, damit MHCenter2 sie neu anlegt. |
| `the following texts are unknown` | Die Sprachdatei enthält Schlüssel, die dieses MHCenter2 nicht kennt. | Meist eine Sprachdatei aus einer neueren Version. |
| `SQLite database` | `DB/Center.db` konnte nicht geöffnet werden. | Schreibrechte und freien Speicher prüfen. |
| `command path` | Zwei Commands beanspruchen denselben Pfad. | `Commands.yml` korrigieren. |

Die Meldung nennt immer Datei, Pfad und Wert. Sie ist absichtlich englisch: sie
entsteht, bevor eine Sprachdatei gelesen werden konnte.

## Ein Modul erscheint nicht in der Übersicht

Der Reihe nach prüfen:

1. **Liegt die JAR am richtigen Ort?**
   `plugins/MHCenter2/Modules/Jars/`, nicht in `plugins/`.
2. **Ist es die richtige Plattform?** Ein Paper-Modul erscheint auf dem Proxy
   nicht und umgekehrt, siehe [Paper- und Velocity-Module](Modules-Platforms.md).
3. **Steht etwas in der Konsole?** MHCenter2 meldet jede übersprungene JAR mit
   Grund:

```
Modul '<datei>.jar' wurde übersprungen: <Grund>
```

Typische Gründe:

| Grund | Bedeutung |
|-------|-----------|
| `does not contain 'center-module.properties'` | Die JAR ist kein MHCenter2-Modul. |
| `is missing the entry '<name>'` | Ein Pflichtfeld der Metadaten fehlt. |
| `duplicate module id '<id>' detected` | Eine zweite JAR beansprucht dieselbe Modul-ID. Das bereits installierte Modul bleibt unverändert, die neue JAR wird abgelehnt. |
| `the module is built for PAPER and does not run on VELOCITY` | Falsche Plattform. |
| `the main class ... was not found` | Der Eintrag `main` zeigt auf eine Klasse, die es nicht gibt. |

4. **`/center modules reload`** ausführen, wenn die JAR erst nach dem Start dazu
   kam.

## INCOMPATIBLE_CENTER

Das Modul unterstützt die laufende MHCenter2-Version nicht. Die Konsole nennt den
geforderten Bereich und die laufende Version. Passende Modulversion besorgen oder
passendes MHCenter2 verwenden, dann Serverneustart. Siehe
[Modulstatus](Modules-Status.md).

## INCOMPATIBLE_MINECRAFT

Nur auf Paper: Das Modul unterstützt die laufende Minecraft-Version nicht. Auch
eine neuere Minecraft-Version gilt nicht automatisch als kompatibel.

## Modul Error

Das Modul ist kompatibel, aber sein Code ist beim Laden, Starten oder Stoppen
fehlgeschlagen. MHCenter2 und alle anderen Module laufen weiter.

Im Menü steht nur **Modul Error**. Die vollständige Ursache mit Modulname, ID,
Version, Lebenszyklusschritt und Stacktrace steht in der Konsole. Damit den
Modulautor kontaktieren.

## Ich habe keine Berechtigung

* **OP allein reicht nicht.** Mit `op: false` braucht ein Spieler die Node
  wirklich.
* Eine Unterpermission allein reicht auch nicht: `center.admin.modules.reload`
  ohne `center.admin` erlaubt gar nichts.
* Die vollständige Tabelle steht unter [Permissions](Permissions.md).
* Zum Gegentesten: In der **Serverkonsole** darf immer alles. Funktioniert der
  Command dort, aber nicht als Spieler, ist es ein Berechtigungsproblem.

## Ein Command wird nicht vorgeschlagen

MHCenter2 schlägt nur vor, was der Absender auch benutzen darf. Fehlt die
Berechtigung, fehlt der Vorschlag.

Weitere Möglichkeiten:

* Der Command steht in `Commands.yml` auf `enabled: false`.
* Ein anderes Plugin besitzt bereits denselben Commandnamen. Dann meldet MHCenter2:

```
Der Command 'center' wird bereits von einem anderen Plugin verwendet.
Er ist nur als 'mhcenter2:center' erreichbar.
```

## Modul-JAR wurde zur Laufzeit ersetzt

MHCenter2 meldet:

```
Die JAR '<datei>.jar' von Modul '<name>' (ID <id>) wurde geändert oder entfernt.
Das Modul hat in dieser Laufzeit bereits Klassen geladen, deshalb ist für den
Austausch ein Serverneustart nötig.
```

Das ist kein Fehler, sondern die Regel: **Eine bereits geladene Modul-Binary wird
nie live ausgetauscht.** Auch der Umweg „JAR löschen, reloaden, neue JAR
hineinlegen, reloaden" funktioniert nicht; die Modulidentität bleibt bis zum
Neustart bekannt.

Für ein Modulupdate: Server stoppen, JAR ersetzen, Server starten.

## Wann ein Serverneustart nötig ist

| Situation | Neustart nötig? |
|-----------|-----------------|
| Neue Modul-JAR hinzufügen | nein, `/center modules reload` reicht |
| Modul aktivieren oder deaktivieren | nein |
| Konfiguration ändern | nein, `/center reload` reicht |
| Bereits geladene Modul-JAR ersetzen | **ja** |
| Modul-JAR löschen | **ja** |
| MHCenter2 selbst aktualisieren | **ja** |

## Velocity-Modul liegt auf Paper (oder umgekehrt)

MHCenter2 lädt es nicht und schreibt in die Konsole, für welche Plattform es gebaut
ist. Die JAR gehört in den `Modules/Jars`-Ordner der anderen Seite.

Die Plattform eines Moduls steht in der Modul-Detailansicht des Paper-Admin-Menüs
als **Paper**, **Velocity** oder **Paper & Velocity**.

## Modules/Jars kann nicht gelesen werden

```
Der Modulordner 'Modules/Jars' konnte nicht gelesen werden: <Grund>.
Er gilt deshalb nicht als leer.
```

MHCenter2 unterscheidet bewusst zwischen „gelesen, keine Module" und „konnte nicht
gelesen werden". Im zweiten Fall wird der Durchlauf abgebrochen und **kein**
bekanntes Modul vergessen.

Zu prüfen: Existiert der Ordner wirklich als Ordner (und nicht als Datei)?
Stimmen die Dateirechte? Läuft ein Backup- oder Virenscanner darauf?

## Der Modulzustand wurde nicht gespeichert

```
Modul '<name>' (ID <id>) ist für diese Laufzeit deaktiviert, der Zustand konnte
aber nicht gespeichert werden: <Grund>. Nach einem Neustart kann es wieder aktiv
sein.
```

Deine Entscheidung gilt für die laufende Sitzung, aber nicht sicher darüber
hinaus. Ursache ist ein Problem mit `DB/Center.db`: Schreibrechte und freien
Speicherplatz prüfen.

## Der Netzwerk-Reload erreicht andere Server nicht

```
MHCenter2 konnte das Netzwerk nicht erreichen. Dieser Server wurde neu geladen,
andere Server nicht.
```

Ohne Remote-Datenbank reist eine MHCenter2-Nachricht durch einen Spieler. Ist auf
dem Server, auf dem du `/center reload` tippst, niemand online, gibt es keinen
Weg zum Proxy.

**Zu tun:** entweder den Reload auf einem Server mit Spielern ausführen oder die
[Remote-Datenbank](Network-Remote.md) einschalten – die erreicht auch leere Server.

## Ein Server steht auf „noch offen"

Der Server hat die Anforderung bekommen, aber noch nicht bestätigt. Über die
Remote-Datenbank kann das schlicht daran liegen, dass sein Polling-Intervall noch
nicht abgelaufen war. Sieh eine Sekunde später ins Log dieses Servers.

Bleibt es dauerhaft „noch offen", steht die Ursache im Log **dieses** Servers,
nicht in deinem.

## Ein Server steht auf „nicht erreichbar"

Es gibt gerade keinen Weg dorthin: kein Spieler für die Plugin-Nachricht und
keine Remote-Datenbank, oder der Knoten hat seit über drei Heartbeat-Intervallen
nichts mehr gemeldet.

## Die Remote-Datenbank ist nicht erreichbar

```
Die Remote-Datenbank <host>:<port>/<db> ist nicht erreichbar (<Grund>). MHCenter2
läuft lokal weiter und versucht es in <n>s erneut. Versuch <n>.
```

MHCenter2 bleibt aktiv, die lokale Datenbank, die Module und alle Commands laufen
normal weiter. Der Knoten versucht es mit wachsender Pause erneut und meldet
sich, sobald es wieder klappt.

Zu prüfen: Läuft MariaDB? Ist der Port erreichbar (Firewall)? Stimmen
Benutzername und Passwort? Darf der Benutzer sich von der IP des Servers aus
verbinden? Passt die SSL-Einstellung zum Zertifikat der Datenbank?

## Das Remote-System startet gar nicht

```
Das Remote-System ist eingeschaltet, aber nicht verwendbar. MHCenter2 läuft
weiterhin lokal. 'remote.server-id' ist leer. ...
```

Der Abschnitt `remote` ist unvollständig. Die Meldung nennt **jeden** fehlenden
Wert, nicht nur den ersten. MHCenter2 verbindet sich in diesem Fall bewusst gar
nicht: ein halb konfigurierter Knoten wäre schlimmer als ein rein lokaler.

## Zwei Knoten mit derselben server-id

```
Ein anderer MHCenter2-Knoten verwendet bereits die server-id '<id>'. Das
Remote-System wird auf diesem Knoten abgeschaltet ...
```

Zwei Knoten mit einer ID sind im Netzwerk nicht unterscheidbar. MHCenter2 schaltet
das Remote-System auf dem zweiten Knoten ab; lokal läuft er normal weiter.

**Zu tun:** in `MainConfig.yml` eine eigene `remote.server-id` vergeben und den
Knoten neu starten.

## Der Reload wird abgelehnt wegen eines Modulcommands

```
Commands.yml: the command path "center test" is already used by the running
module 'TestModule'. ...
```

Ein Core-Command oder ein Alias sollte einen Pfad bekommen, den ein laufendes
Modul bereits bedient. MHCenter2 lehnt die neue Command-Konfiguration ab, statt den
Modulcommand kommentarlos verschwinden zu lassen. Die zuletzt gültige
Konfiguration bleibt aktiv.

**Zu tun:** einen anderen Pfad wählen, oder das Modul vorher mit
`/center modules disable <id>` abschalten.
