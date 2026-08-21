# Das erste Modul

Diese Seite baut Schritt für Schritt ein minimales Center2-Modul. Als Referenz
dient das mitgelieferte `TestModule`.

## 1. Center2 lokal installieren

Die Modul-API kommt aus der Center2-JAR. Einmal installieren:

```bash
cd Center2
mvn clean install
```

Damit liegt `net.managerhub:center:<version>` im lokalen Maven-Repository.

## 2. Maven-Projekt anlegen

`pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">

  <modelVersion>4.0.0</modelVersion>

  <groupId>com.example</groupId>
  <artifactId>meinmodul</artifactId>
  <version>1.0.0</version>
  <packaging>jar</packaging>

  <properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <maven.compiler.release>25</maven.compiler.release>
    <center.version>0.4.0</center.version>
  </properties>

  <dependencies>
    <!-- Die Center2-API wird vom Server bereitgestellt und nie mitgepackt. -->
    <dependency>
      <groupId>net.managerhub</groupId>
      <artifactId>center</artifactId>
      <version>${center.version}</version>
      <scope>provided</scope>
    </dependency>
  </dependencies>

  <build>
    <finalName>MeinModul-${project.version}</finalName>
  </build>

</project>
```

`scope` ist **immer** `provided`: Center2-Klassen dürfen nie in der Modul-JAR
landen.

Braucht dein Modul zusätzlich die Paper-API (nur für `platform=PAPER` oder
`BOTH`), siehe [Paper-Module](Development-Paper.md).

## 3. Hauptklasse schreiben

```java
package com.example.meinmodul;

import net.managerhub.center.api.CenterModule;
import net.managerhub.center.api.ModuleContext;

public final class MeinModul implements CenterModule {

    private ModuleContext context;

    @Override
    public void onLoad(final ModuleContext context) {
        this.context = context;
        context.logger().info("geladen auf " + context.platform() + ".");
    }

    @Override
    public void onEnable() {
        context.registerCommand("meinmodul hallo",
                sender -> sender.sendMessage("<green>Hallo, " + sender.name() + "!"));
        context.logger().info("aktiviert.");
    }

    @Override
    public void onDisable() {
        context.logger().info("deaktiviert.");
    }
}
```

Wichtig:

* Die Klasse implementiert `CenterModule`.
* Sie hat einen öffentlichen Konstruktor ohne Argumente (hier der Standard-
  konstruktor).
* Alles, was Center2 dem Modul gibt, steckt im `ModuleContext`.

## 4. Metadaten anlegen

`src/main/resources/center-module.properties`:

```properties
id=MeinModul
name=Mein Modul
version=1.0.0
author=Dein Name
main=com.example.meinmodul.MeinModul
platform=PAPER
center-min-version=0.4.0
center-max-version=0.4.99
minecraft-min-version=1.21.4
minecraft-max-version=1.21.11
```

Die Datei muss **UTF-8** sein. Alle Felder erklärt
[Metadaten](Development-Metadata.md), die Versionsbereiche erklärt
[Versionskompatibilität](Development-Versioning.md).

## 5. JAR bauen

```bash
mvn clean package
```

Ergebnis: `target/MeinModul-1.0.0.jar`

Die JAR enthält nur deinen Code und `center-module.properties`, keine
Center2-Klasse.

## 6. Modul installieren

JAR kopieren nach:

```
plugins/Center2/Modules/Jars/
```

Bei einem `platform=VELOCITY`-Modul in den entsprechenden Ordner des Proxys.

## 7. Modul laden

Entweder Server neu starten, oder ohne Neustart:

```
/center modules reload
```

Danach prüfen:

```
/center modules
```

Erwartete Ausgabe:

```
--- Center2 Module ---
Mein Modul (MeinModul) Version 1.0.0 - Aktiviert
```

Und in der Konsole:

```
[Center2] [MeinModul] geladen auf PAPER.
[Center2] [MeinModul] aktiviert.
```

## 8. Weiter

* Eigene Konfigurationsdateien: `context.configDirectory()` zeigt auf
  `Modules/Configs/<id>/`. Center2 legt dort nichts an, das macht dein Modul
  selbst.
* Ressourcen wieder freigeben: [Cleanup](Development-Cleanup.md).
* Mehr zu Commands: [Commands](Development-Commands.md).
* Was bei einem Fehler passiert: [Fehlerbehandlung](Development-Errors.md).

## Beim Weiterentwickeln

Eine bereits geladene Modul-JAR wird **nicht** live ausgetauscht. Nach jedem
neuen Build den Server neu starten. `/center modules reload` erkennt nur
**neue** JARs.
