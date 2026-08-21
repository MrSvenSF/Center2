# Drittanbieterhinweise

Die veröffentlichte MHCenter2-JAR enthält die folgenden unveränderten
Drittanbieterkomponenten. Diese Komponenten werden nicht unter der
Apache-2.0-Lizenz von MHCenter2 neu lizenziert, sondern behalten ihre jeweils
angegebene Lizenz.

| Komponente | Version | Lizenz | Verwendung |
| --- | --- | --- | --- |
| [Xerial SQLite JDBC](https://github.com/xerial/sqlite-jdbc/tree/3.46.1.3) | 3.46.1.3 | Apache License 2.0 sowie BSD-2-Clause-Bestandteile aus dem Zentus-Treiber | lokale SQLite-Datenbank |
| [MariaDB Connector/J](https://github.com/mariadb-corporation/mariadb-connector-j/tree/3.5.6) | 3.5.6 | GNU Lesser General Public License 2.1 oder später (LGPL-2.1-or-later) | optionale gemeinsame MariaDB |

Die vollständigen Texte liegen im Quellrepository unter
`MHCenter2/src/main/resources/META-INF/licenses/` und in der gebauten JAR unter
`META-INF/licenses/`. Die Apache-2.0-Lizenz von MHCenter2 selbst liegt außerdem
als `LICENSE` im Repository und als `META-INF/LICENSE` in der JAR.

## MariaDB Connector/J

MHCenter2 verwendet und bündelt MariaDB Connector/J unverändert und ohne
Klassen-Relocation. Der Treiber ist eine eigenständige JDBC-Bibliothek und kann
gegen eine kompatible Version ausgetauscht werden, indem MHCenter2 aus dem
veröffentlichten Quellcode mit der gewünschten Connector-Version neu gebaut
wird. Der vollständige zu Version 3.5.6 gehörende Quellcode einschließlich der
Builddateien ist im
[offiziellen Quellarchiv](https://github.com/mariadb-corporation/mariadb-connector-j/archive/refs/tags/3.5.6.tar.gz)
und im
[offiziellen Repository](https://github.com/mariadb-corporation/mariadb-connector-j/tree/3.5.6)
verfügbar.

MHCenter2 selbst bleibt Apache-2.0-lizenziert. Die LGPL gilt ausschließlich für
MariaDB Connector/J; die Lizenztexte ändern die Lizenz des eigenen
MHCenter2-Codes nicht.

