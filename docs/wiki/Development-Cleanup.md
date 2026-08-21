# Cleanup

```java
context.registerCleanup(() -> HandlerList.unregisterAll(listener));
```

Mit `registerCleanup` sagst du MHCenter2, wie eine Ressource deines Moduls wieder
verschwindet.

## Warum das nötig ist

**MHCenter2 kann nur entfernen, was es kennt.** Ein Listener, ein Scheduler-Task,
ein Thread oder eine offene Datei entsteht in deinem Modulcode; MHCenter2 sieht
davon nichts.

Ohne Cleanup entsteht beim Deaktivieren ein Zombie: Das Modul steht im Menü auf
**Deaktiviert**, aber sein Listener feuert weiter. Genau das soll nicht passieren.

## Wann MHCenter2 das Cleanup ausführt

| Situation | Cleanup läuft |
|-----------|---------------|
| normales Deaktivieren | ja, nach `onDisable()` |
| Fehler in `onLoad()` | ja |
| Fehler in `onEnable()` | ja |
| Fehler in `onReload()` | ja, das Modul wird gestoppt |
| Fehler in `onDisable()` | ja, trotzdem |
| Serverende | ja |

Bei einem **erfolgreichen** Reload läuft das Cleanup **nicht**: das Modul bleibt
ja an. Registriere in `onReload()` deshalb nichts neu, ohne das Alte vorher
selbst zu entfernen – sonst hast du nach drei Reloads drei Listener.

Die Aktionen laufen in **umgekehrter Reihenfolge** ihrer Registrierung: was
zuletzt entstand, verschwindet zuerst.

## Direkt bei der Ressource anmelden

Melde die Aufräumaktion dort an, wo die Ressource entsteht:

```java
@Override
public void onEnable() {
    final Listener listener = new JoinListener();
    Bukkit.getPluginManager().registerEvents(listener, center);
    context.registerCleanup(() -> HandlerList.unregisterAll(listener));

    final BukkitTask task = Bukkit.getScheduler().runTaskTimer(center, this::tick, 20L, 20L);
    context.registerCleanup(task::cancel);

    // Wenn es hier kracht, sind Listener und Task trotzdem schon angemeldet
    // und werden von MHCenter2 wieder entfernt.
    riskanteInitialisierung();
}
```

Das ist der wichtige Unterschied zu „alles in `onDisable()` aufräumen":
`onDisable()` wird für ein Modul, das nie den aktiven Zustand erreicht hat,
**nicht** aufgerufen. Das registrierte Cleanup schon.

## Typische Beispiele

Listener abmelden:

```java
context.registerCleanup(() -> HandlerList.unregisterAll(listener));
```

Scheduler-Task abbrechen:

```java
context.registerCleanup(task::cancel);
```

Eigene Ressource schließen:

```java
final Connection connection = openConnection();
context.registerCleanup(() -> {
    try {
        connection.close();
    } catch (SQLException failure) {
        context.logger().error("Verbindung konnte nicht geschlossen werden.", failure);
    }
});
```

Eigenen Thread beenden:

```java
final ExecutorService pool = Executors.newSingleThreadExecutor();
context.registerCleanup(pool::shutdownNow);
```

## Was du nicht anmelden musst

Commands, die du über `context.registerCommand(...)` registriert hast. Die hat
MHCenter2 selbst ausgegeben und entfernt sie auch selbst.

## Wenn ein Cleanup fehlschlägt

Eine fehlschlagende Aktion stoppt die übrigen **nicht**: es wird so viel
aufgeräumt wie möglich. Der Fehler wird zusätzlich mit dem Schritt `CLEANUP`
geloggt und überschreibt nie den ursprünglichen Fehler, der zum Aufräumen geführt
hat.

Der Core und alle anderen Module laufen weiter.

## Grenzen

* Eine Aktion, die während des Aufräumens ein weiteres Cleanup anmeldet, wird
  ignoriert. Das verhindert Endlosschleifen.
* Jede Aktion läuft genau einmal, danach ist die Liste leer.
* Cleanup ist **Lifecycle-Verwaltung, keine Sicherheitsisolation**. Es entfernt
  das, was du anmeldest; es sperrt ein Modul nicht ein.

## onDisable oder Cleanup?

Beides ist erlaubt und ergänzt sich:

* **`onDisable()`** – der geordnete Abschluss eines Moduls, das lief: Daten
  speichern, Spieler informieren, eigenen Zustand zurücksetzen.
* **Cleanup** – das verlässliche Freigeben registrierter Ressourcen, auch wenn
  der Start mittendrin gescheitert ist.

Für Ressourcen ist das Cleanup die sichere Wahl.

## Was MHCenter2 dir abnimmt

Für einiges brauchst du gar kein eigenes Cleanup, weil MHCenter2 es selbst
zurücknimmt:

| Registriert über | Wird automatisch entfernt |
|------------------|---------------------------|
| `context.registerCommand(...)` | ja |
| `context.network().onAction(...)` | ja |
| `VelocityModuleApi.subscribe(...)` | ja |
| `VelocityModuleApi.schedule(...)` | ja |

Alles andere – ein Bukkit-Listener, ein Bukkit-Task, eine offene Datei, ein
eigener Thread, alles was du direkt auf `VelocityModuleApi.proxy()` registrierst
– kennt MHCenter2 nicht. Dafür ist `registerCleanup(...)` da.
