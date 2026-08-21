# Modulstatus

Jedes installierte Modul hat genau einen Zustand. Es gibt nur eine Quelle dafür:
Modulübersicht, Admin-Menü und Logs zeigen immer denselben Zustand.

| Zustand | Läuft? | Bedeutung |
|---------|--------|-----------|
| `ENABLED` | ja | Das Modul ist gestartet und stellt seine Funktionen bereit. |
| `DISABLED` | nein | Installiert, aber abgeschaltet. |
| `INCOMPATIBLE_CENTER` | nein | Unterstützt die laufende MHCenter2-Version nicht. |
| `INCOMPATIBLE_MINECRAFT` | nein | Unterstützt die laufende Minecraft-Version nicht. |
| `ERROR` | nein | Beim Laden, Starten oder Stoppen fehlgeschlagen. |

Im Menü und im Chat stehen die Zustände als Text: **Aktiviert**, **Deaktiviert**,
**Nicht kompatibel** und **Modul Error**. Bei „Nicht kompatibel" steht zusätzlich
kurz, welche Version nicht passt.

## ENABLED

Alles in Ordnung. Das Modul läuft, seine Commands sind registriert.

**Zu tun:** nichts.

## DISABLED

Das Modul ist installiert, läuft aber nicht. Gründe:

* Ein Administrator hat es abgeschaltet (`/center modules disable …` oder der
  Knopf im Menü). Dieser Zustand bleibt über einen Neustart erhalten.
* MHCenter2 konnte den gespeicherten Modulzustand nicht lesen. Dann startet in
  diesem Durchlauf **kein** Modul automatisch; die Ursache steht in der Konsole.

**Zu tun:** mit `/center modules enable <module-id>` oder dem Knopf **Aktivieren**
wieder einschalten.

## INCOMPATIBLE_CENTER

Das Modul unterstützt die laufende MHCenter2-Version nicht. Es bleibt sichtbar,
startet aber nicht und lässt sich auch nicht von Hand aktivieren.

Die Konsole nennt den geforderten Bereich und die laufende Version:

```
Modul 'Altes Modul' (ID AltesModul, Version 0.4.0) unterstützt MHCenter2 0.4.0 - 0.4.9,
hier läuft 1.0.1. Das Modul bleibt deaktiviert.
```

**Zu tun:** eine Modulversion besorgen, die zur installierten MHCenter2-Version
passt, oder die passende MHCenter2-Version verwenden. Danach Serverneustart.

## INCOMPATIBLE_MINECRAFT

Nur auf Paper. Das Modul unterstützt die laufende Minecraft-Version nicht.

```
Modul 'Future Modul' (ID FutureModul, Version 1.0.1) unterstützt Minecraft 1.22.0 - 1.22.9,
hier läuft 1.21.11. Das Modul bleibt deaktiviert.
```

Auch eine **neuere** Minecraft-Version gilt nicht automatisch als kompatibel:
auch dort kann sich die API geändert haben.

**Zu tun:** passende Modulversion verwenden. Danach Serverneustart.

## ERROR

Das Modul ist grundsätzlich kompatibel, hat aber beim Laden, Starten oder Stoppen
eine Ausnahme geworfen. Es läuft nicht, und seine Commands sind entfernt. MHCenter2
und alle anderen Module laufen normal weiter.

Im Menü steht **ausschließlich**:

```
Modul Error
```

Keine Stacktraces, keine Java-Klassennamen, keine Dateipfade. Ein Inventar ist
zum Verwalten da, nicht zum Debuggen.

Die vollständige Ursache steht in der **Serverkonsole**:

```
[MHCenter2] Modul 'Broken Test Module' (ID BrokenTestModule, Version 1.0.1)
ist im Schritt ENABLE fehlgeschlagen: IllegalStateException: ...
java.lang.IllegalStateException: ...
        at ...
```

Geloggt werden Modulname, Modul-ID, Modulversion, der Lebenszyklusschritt
(`LOAD`, `ENABLE`, `DISABLE`, `COMMAND_REGISTRATION`, `CLEANUP`), eine
verständliche Überschrift, die Ausnahme und der Stacktrace.

**Zu tun:**

1. In die Serverkonsole beziehungsweise ins Logfile schauen.
2. Prüfen, ob eine neuere Modulversion existiert.
3. Den Modulautor mit der Logausgabe kontaktieren.

Ein erneutes Aktivieren ist ein kontrollierter neuer Versuch. Schlägt er wieder
fehl, bleibt es bei `ERROR` und der Fehler wird erneut sauber geloggt. MHCenter2
versucht es nicht endlos.

## Modulsystem nicht verfügbar

Kein Modulzustand, aber eine mögliche Antwort der Commands:

```
Das MHCenter2-Modulsystem ist nicht verfügbar. Die Ursache steht in der Serverkonsole.
```

Dann konnte das Modulsystem gar nicht starten. Auf Paper passiert das, wenn
MHCenter2 die Minecraft-Version des Servers nicht bestimmen kann: ohne bestätigte
Version wird bewusst kein Modul geladen.

Das ist ausdrücklich **nicht** dasselbe wie „kein Modul installiert".
