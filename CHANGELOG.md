# TheMob 1.19.1

- Eigenes Spieler-Skill-/Stats-System, `/stats`, Statsinventar, Ausrüstungstiers und ungenutzte Spielerprogression vollständig entfernt.
- Gegenstände mit Angriff, Rüstung, Defense, Leben, Verzauberungen, Sternen/Seltenheit und Spezialeffekten bleiben erhalten. Ausrüstungsboni werden ohne permanenten Spieler-Statcache aus den getragenen Slots berechnet.
- Bossfähigkeiten und Itemeffekte bleiben einzeln steuerbar über `/mob abilities` und `abilities.yml`; gültige frühere Boss-/Itemschalter werden ohne Änderung der Originaldatei übernommen.
- Keine Skilltree-Menüweiterleitung und keine Registrierung von `/skills` oder `/stats`. Öffentliche API 1.3.1, BattlePass-, Quest-/Job-Ereignisse, Moblevel, Loot und Dungeons bleiben erhalten.
- NoSQLite-Ausgabe und vollständiges IntelliJ-/Git-Projekt aktualisiert.

# TheMob 1.19.0

- Gemeinsamer Java-17-Pluginkern für Minecraft 1.18–26.3; API-Verknüpfung gegen 32 offizielle Spigot-Ausgaben sowie zusätzliche Start-/Menü-/HP-Tests mit 1.18.2.
- Versionsbrücken für Attribute, Modifikatoren, Partikel, Tränke, InventoryView, optionale Ereignisse, Köpfe und Ressourcenpakete. Display-Fallbacks und verfügbarkeitsabhängige Kataloge erhalten den bisherigen Kampfablauf.
- Admineditor akzeptiert vorhandene absteigende HP-Phasenbereiche. Historische Attribut-Snapshots bleiben lesbar.
- API-/Dungeon-SDK 1.3.1 mit Java-17-Bytecode, erhaltenen öffentlichen 1.x-Deskriptoren und eigenständigem Beispiel.
- Vollständiges IntelliJ-/Gradle-Projekt, Git-Dateien und aktualisierte GitBook-Quellen. Kein Git-Push und keine Veröffentlichung ohne tatsächlichen Zugriff.

# TheMob 1.18.2

- Pets als eigenständige Ziele für aggressive, taktische und Zombie-König-Angriffe einschließlich Flächenschaden; Aktivität auch unabhängig vom Besitzer. Abstimmung mit CosmeticsPlugin 0.10.1.

# TheMob 1.18.1

- Markierte Cosmetics-Pets und Darstellungen aus Radar, Bossbar und Gesundheitsanzeigen ausgeschlossen; normale wilde Mobs bleiben sichtbar.

# TheMob 1.18.0

- `/mob` öffnet für berechtigte Spieler das native Adminmenü ohne Ressourcenpaket; sämtliche bisherigen Befehle bleiben nutzbar.
- Mob-/Boss-/Itemkatalog, Spawns, Dungeons, Skills, Integrationen und Wartung über Inventarmenüs. Vollständiger typisierter Dateibaumeditor für eigene Konfigurationen und Mobdefinitionen, inklusive Phasen und Droptabellen; kurze Chat-Eingaben.
- Persönliche Entwürfe, erneute Rechte-/Fensterprüfung, Bestätigungen, strikte YAML-Prüfung, SHA-256-Konflikterkennung, Pfadbegrenzung und exakte `.admin.bak`-Sicherung. Speicherung getrennt vom Anwenden.
- 20 ausgewählte optionale RPG-/Server-Verbindungen. Neue native Itemfabriken, LuckPerms-Dungeonkontext, explizite AuraSkills-/mcMMO-XP, Claimprüfung vor Dungeonbau, EconomyShopGUI-Verkaufsschutz und optionale DecentHolograms-Bossanzeigen. EssentialsX/Multiverse verwenden Bukkit-Kompatibilität.
- API 1.3.0 ergänzt `AdminMenuExtension`, `TheMobItemProvider`, Menü-/Provider-Funktionen und `TheMobDungeonPreCreateEvent`. Bestehende öffentliche API-1.x-Methoden und Konstruktoren bleiben erhalten.
- Original-Metadaten fremder Items bleiben erhalten. Drop-/Lootidentität liegt persistent am Boden-Entity, statt Kistenschlüssel durch zusätzliche Inventar-Tags zu verändern.
- Spawn-Persistenz korrigiert: enabled, minRadius, maxRadius und arenaRadiusChunks werden vollständig gespeichert und geladen. FOLLOW_PLAYER lädt ohne feste Weltkoordinaten.
- SDK mit aktualisierten Sources/JavaDoc/Maven-POMs, eigenständig baubarem Beispiel für Menü-/Itemdienste und Anleitungen. Keine Premium-Anbieter-JARs werden verteilt.

# TheMob 1.17.0

- SkilltreePlugin 2.0.0: Statsmenü liest tatsächliche Attribute, öffnet den Skilltree und vermeidet irreführende Ausrüstungs-Tiers. TheMob-Waffenbasis wird vor Skilltree-Kampfboni berechnet; Lebensraub verwendet normale Heilereignisse.
- Boss-/Mobskills und Itemeffekte global und einzeln schaltbar: /mob skills, Rechte, Sicherung und Neustartvormerkung in skills.yml. Zombie-Hieb, Ansturm, Bodenwelle und Rückzug einzeln. Bestehende Phasengrenzen/IDs und Skilltree-Punkte bleiben erhalten.
- Öffentliche API 1.2.0: ItemSnapshot/LootSource, Schadens-/Drop-/Lootereignisse, aktive Phasenteilnehmer und Itemkatalog-Zugriff. Vorhandene Methoden/Konstruktoren und Dungeon-API bleiben kompatibel.
- SDK mit API-, Source-, JavaDoc-JARs, lokalem Maven-Repository, MIT-Lizenz für API/SDK und eigenständig baubarem Add-on.
- Vier native BattlePass-Typen ergänzen die fünf bisherigen: themob_phase_enter, themob_damage, themob_drop, themob_loot. Level-/Sternengrenzen, Seltenheit, Item-ID und eigene Kills sind filterbar. 20 Aufgabenvorlagen; bestehende Wochen und Spielerstände bleiben erhalten.
- Beuteherkunft an Boden-Entities: Teilaufnahmen und Stackmengen korrekt; erneutes Wegwerfen zählt nicht. Verschmelzung nur bei gleicher Herkunft. Begrenzte ereignisbasierte Puffer ohne Weltscans.
- /mob connect: Rechteprüfung, Status, Hilfen und Anbieter-Schalter mit atomarem Speichern und vorheriger Byte-Sicherung. Änderungen nach vollständigem Neustart; defekte Konfiguration wird nicht überschrieben.
- Nicht endliche API-Spawnpositionen werden abgelehnt. Inkompatible aktivierte WorldGuard-Prüfung blockiert Spawns, statt Schutzprüfung zu umgehen.
- Enthält die Textkompass-/optionales-Ressourcenpaket-Änderungen aus 1.16.2 und die bisherigen Spielerwerte-/Bosskorrekturen.

# TheMob 1.16.1

- Rejoin-/Shutdown-Verlust zusätzlicher Lebenspunkte behoben: Ausrüstungsboni werden vor dem Laden des HUD wieder angewendet; nur der beim Entfernen tatsächlich verlorene Lebensanteil wird einmalig gutgeschrieben. Wunden, Schaden während des Ladens und fremde Boni bleiben erhalten.
- Passende additive Skilltree-1.0.1-Korrektur, mit zwölf Prüfungen gegen die tatsächliche JAR für beliebige Reihenfolgen beider Anbieter. Keine Wiederverwendung alter Gutschriften nach Tod/Respawn.
- Zusätzliche Item-Defense wirkt jetzt auch gegen Mob-/Projektilangriffe und wird im PvP nicht doppelt gezählt. Statistikmenü verwendet echte Lebens-/Rüstungsattribute. CoinOverlay 1.8.3 zeigt Rüstung bis 30; Nahrung bleibt separat bei 20. Anleitung: SPIELERWERTE.md.

# TheMob 1.16.0

- Eigene Zombie-König-Steuerung für alle vier HP-Phasen: frontaler schwerer Hieb, gerichteter Ansturm, expandierende Bodenwelle und begrenzter verwundeter Rückzug mit Gegenhieb.
- Sicht-/Hörzeichen, festgelegte Schlagrichtung, springbare Welle, Erholungsfenster und höchstens ein Treffer je Spieler/Spezialaktion. Native Nahkampftreffer sind während Spezialaktionen gesperrt; verhinderter Schaden erzeugt keinen zusätzlichen Rückstoß.
- Zentrale Bewegungssteuerung nur für gerade bewegte Bosse, geladene Chunks, skalierte Körperprüfung, Arena-Leine, Cold-Abbruch und sichere Phasenwechsel. Keine Teleports oder Blockzerstörung durch die allgemeine Feststeckhilfe.
- Automatisches, abschaltbares Laufzeitprofil `zombie-boss.yml`, auch für vorhandene Installationen und fünf Warlord-Level. Originaldateien, HP-Phasen, Beute und Integrations-IDs bleiben erhalten. Anleitung: ZOMBIE-KOENIG.md.

# TheMob 1.15.0

- Physischer Ausgangsaltar in jeder Bosskammer; beim nächsten Besuch auch in vorhandenen Dungeons. Fünf mal fünf Blöcke mit Zaubertisch, weinendem Obsidian und Seelenlaternen.
- Geschütztes, paginiertes Reisemenü für PK-Homebanner, eigene aktive Knoten und nächstes zugängliches Stadtzentrum; Rückkehr und Dungeon-Eingang bleiben ohne PK verfügbar.
- Additive Reise-SPI und Quellenprüfung in Dungeon-API 1.1.0. PKPlugin 0.7.2 prüft Rechte, Ziele und sichere Landung erneut. Kein Fake-Banner, kein globaler PK-Bypass.
- Admin-Platzierung/Reparatur `/mob dungeon altar ID [here]`, persistente Position und begrenzte Chunk-Nachrüstung. Keine BattlePass-Wertung durch Altarbenutzung.

# TheMob 1.14.0

- Additive API 1.1 für Dungeonbereiche, abgestimmte PKPlugin-0.7.1-Anbindung für freie Bewegung ohne Bannerausbau im Dungeon. Vorhandene Anlagen bleiben erhalten.
- `/mob dungeon boss ID Boss-Mob-ID`: persistente Endboss-Auswahl. Passende Spielerkills werten für anwesende lebende Spieler; andere Mobs, Minions, NPCs, Zuschauer und Kills von außerhalb erzeugen keinen Dungeonabschluss.
- Neuer BattlePass-Typ `themob_dungeon_complete`, ID-/Stil-/Welt-/Boss-/Ebenenfilter und zwei neue Vorlagen. Echte BattlePass-Questpipeline zusätzlich für Dungeonfortschritt geprüft.
- Öffentlicher Dungeon-Abschlussservice und `DungeonCompletedEvent`; persistenter Wiederholschutz für die letzten 256 Durchlauf-IDs pro Anlage.
- Anleitung: DUNGEON-API.md. Bestehende Killziele, Spawns, Bauschutz und Ressourcenpaket bleiben erhalten.

# TheMob 1.13.1

- Öffentliche Dungeon-API 1.1: unveränderliche reservierte Grundflächen, Welt-/Grenzabfrage und Fertigstatus ohne Chunkladen, auch nach Reload/Neustart.
- PKPlugin 0.7.1 kann fertige Dungeonbereiche von Bewegungsgrenzen ausnehmen und PK-Banner sowie übergreifende PK-Gebäude in reservierten Baufeldern verhindern. Vorhandene API-1.0-Add-ons bleiben kompatibel.

# TheMob 1.13.0

- `/mob dungeon` erzeugt leere, große, zufällige Bauwerke am Admin-Standort. Sechs Familien: Krypta, Turm, Mine, Weltenbaum, zehnstöckiger Gottesturm und Labyrinth. Mindestens 500 begehbare Blöcke im verbundenen Wegenetz, große Tore und breite Treppen.
- Vorhandenes Gelände im 113×133-Baufeld wird ohne Drops vom Fundament bis zur Weltobergrenze freigeräumt/ersetzt. Spieler und vorhandene lebende Wesen werden aus dem Baufeld versetzt. Planung im Hintergrund, Schreiben mit Block-/Zeitbudget und einem Chunk gleichzeitig.
- Erzeugung ausschließlich für Admins. Persistenter Schutz gegen Abbau, Explosionen, Feuer, Kolben, Flüssigkeiten und Veränderungen durch Wesen; persönlicher Admin-Baumodus zum Einrichten. Mehrspieler-Zutritt und Rückkehr, Status, Listen, Abbruch und Weiterbau unterbrochener Anlagen.
- Keine automatische Mob-/Lootbefüllung. Natürliche Spawns im Dungeon gesperrt; bestehende TheMob-Spawns, API, BattlePass und Ressourcenpaket bleiben erhalten. Einzelheiten: DUNGEONS.md.

# TheMob 1.12.0

- 450 Varianten: jede der 90 spawnfähigen Mob-Spezies aus der Minecraft-26.3-API in Level 1–5. Zusätzlich Warlord, Marksman, Broodmother und Guardian in je fünf Stufen. Zusammen mit den acht erhaltenen Originalen 478 Definitionen.
- 160 Ausrüstungsteile: 16 Vorlagen in Rare/Epic mit je 1–5 Sternen, echten zulässigen Verzauberungen und persistenten Itemstats. Neue spezies-/levelgebundene Essenzen und gemeinsame Beutetabellen; Native-Drops der generierten Mobs bleiben erhalten.
- Hot-/Cold-Hysterese, Kampf-Aufwachen, Wiederherstellen ursprünglicher AI, begrenzte zentrale Arbeit, Mobregistry statt wiederholter globaler Entity-Suchen, begrenzte Namensschild-Updates und geteilte Radar-Chunkdaten.
- Arena-Tickets laden keine fehlenden Chunks. Kalte feste Arenen werden nach 60 Sekunden bereinigt; Generationsmarkierung verhindert spätere Rückkehr alter Exemplare aus entladenen Chunks.
- Bossangriffe mit Vorwarnzeit, Abklingzeit, Leine, Ausweichen, Fernkampf und begrenzten Minions. Zusätzliche Pfeile werden aufgeräumt, kalte Angriffe nicht nachgeholt. Kreative, Zuschauer und NPCs sind keine Ziele.
- Native Nahkampf-/Projektilskalierung der Katalogmobs; eigene Spezialprojektile werden nicht doppelt verstärkt. Bestehende Drops werden vorkompiliert und über EntityDeathEvent ausgegeben.
- `/mob mobs`, `/mob items`, `/mob item` und `/mob performance`; Eier für jede Definition, paginierte Listen und kurze BattlePass-Ausgabe. Die öffentliche API 1.0 und der BattlePass-Fix bleiben enthalten. Keine Ersetzung natürlicher Spawns.

# TheMob 1.11.1

- BattlePass-Anbindung startet auch ohne PlaceholderAPI/Vault. Die bisherige Reflexion über sämtliche Methoden von BattlePlugin löste optionale Rückgabetypen auf und brach mit `NoClassDefFoundError: me/clip/placeholderapi/expansion/PlaceholderExpansion` ab.
- Die benötigte `ActionRegistry` wird über ihren exakten statischen API-Zugriff aufgelöst; die fremde Hauptklasse wird nicht mehr reflektiert.
- Alle acht echten BattlePass-API-Tests laufen mit ausdrücklich fehlenden PAPI-/Vault-Klassen. Ein neuer Test reproduziert den alten Startfehler, ein weiterer prüft die native Questpipeline bis zum Spielerfortschritt von 0 auf 1/20.
- Bestehende Questdateien, Saisons, Spielerstände, API und Ressourcenpaket bleiben erhalten. Nur TheMob-JAR ersetzen und vollständig neu starten.

# TheMob 1.11.0

- BattlePass 5.0.12 direkt mit der bereitgestellten Original-JAR geprüft. Fehlerhafte Registrierung mit `Function` durch Unterstützung der echten `Instantiator[]`-API korrigiert.
- Vier native Aufgabentypen: allgemeine Mobkills, Bosskills, Minionkills und konfigurierbare Aufgabenprofile. Filter für Mobgruppen, Welten, Spawnarten und Bossphasen; zusätzliche BattlePass-Subvariablen.
- `battlepass.yml`, sechs geprüfte YAML-Questvorlagen, `/mob battlepass` für Status/IDs/Profilfehler und ausführliche Einrichtung in BATTLEPASS.md. Fehlerhafte Filter deaktivieren das jeweilige Profil.
- Vorhandene API 1.0 bleibt kompatibel. Einmalige native Todesereignisse liefern Fortschritt; NPCs, administrative Entfernungen und standardmäßig Minions bleiben ausgeschlossen.
- Vier Profiltests und sechs separate Tests mit echten BattlePass-API-Klassen ergänzen die bisherige Regression. Keine Premium-JAR oder BattlePass-Spielerstände im Release enthalten.

# TheMob 1.10.2

- Kompass und goldener Pfeil stehen über den nativen Bossbars (y=8/18). Zwei unsichtbare BetterHud-Trägerzeilen reservieren den Kopfbereich; native Bossbars werden nicht mehr mit dem HUD-Träger zusammengeführt.
- Bestehende BetterHud-Positionen werden mit Sicherung migriert. Ein Startcallback verhindert, dass CoinOverlay 1.7.4 den Kompass beim Paketbau wieder unter die Bossbars setzt.
- Die tatsächliche Minecraft-26.3-Zeilenhöhe wird im gemeinsamen Paket kompensiert; übrige HUD-Positionen und alle Mobmechaniken bleiben erhalten.
- Fertiges gemeinsames CoinOverlay-Clientpaket für den bestätigten aktuellen Serverexport. Vollständiger Neustart und aktualisierte Paket-SHA-1 erforderlich.

# TheMob 1.10.1

- Der Kompass nutzt das gemeinsame CoinOverlay-HUD-Paket. TheMob beobachtet dessen Paket-ID/Ladestatus und sendet keine zweite Join-Anfrage.
- `/mobkompass reload` baut über CoinOverlay; `/mobkompass pack` nutzt dessen Paketanfrage. Bestehende TheMob-Paket-URLs greifen im gemeinsamen Betrieb nicht dazwischen.
- Das Serverpaket enthält keine separaten Kompass-/Kombinations-ZIPs mehr. Nach dem Update das aktuelle CoinOverlay-Paket einmal gemeinsam neu bauen und veröffentlichen.

# TheMob 1.10.0

- Öffentliche API 1.0: ServicesManager-Service, unveränderliche Mobdaten, geschützter Spawn, administratives Entfernen sowie Spawn-/Tod-/Phasenereignisse. Eigene kleine API-JAR und geprüftes Add-on-Beispiel.
- Optionale Adapter für Jobs Reborn, Citizens 2, PikaMug Quests, AdvancedPlugins BattlePass v4, PlaceholderAPI, Vault und WorldGuard. Status über `/mob integrations`; Konfiguration und Prüfgrenzen in INTEGRATIONEN.md.
- CoinOverlay-Regenbogenkompass mit identischer Farb-/Pfeilgestaltung, eigenen konfliktfreien BetterHud-IDs, persönlichen/serverweiten Einstellungen und Paketladeprüfung. Mob-Radar bleibt bestehen.
- Zwei fertige Minecraft-26.3-Clientpakete samt SHA-1 und mitgeliefertes BetterHud im Serverpaket. Zusätzliche HUDs können auf dem Server neu gebaut werden.
- Regionsprüfung nach tatsächlicher Spawnortberechnung; abgewiesene manuelle Spawns werden nicht mehr als Erfolg gemeldet. Administratives Entfernen räumt Bossphasen und Spawnkapazität auf.
- Unveränderte acht Original-Mobvorlagen und vorhandene Spigot-Korrekturen. Vollständige Regression ohne übersprungene Tests; echte Fremdplugin-/Clienttests bleiben ausdrücklich offen.

# TheMob 1.9.1 – 24. Oktober 2026

- Spigot 26.3 zusätzlich zu Paper 26.3 unterstützt; Produktionscode gegen Spigot statt Paper kompiliert. Alle Bukkit-Methoden-/Feldreferenzen der kompilierten Klassen werden mit isolierter Spigot-API geprüft.
- Gemeldete Fehler in `AutoSpawnManager`, `NaviHudService` und `BossPhaseController` beseitigt: gemeinsamer Scheduler-Tickzähler und Bukkit-Titel mit unveränderter Anzeigedauer ersetzen Paper-Aufrufe.
- Auch Minion-Wellen und Spawn-Feedback verwenden den gemeinsamen Tickzähler. TPS-Drosselung misst reale Tickintervalle und berücksichtigt lange Hänger.
- Itemnamen/Lore, rote Boss-Teams, Kopftexturen, eigene Ausrüstungsmodifikatoren und Minion-Suche auf gemeinsame Bukkit-APIs umgestellt. Kopftextur-JSON wird auch mit Leerzeichen verarbeitet.
- Bukkit-BASE64-Itemdaten weiterhin mit Größen-/Objektlimits eingelesen; Regressionstest erhält Namen und PDC-Werte. Optionales Paper-NBT wird ohne feste Paper-Verknüpfung gelesen.
- Neue Regressionstests für den gemeldeten Auto-Spawn-Befehl, Itemdaten, Tick-/TPS-Verhalten, Kopftexturen und Spigot-Verknüpfung ergänzt. Boss-Phasentest prüft jetzt auch Titel an einen nahen Spieler.
- Acht ursprüngliche Mobvorlagen unverändert; keine neue öffentliche Integrations-API und keine Änderung der konfigurierten Spawnintervalle oder Bossphasen.

# TheMob 1.9.0 – 24. Oktober 2026

- Eigenständig baubares Gradle-/IntelliJ-Projekt aus dem vorhandenen Plugin wiederhergestellt; Paper 26.3 und Java 25 als Ziel gesetzt.
- Zwangstod nach vermeintlich tödlichen Treffern entfernt. Aufgelöste Damage-Events bleiben maßgeblich; nachträglich abgewiesene Treffer erzeugen keine Heilung.
- Vanilla-Waffenschaden erhalten, eigene Itemwerte einmal erfasst, Extraschaden und Krit-Schlüssel vereinheitlicht. Bestehende höhere Kritfaktoren funktionieren; Lebensraub wird einmal in Prozent berechnet.
- Vanilla-Rüstung und eigene Verteidigung getrennt. Spielerboni verwenden eigene temporäre Attributmodifikatoren und verändern nicht mehr die Basiswerte anderer Plugins. Ausrüstungswechsel werden aus tatsächlichen Slots nachgeführt.
- Stats-Menü über eigenen InventoryHolder statt Fenstertitel geschützt; Klicks und Drags gesperrt. Menügröße, Slots, Materialien und Tierwerte robuster behandelt.
- Paketimport mit Pfadprüfung, Größenlimits, Staging, Rollback und Schutz vorhandener Dateien versehen. Alte Java-Itemserialisierung bleibt unter begrenzter Objektfilterung lesbar.
- Alle Custom-Mob-Spawnquellen verwenden Gate/Budgets; Minions prüfen die Minion-Rolle. Vanilla-Spawns an verknüpften Block-Spawnern werden unterbunden. Block-Spawner werden indiziert, statt jede Sekunde sämtliche Block-Entities zu durchsuchen.
- TPS-/Spielerskalierung der Intervalle wird nicht mehr überschrieben. Überlappende Arena-Chunk-Tickets werden gezählt und beim Verlassen freigegeben. Radar lädt beim Scannen keine fehlenden Chunks nach.
- Bossphasen pro Entity isoliert. Phasenschaden greift auch gegen Spieler. Phasenfähigkeiten und Kampfdaten werden beim Verlassen auf den vorherigen Zustand zurückgeführt; Schadensbuffs werden dem Angriffsattribut zugeordnet.
- Boss-XP ausschließlich über einen Todes-/Drop-Pfad vergeben. Minion-No-Drops unterbindet auch Vanilla-Drops und XP. Cleanup vergibt keine Belohnungen.
- Lokale Wetter-/Zeiteffekte pro Boss verwaltet und beim Verlassen zurückgesetzt. HUD-Konfiguration, Radarprioritäten und Kampfausblendung angeschlossen; Anzeigen pro Spieler getrennt.
- Reload beendet sämtliche Pluginaufgaben und Listener. Temporäre Anzeigen, Minions und alte Sitzungsentities werden bereinigt; Cooldowns/Auto-Spawns werden atomarer gespeichert, Speicherfehler gemeldet.
- Unterbefehlrechte korrigiert, Progressionszustand gemeinsam verwendet und vorhandener Loot-Dienst angeschlossen.
- Mobvorlagen, Item-PDC-Schlüssel, Befehle, Spawnarten und Datenordner erhalten. Die spätere externe Integrations-API ist noch nicht Bestandteil dieses Builds.
