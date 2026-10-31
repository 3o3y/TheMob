# Prüfstand TheMob 1.19.1

31. Oktober 2026. Pluginkern und API 1.3.1: Java-17-Bytecode, Spigot/Paper 1.18–26.3. Build- und Testwerkzeuge: JDK 25.

| Prüflauf | Tests | Inhalt |
|---|---:|---|
| Hauptsuite | 299 | Entfernung der Spieler-Skills/-Stats, fremde Befehle, Konfigurationsmigration, Itemboni/Spezialeffekte, HP-Rejoin, Mobs/Bosse, Dungeons, Adminmenü, API und HUD |
| BattlePass 5.0.12 | 17 | Echte Registry und native Questpipeline: Kills, Phasen, Schaden, Drops, Loot und Dungeonabschlüsse |
| Itemboni neben SkilltreePlugin 2.0.0 | 14 | Externe Attribut-/Waffen-/Heilmodifikatoren bleiben erhalten; kein eigener Skilltree-Adapter oder Statsmenü |
| Weitere echte Anbieter-APIs | 8 | LuckPerms 5.5, AuraSkills 2.2.4, AdvancedCrates 4.7.4, EconomyShopGUI-Premium 6.5.1 |
| Start ohne optionale HUD-Anbieter | 1 | Ohne BetterHud und PlaceholderAPI |
| Ältere Minecraft-API 1.18.2 | 4 | Start, Menü, Items und HP-Rejoin |
| Versionsmatrix | 1 | Verknüpfung gegen 32 SHA-256-geprüfte offizielle Spigot-APIs |
| Physische NoSQLite-JAR | 3 | Ohne Treiber und retired Statsklassen/-ressourcen; Dateicooldowns und unveränderte alte Datenbank |
| NoSQLite auf 1.18.2 | 4 | Getrennter Klassenpfad mit der tatsächlich gebauten NoSQLite-JAR |
| **Gesamt** | **351** | **0 Fehler, 0 übersprungene Tests** |

Die öffentlichen 19 Haupt-API- und vier Dungeon-API-Klassen sind bytegleich mit API 1.3.1 aus 1.19.0. Die acht Original-Mobdateien und 14 ursprünglichen bStats-Klassen bleiben erhalten. Zusätzliche Spielerwerte entstehen ausschließlich aus getragenen Items; eine eigene Spielerprogression, das Statsinventar und deren Konfigurationen werden nicht mehr mitgeliefert oder geladen.

Neue Tests prüfen die Entfernung der alten Befehle/Rechte/Klassen, den unveränderten Besitz fremder `/stats`-/`/skills`-Befehle, ignorierte alte Statsdateien, eine sichere einmalige Migration der Boss-/Itemschalter, die Vorrangigkeit vorhandener neuer Schalter und die Sperre alter Spielerdateien im Admineditor. Die Ausrüstungs-, Lebenspunkte-, Kampf-, Boss- und BattlePass-Regressionsprüfungen bleiben bestehen.

Die normalen Produktionsreferenzen und optionalen Brücken werden gegen die Versionsmatrix geprüft. Alte MockBukkit-Fixtures ergänzen dokumentierte fehlende Methoden, ohne Produktionspfade zu ändern oder Tests zu überspringen. Der eigenständige SDK-Beispielbuild gegen 1.18 wurde für 1.19.0 geprüft; seine öffentlichen API-Klassen sind weiterhin bytegleich. Bericht: `build/release-1.19.1-verification.json`. Der Artefaktprüfer `compatibility/verify_release.py` prüft zusätzlich Bytecode, SDK/POMs, Prüfsummen, Quellarchiv, entfernte Klassen und Dokuverweise.

## Prüfgrenzen

Kein echter Minecraft-Server/Client und keine Last-/TPS-Messung wurden gestartet. Native KI, Pfadfindung, Netzwerke, Clientdarstellung und große Bauten benötigen eine Testwelt. Die API-Tests starten keine vollständigen Anbieterplugins, deren Datenbanken oder BattlePass-Saisons. Versionspassende Fremdplugins und Clientpakete sind weiterhin nötig; Folia wird nicht unterstützt.
