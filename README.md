# TheMob 1.19.1 · Minecraft 1.18–26.3

Eigenständig baubares IntelliJ-/Gradle-Projekt des aktuellen TheMob-Plugins. **Eine Plugin-JAR für Spigot und Paper von 1.18 bis 26.3**, Java-17-Bytecode. Die Pluginversion 1.19.1 ist unabhängig von der Minecraft-Version. [Kompatibilität und Prüfgrenzen](KOMPATIBILITAET.md).

**`/mob` öffnet das vollständige Adminmenü ohne Ressourcenpaket. Alle bisherigen Befehle bleiben erhalten.** Mobdefinitionen, Bossphasen, Drops, Spawns, Dungeons, Skills, Integrationen und Einstellungen sind im Menü erreichbar; Namen und Zahlen werden bei Bedarf im Chat eingegeben. [Menüanleitung](ADMIN-MENUE.md).

Enthalten sind der bisherige Kampf-/Phasenablauf, Hot-/Cold-Verwaltung, Spawnlimits, Eier und Spawner, sechs zufällige Dungeonbauarten mit mindestens 500 Blöcken Wegenetz, Ausgangsaltäre, Rare-/Epic-Ausrüstung mit 1–5 Sternen, Pet-Kampfunterstützung und die öffentliche **API 1.3.1**. Der Katalog erzeugt Level 1–5 für die auf der jeweiligen Minecraft-Version vorhandenen Mobarten. Auf 26.3 sind das 450 reguläre Levelvarianten, 20 Archetypbosse und 160 Ausrüstungsteile zusätzlich zu den acht Originalvorlagen. Natürliche Spawns werden nicht ersetzt.

**20 ausgewählte optionale RPG-/Server-Anbindungen:** Jobs Reborn, Citizens, PikaMug Quests, AdvancedPlugins BattlePass, PlaceholderAPI, Vault, WorldGuard, LuckPerms, EssentialsX, Multiverse-Core, MythicMobs, MMOItems, AuraSkills, mcMMO, GriefPrevention, Towny, Lands, AdvancedCrates, EconomyShopGUI und DecentHolograms. Funktionen und jeweilige Grenzen: [Integrationen](INTEGRATIONEN-20.md). PKPlugin und CoinOverlay bleiben zusätzliche projektspezifische Verbindungen.

## Spieler-Skills und Stats entfernt

TheMob 1.19.1 enthält kein Spieler-Statsmenü, keine Spieler-Tiers und keine eigene Spielerprogression. Itemboni, Verzauberungen, Seltenheit/Sterne, Spezialeffekte und Bossfähigkeiten bleiben erhalten. `/stats` und `/skills` werden nicht mehr registriert. Boss-/Itemschalter heißen jetzt `/mob abilities` und `abilities.yml`; gültige alte Schalter werden übernommen. [Änderungen und Migration](FAEHIGKEITEN.md).

## Projekt öffnen und bauen

1. Diesen Ordner in IntelliJ als Gradle-Projekt öffnen.
2. Project SDK und Gradle JVM auf **JDK 25** stellen. Server- und Build-Java sind getrennt: der erzeugte Pluginkern verwendet Java 17.
3. Unter Windows `gradlew.bat build`, unter Linux/macOS `sh gradlew build` ausführen. Alternativ sucht `build.ps1` ein vorhandenes JDK 25. Der erste Build benötigt Internetzugriff auf Gradle/Maven.

Wichtige Ausgaben:

- `build/libs/TheMob-1.19.1-MC1.18-26.3.jar`
- `build/distributions/TheMob-1.19.1-MC1.18-26.3-Quellprojekt.zip`
- `build/distributions/TheMob-1.19.1-MC1.18-26.3-Update.zip`
- `build/distributions/TheMob-Developer-SDK-1.19.1.zip`
- `build/distributions/TheMob-GitBook-1.19.1.zip`

Das Quellprojekt enthält normale Java-Quellen unter `src/main/java`, Gradle Wrapper, Bibliotheken, Tests, Beispiele und Dokumentation. Es benötigt kein IntelliJ-Spezialformat und kein anderes lokales Projekt. [Git-Vorbereitung](GIT-START.md), [Installation](UPDATE.md), [Prüfstand](VALIDATION.md).

## Variante ohne SQLite

`gradlew.bat jarWithoutSqlite noSqliteTest noSqliteLegacy118Test` erzeugt und prüft `build/libs/TheMob-1.19.1-MC1.18-26.3-NoSQLite.jar`. Diese separate Variante enthält keinen SQLite-Treiber und lädt ihn nicht herunter. Boss-Respawnzeiten werden in `boss-cooldowns.yml` gespeichert. Auch bei einer alten Einstellung `boss-cooldowns.storage: sqlite` wird mit Warnung die Dateispeicherung verwendet. Eine vorhandene `boss-cooldowns.db` wird weder verändert noch automatisch importiert. Wer SQLite tatsächlich verwendet hat, muss seine benötigten Wartezeiten vor dem Wechsel in die Dateispeicherung übernehmen. Nur eine TheMob-JAR installieren und vollständig neu starten.

## Dokumentation und Entwickler

[GitBook](https://3o3y.gitbook.io/the-mob) · [Dokuquellen](docs/README.md) · [API](API.md) · [Developer Guide](DEVELOPER-GUIDE.md) · [BattlePass](BATTLEPASS.md) · [Boss-/Itemfähigkeiten](FAEHIGKEITEN.md) · [Dungeons](DUNGEONS.md) · [Spielerwerte](SPIELERWERTE.md) · [Performance](PERFORMANCE.md).

Die API wird über Bukkit ServicesManager bezogen; sie wird im Add-on nur als `compileOnly`/`provided` eingebunden. SDK, Beispiel, JavaDoc und lokales Maven-Repository sind enthalten. Es wurde kein öffentliches Maven- oder Git-Repository angelegt. Die MIT-Lizenz in `API-LICENSE.txt` gilt für API und SDK, nicht automatisch für das gesamte Plugin oder fremde Bibliotheken.

## Herkunft und bestehende Daten

Der ursprüngliche Quellstand war nicht auffindbar; Grundlage war die vom Nutzer bereitgestellte TheMob-1.8.4-JAR, SHA-256 `538aa5f0105ab2abaa7b9c0d22155eb4e9791655574df2b8008aeafed0f80dc9`. Die acht Original-Mobdateien und die 14 ursprünglich umbenannten bStats-Klassen bleiben bytegleich. Pluginname, Datenordner `plugins/TheMob`, bestehende Befehle, Mob-IDs und Item-PDC bleiben bestehen. Der bisherige 26.3-Projektstand wurde vor der Anpassung separat gesichert.

Das gemeinsame grafische Ressourcenpaket wird von CoinOverlay geliefert. Es muss zur Clientversion passen; das Adminmenü und der Textkompass funktionieren ohne dieses Paket. Ein 26.3-Clientpaket ist kein universelles Paket für ältere Minecraft-Versionen.
