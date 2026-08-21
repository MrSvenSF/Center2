# Permissions

Center2 bringt **kein** eigenes Rollen- oder Gruppensystem mit. Die Nodes werden
an das Permissionsystem des Servers gereicht; LuckPerms und alle anderen
funktionieren wie gewohnt.

## Die Nodes

| Node | Wofür |
|------|-------|
| `center.admin.*` | Master: **alle** Center2-Adminfunktionen |
| `center.admin` | Admin-Button, Admin-Menü, Serverstatus |
| `center.admin.reload` | `/center reload` |
| `center.admin.modules` | Modulübersicht, Modulreihe, Detailansicht |
| `center.admin.modules.reload` | Modulordner neu einlesen |
| `center.admin.modules.enable` | Modul aktivieren (Command und Knopf) |
| `center.admin.modules.disable` | Modul deaktivieren (Command und Knopf) |

`/center info` braucht keine Permission.

## op: false

In `Permissions.yml` hat jeder Eintrag zwei Werte:

```yaml
  admin:
    permission: "center.admin"
    op: false
```

| Wert | Bedeutung |
|------|-----------|
| `permission` | die Node, die du über dein Permission-Plugin vergibst |
| `op` | ob Server-OP allein bereits ausreicht |

Mit `op: false` reicht OP **nicht**. Der Spieler braucht die Permission wirklich.
Das ist Absicht: Bukkit vergibt unbekannte Permissions sonst automatisch an
Operatoren, und ein OP käme so ohne `center.admin` in den Adminbereich.

Mit `op: true` gilt das normale Verhalten der Plattform.

Die **Serverkonsole** darf immer alles.

## Das Admin-Gate

Es gibt genau **eine** Berechtigungslogik. Commands, Tab-Vervollständigung,
Admin-Menü, Detailansicht und die beiden Knöpfe fragen dieselbe Regel:

| Funktion | erlaubt mit |
|----------|-------------|
| Adminbereich | `center.admin.*` **oder** `center.admin` |
| `/center reload` | `center.admin.*` **oder** (`center.admin` + `center.admin.reload`) |
| Modulübersicht und Modulmenü | `center.admin.*` **oder** (`center.admin` + `center.admin.modules`) |
| Modulordner neu einlesen | `center.admin.*` **oder** (`center.admin` + `center.admin.modules` + `center.admin.modules.reload`) |
| Modul aktivieren | `center.admin.*` **oder** (`center.admin` + `center.admin.modules` + `center.admin.modules.enable`) |
| Modul deaktivieren | `center.admin.*` **oder** (`center.admin` + `center.admin.modules` + `center.admin.modules.disable`) |

**Eine Unterpermission öffnet nie die allgemeine Adminschranke.** Wer nur
`center.admin.modules.reload` besitzt, aber nicht `center.admin`, darf gar
nichts. Das ist bewusst so: sonst wäre die Adminschranke umgehbar.

## Sichtbarkeit

Wer eine Funktion nicht benutzen darf, bekommt sie auch nicht vorgeschlagen:

* Der Admin-Knopf wird ohne Berechtigung nicht gesetzt.
* Die Modulreihe im Admin-Menü bleibt ohne Modulberechtigung leer.
* Die Modulcommands tauchen weder in der Tab-Vervollständigung noch in der
  Commandübersicht auf.

Das ist Bedienkomfort, kein Sicherheitsmechanismus. Die eigentliche Prüfung
passiert immer beim Ausführen.

## Beispiel mit LuckPerms

LuckPerms ist hier nur ein Beispiel; Center2 braucht es nicht.

Ein Hauptadministrator bekommt alles:

```
/lp user <Name> permission set center.admin.* true
```

Ein Moderator soll Module nur ansehen dürfen:

```
/lp user <Name> permission set center.admin true
/lp user <Name> permission set center.admin.modules true
```

Ein Modul-Operator soll zusätzlich aktivieren und deaktivieren dürfen:

```
/lp user <Name> permission set center.admin true
/lp user <Name> permission set center.admin.modules true
/lp user <Name> permission set center.admin.modules.enable true
/lp user <Name> permission set center.admin.modules.disable true
```

Genauso geht das über Gruppen (`/lp group <Gruppe> permission set …`).

## Eigene Nodes

Du kannst jede Node in `Permissions.yml` umbenennen, zum Beispiel auf
`meinnetzwerk.center.admin`. Erlaubt sind Kleinbuchstaben, Ziffern, `_`, `-` und
`.` als Trenner. Der Wildcard `*` ist ausschließlich am Ende der Master-Node
erlaubt; `*`, `*.admin`, `center.*.modules` und `center.admin.**` sind ungültig
und lehnen den Reload ab.

## Auf Velocity

Der Proxy hat keine `Permissions.yml`. Dort gelten dieselben Nodes fest
eingebaut, mit derselben Gate-Regel und derselben Bedeutung:

| Node | Wofür auf dem Proxy |
|------|---------------------|
| `center.admin.*` | Master |
| `center.admin` | allgemeine Adminschranke |
| `center.admin.reload` | `center reload` auf dem Proxy |
| `center.admin.modules` | `center modules` |
| `center.admin.modules.reload` | `center modules reload` |
| `center.admin.modules.enable` | `center modules enable` |
| `center.admin.modules.disable` | `center modules disable` |

Die Proxykonsole darf immer alles.

> Der Netzwerk-Reload braucht **keine** Proxy-Permission und keinen
> Proxy-Command: er wird auf Paper ausgelöst und erreicht den Proxy über die
> Plugin-Nachricht oder über die Remote-Datenbank. `center reload` auf dem Proxy
> ist nur eine Bequemlichkeit.
