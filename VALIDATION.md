# Prüfstand TheMob 1.19.0

28. Oktober 2026. Pluginkern und API 1.3.1: **Java-17-Bytecode**, Spigot/Paper **1.18–26.3**. Build- und Testwerkzeuge: JDK 25. Alle automatisierten Läufe müssen ohne Fehler und ohne übersprungene Tests bestehen; XML-Ergebnisse: `build/test-results`.

| Prüflauf | Tests | Inhalt |
|---|---:|---|
| Hauptsuite | 294 | Kampf, HP-Rejoin, Skills, Katalog, Bossphasen/Zombie-König, Pets, Spawns/Cold, Dungeons/Altar, Menürechte/Dateientwürfe, öffentliche API, HUD, Provider-Fixtures |
| BattlePass 5.0.12 | 17 | Echte Registry/Action-/Questpipeline-Klassen, Kills, Phasen, Schaden, Drops, Loot, Dungeonfortschritt und Filter |
| SkilltreePlugin 2.0.0 | 14 | Echte lokale JAR, Ausrüstung/Attribute, Rejoin, Waffen- und Heilmodifikatoren |
| Weitere echte Anbieter-APIs | 8 | LuckPerms API 5.5, AuraSkills API 2.2.4, AdvancedCrates 4.7.4, EconomyShopGUI-Premium 6.5.1 |
| Start ohne optionale HUD-Anbieter | 1 | Eigener Klassenpfad ohne BetterHud/PlaceholderAPI |
| Ältere Minecraft-API 1.18.2 | 4 | Start/Shutdown, Adminmenü/alte Befehle, fehlende neuere Items, alte UUID-Modifikatoren und HP-Rejoin |
| Versionsmatrix | 1 | Produktionsverweise gegen 32 SHA-256-geprüfte offizielle Spigot-APIs |
| **Gesamt** | **339** | **0 Fehler, 0 übersprungene Tests** |

Die Matrix umfasst jede verfügbare stabile R0.1-Ausgabe von 1.18 bis 26.3 einschließlich Zwischenversionen. Sie prüft Klassen, JVM-Feld-/Methoden-/Konstruktordeskriptoren und Klassen-/Interface-Aufrufarten. Bericht: `build/reports/compatibility/api-matrix.txt`. Reproduzieren: [compatibility/README.md](compatibility/README.md).

Produktionscode wird gegen Spigot kompiliert. Paper kommt in den Simulationen zum Einsatz, nicht als Laufzeitklasse im Plugin. Getrennte Klassenpfade prüfen 1.18.2 und 26.3. Die damalige MockBukkit-Bibliothek benötigt dokumentierte Fixture-Ergänzungen: LivingEntities-Abfrage, leere Crossbow-Projektillisten, Paper-Strukturregistry, null bei fehlenden Spielerattributen und eine Auswertung gespeicherter Attributmodifikatoren. Diese Ergänzungen verändern keine Produktionspfade und überspringen keine Tests.

Neue Prüfungen vergleichen die Java-17-Clamp-Ergebnisse mit der modernen Math-Funktion einschließlich Sonderwerten, lesen historische Attribut-Snapshots und prüfen Modifikatoridentitäten sowie Inventarfenster. Der Editor akzeptiert absteigende HP-Phasenbereiche unverändert.

`compatibility/verify_release.py` prüft Artefakte/SHA-256, Java-17-Klassen, die 19 Haupt-API-/4 Dungeon-API-Klassen, SDK/Maven-Prüfsummen, Quellprojekt und Dokuverweise. Mit früheren API-/Original-JAR-Pfaden prüft es öffentliche API-1.3.0-Deskriptoren sowie acht unveränderte Original-Mobdateien und 14 Vendor-Klassen. Produktions-/Quell-/SDK-Pakete enthalten keine gekauften Anbieter-JARs. Bericht: `build/release-1.19.0-verification.json`.

Das SDK-Beispiel wird zusätzlich aus dem entpackten SDK gegen die 1.18-Server-API gebaut. Das vollständige Quellprojekt wird ebenfalls entpackt und unabhängig gebaut. So wird geprüft, dass keine Nachbarprojekte, erzeugten Java-Quelldateien oder IntelliJ-Caches erforderlich sind.

## Prüfgrenzen

Es wurde **kein echter Minecraft-Server/Client** gestartet und keine Last-/TPS-Steigerung gemessen. Die Matrix ist ein API-Verknüpfungstest, keine Liveprüfung jeder Ausgabe. Native KI, Pfadfindung, Flug-/Wasserverhalten, Chunk-/Netzwerkabwicklung, Clientpakete und große Bauten sind auf Testwelten zu prüfen.

Die echten Premium-/Skill-API-Tests starten keine vollständigen Anbieterplugins, Datenbanken, BattlePass-Saisons oder Netzwerke. Andere Adapter verwenden dokumentierte Verträge und Fixtures. Passende Fremdplugin- und Ressourcenpaketversionen sind je Minecraft-Version erforderlich. „Bereit“ bestätigt die Schnittstelle, keine gleichzeitige Liveprüfung aller 20 Anbieter. Folia ist nicht unterstützt.
