# Netzwerk für Module

Jedes Modul bekommt Zugriff auf das Center2-Netzwerk. Actions bevorzugen die
optionale MariaDB und verwenden sonst Plugin Messaging als begrenzten zweiten
Weg. Der gemeinsame Storage bleibt ausschließlich MariaDB.

```java
ModuleNetwork network = context.network();
```

## Ist es überhaupt da?

```java
if (!network.available()) {
    context.logger().warn("Gerade ist kein Netzwerk-Transport erreichbar.");
    return;
}
```

`network.available()` ist `true`, wenn MariaDB erreichbar ist oder Velocity über
einen Online-Spieler geantwortet hat. `network.storage().available()` ist nur
bei erreichbarer MariaDB `true`.

> **In `onEnable()` ist die Antwort meistens noch `false`.** Center2 verbindet
> sich im Hintergrund, der erste Heartbeat kommt erst danach. Frage `available()`
> also dort, wo du das Netzwerk wirklich brauchst, nicht einmal beim Start.
> Genauso kann die Antwort später wieder `false` werden, wenn die Datenbank
> ausfällt.

`network.serverId()` gibt dir die ID dieses Knotens, `network.onlineNodes()` die
IDs aller Knoten, die kürzlich einen Heartbeat geschrieben haben.

## Remote-Storage

`network.storage()` ist ein kleiner Speicher für **kurzlebige** Daten im
Namensraum deines Moduls.

```java
public interface ModuleStorage {
    boolean available();
    void put(String key, byte[] payload, Duration ttl) throws ModuleRemoteException;
    Optional<byte[]> get(String key) throws ModuleRemoteException;
    Optional<byte[]> take(String key) throws ModuleRemoteException;
    boolean delete(String key) throws ModuleRemoteException;
}
```

* **Namensraum:** deine Modul-ID. Ein anderes Modul kann deine Einträge weder
  lesen noch überschreiben.
* **Payload:** ein Block Bytes. Center2 schaut nicht hinein – ein serialisiertes
  Inventar, ein kleines JSON, was auch immer dein Modul versteht. Höchstens
  8 MiB.
* **Schlüssel:** bis zu 190 Zeichen.
* **Ablaufzeit:** Pflicht, mindestens eine Sekunde.

### `take()` – genau einmal

```java
Optional<byte[]> data = storage.take("transfer:" + player.getUniqueId());
```

`take()` liest und verbraucht in einem Schritt. Genau ein Aufrufer im ganzen
Netzwerk bekommt die Daten; jeder weitere bekommt `Optional.empty()`, auch wenn
zwei Server im selben Moment fragen.

Das ist der Unterschied zwischen "Inventar übertragen" und "Inventar
dupliziert".

### Kein lokaler Fallback

Wenn die Remote-Datenbank nicht verfügbar ist, wirft **jede** Methode eine
`ModuleRemoteException`. Center2 schreibt Remote-Daten **niemals** in die lokale
`DB/Center.db`.

Das ist Absicht, kein fehlendes Feature: Daten, die zwischen Servern reisen
sollen, wären auf genau einem Server gelandet – die Übertragung sähe erfolgreich
aus und wäre trotzdem verloren.

> Ein Modul muss diesen Fehlerfall behandeln. Eine Übertragung, die nicht
> gespeichert werden konnte, darf nicht so tun, als sei sie gespeichert.

### Nichts davon läuft auf dem Mainthread

Jede Methode blockiert auf einem Datenbankaufruf. Rufe sie **nie** auf dem
Paper-Mainthread oder auf dem Velocity-Event-Loop auf, sondern über den
Scheduler deiner Plattform.

## Remote-Actions

Eine Action ist eine Nachricht an dasselbe Modul auf anderen Knoten.

```java
network.send("HANDOVER", ModuleActionTarget.server("survival"),
        payload, Duration.ofSeconds(30));
```

```java
network.onAction(action -> {
    if ("HANDOVER".equals(action.type())) {
        applyHandover(action.payload(), action.origin());
    }
});
```

* **Ziel:** `ModuleActionTarget.ALL`, `.PAPER`, `.VELOCITY` oder
  `.server("<server-id>")`.
* **Typ:** ein Name, den du wählst. Bis zu 64 Zeichen aus `a-z`, `A-Z`, `0-9`,
  `_`, `-` und `.`.
* **Payload:** über MariaDB bis 1 MiB; über Plugin Messaging bis 900 KiB, damit
  der Protokollkopf sicher in Papers Gesamtlimit passt. Darf leer sein.
* **Laufzeit:** mindestens eine Sekunde. Was danach niemand abgeholt hat, wird
  verworfen.
* Der sendende Knoten bekommt seine eigene Action nie zugestellt.
* Mit MariaDB führt jeder Knoten jede Action **genau einmal** aus, auch nach
  einem Neustart. Der Plugin-Messaging-Fallback sperrt Duplikate nur im Speicher.
  Ist am Ziel noch kein Spieler, hält Velocity die Action bis zum nächsten Join
  oder bis zum Ablauf ihrer Laufzeit im Arbeitsspeicher bereit.
* Ein Listener pro Modul; ein zweiter Aufruf ersetzt den ersten. Center2 entfernt
  ihn, wenn das Modul stoppt.

Der Listener läuft im Hintergrund-Thread von Center2. Alles, was die Spielwelt
anfasst, gibst du selbst an den Scheduler deiner Plattform weiter.

### Eine Action ist kein Befehl

Center2 führt niemals einen Text aus der Datenbank als Konsolenbefehl aus. Was
mit deiner Action passiert, entscheidet ausschließlich dein eigener Code in
deinem Listener. Details unter [Sicherheit](Security.md).

## Beispiel: Inventory-Sync

Center2 baut **kein** Inventory-Sync-Modul. Es macht nur möglich, dass jemand
anderes eines schreibt. So sähe der Ablauf aus:

```
Paper A                       Velocity                    Paper B
-------                       --------                    -------
Spieler wechselt Server
Modul liest Bukkit-Inventar
Modul serialisiert es
storage.put("transfer:<uuid>",
            blob, 30s)
                              erkennt den Wechsel
                              koordiniert ihn
                                                          Spieler kommt an
                                                          storage.take("transfer:<uuid>")
                                                          Inventar wiederherstellen
                                                          (Eintrag ist damit weg)
```

Drei Regeln, die dabei nicht verhandelbar sind:

1. **Velocity liest kein Inventar.** Der Proxy hat die Bukkit-Spielerdaten nicht.
   Er erkennt den Wechsel, kennt Herkunft und Ziel und kann eine Remote-Action
   erzeugen – lesen und schreiben tut ausschließlich Paper.
2. **SQLite ist niemals der Netzwerkspeicher.** Mit MariaDB nutzt das Modul
   `put()` und `take()`. Ohne MariaDB kann es ein kleines, kurzlebiges Inventar
   direkt als Action-Payload senden, aber nur wenn der Spielerkanal zum Ziel
   gerade besteht; sonst muss die Übertragung kontrolliert fehlschlagen.
3. **`take()`, nicht `get()`.** Sonst könnten zwei Zielserver dasselbe Inventar
   anwenden.

Dazu die Ablaufzeit: bricht der Wechsel ab, verschwinden die Daten von selbst.
Der Storage darf keine dauerhafte Item-Datenbank werden.

Und: **logge keine Inventardaten.** Modul-ID, Schlüssel, Größe, Status – mehr
gehört nicht ins Log.

## Siehe auch

* [Remote-Datenbank](Network-Remote.md)
* [Sicherheit](Security.md)
* [API-Referenz](Development-API.md)
* [Velocity-Module](Development-Velocity.md)
