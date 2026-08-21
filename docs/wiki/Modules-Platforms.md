# Paper- und Velocity-Module

Center2 ist ein gemeinsames System für Paper und Velocity, und das gilt auch für
Module. **Velocity-Module sind vollwertige Center2-Module**, keine Randnotiz.

Jedes Modul gibt in seinen Metadaten an, wofür es gebaut ist:

```properties
platform=PAPER
platform=VELOCITY
platform=BOTH
```

## PAPER

Das Modul läuft ausschließlich auf einem Paper-Server.

* Auf Paper: wird geladen, geprüft und gestartet.
* Auf Velocity: wird **nicht** geladen. Der Proxy meldet in der Konsole
  `the module is built for PAPER and does not run on VELOCITY.` und läuft normal
  weiter. Das Modul taucht auf dem Proxy nicht in der Modulübersicht auf, weil es
  dort schlicht nicht hingehört.

Ein Paper-Modul **muss** einen Minecraft-Versionsbereich angeben.

## VELOCITY

Das Modul läuft ausschließlich auf dem Proxy.

* Auf Velocity: wird geladen, geprüft und gestartet.
* Auf Paper: wird **nicht** geladen, mit der entsprechenden Meldung.

Ein Velocity-Modul **braucht keinen** Minecraft-Versionsbereich.

## BOTH

Das Modul ist für beide Seiten vorgesehen. Es durchläuft auf Paper **und** auf
Velocity denselben Lebenszyklus.

Ein `BOTH`-Modul muss einen Minecraft-Bereich angeben, weil es auch auf Paper
laufen kann. Auf Velocity wird dieser Bereich ignoriert.

Wichtig: `BOTH` heißt nicht, dass Center2 Paper-Code auf Velocity lauffähig
macht. Der Modulautor muss plattformspezifischen Code sauber trennen, siehe
[BOTH-Module](Development-Both.md).

## Was auf beiden Plattformen gleich ist

Ein Velocity-Modul kann alles, was ein Paper-Modul kann:

| Schritt | Paper | Velocity |
|---------|-------|----------|
| JAR in `Modules/Jars` erkennen | ja | ja |
| Metadaten lesen und prüfen | ja | ja |
| Modul-ID und doppelte IDs prüfen | ja | ja |
| Plattform prüfen | ja | ja |
| Center2-Versionsbereich prüfen | ja | ja |
| Minecraft-Versionsbereich prüfen | ja | nein, siehe unten |
| `onLoad`, `onEnable`, `onReload`, `onDisable` | ja | ja |
| eigene Commands registrieren | ja | ja |
| Cleanup registrieren | ja | ja |
| Remote-Storage und Remote-Actions | ja | ja |
| Fehlerisolation und `ERROR` | ja | ja |
| aktivieren, deaktivieren, erneut aktivieren | ja | ja |
| Zustand über Neustart merken | ja | ja |
| `/center modules reload` zur Laufzeit | ja | ja |

Auf dem Proxy gibt es kein Menü; die Verwaltung läuft dort über die Commands.

## Was ein Velocity-Modul zusätzlich kann

Ein Proxy-Modul ist keine Descriptor-Dekoration: es kann den Proxy wirklich
benutzen. Über `context.service(VelocityModuleApi.class)` bekommt es

* Velocity-Events (Verbindungen, Trennungen, Serverwechsel, Serverlisten-Ping),
* den Proxy-Scheduler,
* die Liste der Backend-Server,
* und den `ProxyServer` selbst für alles Weitere.

Was ein Modul darüber registriert, entfernt Center2 beim Stoppen wieder. Damit
lässt sich zum Beispiel ein MOTD-Modul, eine Wartungsanzeige oder eine
Netzwerkkoordination schreiben. Center2 selbst bringt so etwas nicht mit; es
macht es nur möglich. Siehe [Velocity-Module](Development-Velocity.md).

## Warum Velocity keine Minecraft-Version prüft

Ein Paper-Server hat genau eine Minecraft-Spielversion. Ein Velocity-Proxy nicht:
er vermittelt Verbindungen und kann Clients unterschiedlicher Versionen bedienen.

Center2 erfindet deshalb **keine** Minecraft-Version für den Proxy. Der
Minecraft-Bereich ist eine Paper-relevante Kompatibilitätsinformation.

Der **Center2-Versionsbereich** gilt dagegen auf beiden Plattformen: die Modul-API
ist dieselbe.

Auf Paper gilt zusätzlich: kann Center2 die Minecraft-Version des Servers nicht
bestimmen, wird gar kein Modul geladen. „Unbekannt" heißt dort „Kompatibilität
nicht bestätigt", nie „keine Prüfung nötig".

## Wenn ein Modul nicht auftaucht

Prüfe zuerst die Plattform des Moduls. Die Modul-Detailansicht im Paper-Admin-Menü
zeigt sie als **Paper**, **Velocity** oder **Paper & Velocity**. Weil Center2 eine
gemeinsame JAR für beide Seiten ist, ist das der häufigste Grund für „mein Modul
startet nicht": es liegt im falschen `Modules/Jars`-Ordner.

Siehe auch [Troubleshooting](Troubleshooting.md).
