# Modulcommands

Ein Modul registriert seine Commands nie selbst beim Server oder beim Proxy. Es
übergibt sie an Center2:

```java
boolean accepted = context.registerCommand("meinmodul hallo",
        sender -> sender.sendMessage("<green>Hallo, " + sender.name() + "!"));
```

Damit gilt die sichere Registrierung von Center2: kein Command des Cores und kein
Command eines anderen Plugins kann übernommen werden.

## Der Pfad

Der Pfad ist ein vollständiger Commandpfad ohne führenden Slash, in derselben
Schreibweise wie in `Commands.yml`:

```
"meinmodul hallo"   ->  /meinmodul hallo
"center test"       ->  /center test
```

* Der **erste** Teil ist der Command, der auf der Plattform registriert wird.
* Jeder weitere Teil ist ein festes Argument davor.
* Erlaubt sind `a-z`, `A-Z`, `0-9`, `_` und `-`, höchstens 32 Zeichen je Teil.
* Kein führender Slash, keine Doppelpunkte, keine Leerzeichen innerhalb eines
  Teils.
* Groß- und Kleinschreibung zählt als derselbe Pfad.

## Der Rückgabewert ist ehrlich

`registerCommand` liefert `true` **nur**, wenn der Command wirklich in den
Center2-Commandbaum aufgenommen wurde.

`false` bekommst du bei:

| Grund | Beispiel |
|-------|----------|
| ungültiger Pfad | `"/meinmodul hallo"` mit Slash |
| Pfad eines Center2-Core-Commands | `"center reload"`, `"center modules"` |
| Pfad eines konfigurierten Core-Commands oder seines Alias | `"center info"` |
| Pfad, den ein anderes Modul schon benutzt | doppelte Registrierung |
| Commandname, der einem **anderen Plugin** gehört | `"spawn home"`, wenn ein anderes Plugin `/spawn` besitzt |

Den letzten Fall prüft Center2 direkt bei der Plattform: auf Paper in der
CommandMap, auf Velocity im `CommandManager`. Ein Name, den Center2 selbst
registriert hat, zählt dabei nicht als fremd – `"center test"` ist also in
Ordnung.

In allen Fällen steht der Grund im Serverlog, mit Modulname, ID, Version und dem
Schritt `COMMAND_REGISTRATION`.

Du kannst dich auf den Rückgabewert verlassen:

```java
if (!context.registerCommand("meinmodul hallo", this::hallo)) {
    context.logger().warn("Command-Pfad belegt, bitte in der Modulkonfiguration aendern.");
}
```

## Was der Command bekommt

```java
@FunctionalInterface
public interface ModuleCommand {
    void execute(ModuleCommandSender sender);
}
```

`ModuleCommandSender` ist plattformneutral, damit derselbe Code auf Paper und auf
Velocity funktioniert:

| Methode | Bedeutung |
|---------|-----------|
| `String name()` | Spielername, oder `CONSOLE` für die Konsole |
| `boolean isPlayer()` | ob ein Spieler den Command benutzt hat |
| `boolean hasPermission(String)` | Berechtigungsprüfung über das Permissionsystem der Plattform |
| `void sendMessage(String)` | eine Zeile zurückschicken |

`sendMessage` liest den Text als Adventure MiniMessage, auf beiden Plattformen:

```java
sender.sendMessage("<green>Fertig.");
sender.sendMessage("<red>Das ging schief.");
```

Text, der von außen kommt (Spielernamen, Dateinamen), solltest du vorher
entschärfen, sonst kann er als MiniMessage interpretiert werden.

## Eigene Berechtigungen

Center2 prüft für einen Modulcommand keine Permission. Wenn dein Command eine
braucht, prüf sie selbst:

```java
context.registerCommand("meinmodul admin", sender -> {
    if (!sender.hasPermission("meinmodul.admin")) {
        sender.sendMessage("<red>Keine Berechtigung.");
        return;
    }
    // ...
});
```

Nimm dafür einen eigenen Namensraum, nicht `center.admin.*`.

## Center2-Commands sind tabu

Ein Modul darf keinen Center2-Core-Command übernehmen. Geschützt sind:

* `/center reload` (fest),
* `/center modules` (fest),
* jeder in `Commands.yml` konfigurierte Pfad und jeder seiner Aliase, auch der
  eines abgeschalteten Commands.

Der Versuch wird sofort mit `false` beantwortet.

## Lebenszyklus der Commands

* **Aktivieren:** Der Command wird registriert und ist sofort benutzbar.
* **Deaktivieren:** Center2 entfernt ihn wieder. Ein deaktiviertes Modul ist nicht
  mehr über seinen Command erreichbar.
* **Fehler:** Bei `ERROR` verschwinden die Commands des Moduls ebenfalls.
* **Shutdown:** Alle Modulcommands werden entfernt.
* **Erneutes Aktivieren:** Die Commands sind wieder da.
* **Reload:** Deine Commands bleiben registriert. Registriere sie in
  `onReload()` **nicht** erneut – der Pfad ist dann bereits vergeben und wird
  abgelehnt. Siehe [Reload](Development-Reload.md).

## Ein Core-Command darf dir nichts wegnehmen

Umgekehrt gilt dasselbe: Legt jemand in `Commands.yml` einen Core-Command oder
einen Alias auf einen Pfad, den dein laufendes Modul bereits bedient, lehnt
Center2 die **neue Command-Konfiguration** ab. Der Reload schlägt mit einer
klaren Meldung fehl und der letzte gültige Commandstand bleibt aktiv.

Ohne diese Regel wäre der Reload „erfolgreich" und dein Command danach
kommentarlos weg.

Dafür musst du **kein** eigenes Cleanup anmelden: Center2 entfernt einen Command,
den es selbst ausgegeben hat, von allein.

## Wo Commands registriert werden

In `onEnable()`. In `onLoad()` ist das Modul noch nicht aktiv.

## Konfigurierbar machen

Center2 schreibt Modulcommands nie in seine eigene `Commands.yml`. Willst du den
Pfad konfigurierbar machen, lies ihn aus deiner eigenen Datei unter
`Modules/Configs/<id>/`. Genau das macht das mitgelieferte TestModule, siehe
[Beispielmodul](Development-Example.md).
