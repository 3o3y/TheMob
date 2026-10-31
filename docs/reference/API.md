# TheMob API 1.3.1 / Plugin 1.19.1

Das öffentliche SDK enthält getrennte API-JARs, Quellen, JavaDoc, einen lokalen Maven-Repository-Ordner und ein eigenständig baubares Add-on. Lizenz: [API-LICENSE.txt](API-LICENSE.md), Entwicklerstart: [DEVELOPER-GUIDE.md](DEVELOPER-GUIDE.md), Adminstart: [ADMIN-VERBINDUNGEN.md](ADMIN-VERBINDUNGEN.md). Die Erweiterung ist additiv und erhält die vorhandenen Methoden und Konstruktoren.

Die generierten Levelmobs und Bossarchetypen stehen in `definitions()` und können mit denselben IDs über `spawn(...)` erzeugt werden. `activeMobs()` fragt die Registry der geladenen TheMob-Entities ab; Chunkwechsel pflegen diese Registry.

BattlePass 5.0.12 nutzt diese Todesereignisse automatisch für native Aufgaben. Über `spawn(...)` erzeugte Mobs zählen ebenfalls; das API-Format bleibt kompatibel. Aufgabentypen, Filter und Konfiguration stehen in [BATTLEPASS.md](BATTLEPASS.md).

Öffentliches Paket: `org.plugin.theMob.api`. Die kleine `TheMob-API-1.3.1.jar` dient ausschließlich als `compileOnly`-Abhängigkeit für Add-ons. Sie enthält Definitionen, Snapshots, Serviceinterface und Ereignisse. Sie gehört nicht als zusätzliches Plugin auf den Server und darf nicht in ein Add-on hineinkopiert oder geschattet werden. Im laufenden TheMob ist die API bereits enthalten. `API_VERSION` bleibt 1; vorhandene Add-ons mit API 1.0/1.1 bleiben kompatibel.

Der API-Code ist Java-17-Bytecode und unterstützt Bukkit/Spigot/Paper 1.18–26.3. Der Server benötigt die zu seiner Minecraft-Version passende Java-Laufzeit. Das vollständige Gradle-Projekt wird mit JDK 25 gebaut; Details: [Kompatibilität](KOMPATIBILITAET.md). Das eigene Add-on setzt in `plugin.yml` `depend: [TheMob]` oder prüft bei `softdepend` die Verfügbarkeit. Alle API-Abfragen und Mutationen erfolgen auf dem Serverthread. Die Serviceinstanz bleibt über `/mob reload` hinweg bestehen; gespeicherte Entity-Referenzen verlieren dabei wie bisher ihre Gültigkeit.

```kotlin
dependencies {
    compileOnly(files("libraries/TheMob-API-1.3.1.jar"))
    compileOnly("org.spigotmc:spigot-api:26.3-R0.1-SNAPSHOT")
}
```

```java
TheMobApi api = Bukkit.getServicesManager().load(TheMobApi.class);
if (api == null || api.apiVersion() != 1) {
    throw new IllegalStateException("TheMob API 1 fehlt");
}
api.spawn("pig_lvl_1", player.getLocation()).ifPresent(mob -> {
    getLogger().info("Mob erzeugt: " + mob.getUniqueId());
});
```

## Service und Daten

| Methode | Vertrag |
|---|---|
| `definitions()` | Unveränderliche Liste gültiger Mobdefinitionen mit ID, Name, EntityType und Bosskennung |
| `mob(LivingEntity)` | Optionaler Snapshot; funktioniert auch im Todesereignis |
| `activeMobs()` | Snapshots gültiger lebender TheMob-Entities in geladenen Welten, einschließlich zugeordneter Minions |
| `spawn(String, Location)` | Normaler Spawnweg mit Budget, Boss-Cooldown und abbrechbarem PreSpawn; leeres Optional bei Abweisung |
| `remove(UUID)` | Administratives Entfernen ohne Kill, Loot, XP oder Questfortschritt; Bosszustand und Spawnkapazität werden freigegeben |
| `integrations()` | Status der 20 optionalen Anbieter |
| `supportsCombatObjectives()` | Ab 1.17 true: Kampf-/Beuteereignisse und Item-Metadaten verfügbar |
| `definitionLevel(id)` | Konfiguriertes Level 1–1.000.000; unbekannt/null ergibt 0 |
| `itemIds()` | Unveränderliche sortierte IDs der 160 Rare-/Epic-Katalogitems |
| `createItem(id)` | Kopie eines Katalogitems; unbekannt/null ergibt leer. Aufrufer prüft Rechte und Inventarreste. Erzeugt keinen Beutefortschritt. |
| `inspectItem(stack)` | Unveränderlicher `ItemSnapshot`, inklusive Material, ID, Seltenheit, Sternen und Menge; null/Luft ergibt leer |
| `supportsDungeonRegions()` | Ab TheMob 1.15.0 true; neue optionale Fähigkeit innerhalb API-Version 1 |
| `dungeons()` | Unveränderliche Snapshots aller reservierten Dungeon-Grundflächen, auch unfertiger/abgebrochener Bauten; lädt keine Chunks |
| `dungeonAt(Location)` | Optionaler Snapshot der reservierten Grundfläche an der Position; Chunkindex-Abfrage ohne Chunkladen; null/fremde Welt/außerhalb ergibt leer |

## Dungeonbereiche und Schutzplugins

`DungeonRegion` enthält `id`, `worldId`, `minX`, `minZ`, `maxX`, `maxZ` und `ready`. Minimale Grenzen sind inklusive, maximale exklusiv. Die Grundfläche gilt wie der Dungeon-Bauschutz über die gesamte Welthöhe. `ready` ist nur bei einem fertigen Dungeon mit verfügbarer Dungeonverwaltung true. `contains(Location)` prüft Welt und horizontale Grenzen. Bereits abgefragte Snapshots sind unveränderlich; nach Zustandsänderungen oder `/mob reload` neu abfragen. Die Serviceinstanz bleibt erhalten und liest die jeweils aktuelle Dungeonverwaltung.

PKPlugin 0.7.1 nutzt diese API: In fertigen Grundflächen gelten freie Bewegung, Dungeon-Teleports und Respawns ohne eigenes PK-Gebiet; im gesamten reservierten Baufeld sind PK-Banner und übergreifende PK-Gebäudebauten gesperrt. Außerhalb gelten sofort wieder die normalen Gebietsrechte. Schutzplugins sollten auch das Teleportziel prüfen, da der Spieler beim Teleportereignis noch am Ausgangspunkt steht. Reine Befehls-/Sitzungsmarkierungen sind dafür nicht erforderlich. TheMobs Berechtigungsprüfung und bestehender Dungeon-Bauschutz bleiben zuständig.

```java
if (api.supportsDungeonRegions()) {
    api.dungeonAt(event.getTo()).filter(DungeonRegion::ready).ifPresent(region -> {
        // Eigene Bewegungsprüfung für dieses Ziel passend behandeln.
    });
}
// Banner-/Gebietserstellung: jedes vorhandene dungeonAt(...) sperren, auch wenn ready false ist.
```

Wer ältere TheMob-JARs unterstützt, muss die gesamte optionale Anbindung einschließlich der neuen API-Typen isoliert laden und eine fehlende API-Fähigkeit behandeln. PKPlugin protokolliert in diesem Fall den nötigen TheMob-Updatehinweis; normale PK-Regeln bleiben aktiv.

`MobSnapshot` enthält Entity-UUID, `definitionId`, Anzeigename, `boss`, `minion`, optionale `phaseId` und `spawnReason`. Bei Minions ist `definitionId` die ID des Elternbosses, `boss` bleibt false und `spawnReason` ist MINION. Es wird dadurch keine normale Mobdefinition auf den Minion übertragen. Gründe: MANUAL, AUTO_SPAWN, EGG, SPAWNER, MINION, NPC und API.

## Ereignisse

- `TheMobPreSpawnEvent`: abbrechbar, bevor die Entity erzeugt wird; enthält Definition, berechneten Spawnort einschließlich Boden-/Höhenkorrektur und Grund. `getLocation()` liefert eine Kopie. Für Minions bezeichnet die Definition den Elternboss.
- `TheMobSpawnEvent`: nach Initialisierung und Budgetregistrierung. Die Startphase eines Bosses ist bereits im Snapshot vorhanden.
- `TheMobPhaseChangeEvent`: Startphase nach dem Spawnereignis mit `previousPhase == null`, danach tatsächliche Wechsel mit vorheriger und neuer Phase. Minion-Spawnereignisse aus Phasenaktionen können während der Bossinitialisierung auftreten.
- `TheMobDeathEvent`: einmal pro echtem nativen Tod; enthält den von Bukkit ermittelten, gegebenenfalls fehlenden Spielerkiller. Reload, Killall, API-Remove und Minion-Cleanup lösen dieses Ereignis nicht aus.
- `TheMobDamageEvent`: bestätigter nicht abgebrochener Spielerschaden, auch Projektile/gezähmte Tiere. `getDamage()` ist positiver finaler HP-Schaden, auf die verbliebene Gesundheit begrenzt. Veröffentlicht im nächsten Tick, nach erneuter Abbruchprüfung; Opfer kann inzwischen tot sein.
- `TheMobDropEvent`: tatsächlich zugelassener nativer Itemspawn aus den endgültigen Todesdrops, im nächsten Tick. Enthält ursprünglichen Mob-Snapshot, `LootSource`, Item-Entity und defensive Itemkopie. Auch spätere Verschmelzung/Despawn ändern die ursprüngliche Menge nicht.
- `TheMobLootEvent`: zugelassene Spieleraufnahme von TheMob-Todesbeute; Teilaufnahmen zählen nur die aufgenommene Menge. Aufsammler und ursprünglicher Killer können verschieden sein. Die Herkunft liegt ausschließlich an der Boden-Entity und überlebt Chunkladen/Neustart; Inventaritems behalten sie nicht. Erneutes Wegwerfen erzeugt keinen weiteren Beutefortschritt.

`TheMobPhaseChangeEvent.getParticipants()` enthält bei echten Phasenwechseln aktive Kämpfer mit Schaden innerhalb der letzten 120 Sekunden, die noch lebend und im Arenaradius sind. Kreativmodus, Zuschauer, NPCs und bloße Umstehende zählen nicht. Bei der Startphase ist die Liste leer. Der alte Vier-Argument-Konstruktor bleibt erhalten und liefert eine leere Liste.

`ItemSnapshot` liest native Metadaten statt Namen/Lore: `rare`, `epic`, `legendary`, `custom` oder `vanilla`; Sterne 0–5, wobei 0 kein Sternenitem bezeichnet. Katalog-ID z.B. `epic_sword_5`, Essenz-ID `essence:zombie:5`, sonst `minecraft:diamond`. `LootSource` enthält Mob-ID, Todesphase, Spawnart, Boss-/Minionkennung, Level, Ursprungswelt und optionalen Killer. Er beschreibt gleichartige Herkunft, keine einzelne Mob-Entity-UUID. Nur identische Herkunft darf verschmelzen; normale gleichartige Drops derselben Herkunft können weiterhin gestapelt werden. Items ohne Herkunft, mit anderem Killer oder anderer Mobquelle verschmelzen nicht mit diesen Drops.

```java
@EventHandler
public void onDeath(TheMobDeathEvent event) {
    if (event.getKiller() == null || event.getMob().minion()) return;
    String mobId = event.getMob().definitionId();
    // Eigenen Fortschritt genau hier verbuchen.
}
```

Die API-Ereignisse sind Beobachtungen, keine Aufträge zur Auszahlung. Nicht selbst erzeugen/erneut versenden und nicht parallel Bukkit- und TheMob-Ereignis für dasselbe Ziel zählen. Es gibt keine zusätzliche Damage-Rangliste; Quests verarbeitet seinen eigenen Gruppenfortschritt, Jobs seine eigene Zahlungslogik. Implementierungsdetails unter `api.internal`, `integration` und den übrigen Pluginpaketen gehören nicht zum stabilen API-Vertrag.

Ein kleines Add-on-Beispiel steht unter `examples/addon/`. Der Integrationsumfang und die offenen praktischen Tests stehen in `INTEGRATIONEN.md` und `VALIDATION.md`.

## Dungeon-Erweiterung in 1.14.0

Freie Bewegung mit PKPlugin 0.7.1, Endboss-Zuordnung, Gruppenabschluss, `themob_dungeon_complete` und öffentliche Abschlussereignisse stehen in [DUNGEON-API.md](DUNGEON-API.md). `/mob dungeon boss ID Boss-Mob-ID` legt den Endboss fest; das Betreten allein wertet keinen Dungeonabschluss.

## Ausgangsaltar ab 1.15.0

Im Bossraum gibt es jetzt einen physischen Ausgangsaltar mit PK-Zielauswahl und Rückreise. Vorhandene besuchte Dungeons werden nachgerüstet. `/mob dungeon altar ID [here]` ergänzt, repariert oder versetzt den Altar. TheMob 1.19.1 und eine passende PKPlugin-Version (mindestens 0.7.2 für Reisen) verwenden. Anleitung: [AUSGANGSALTAR.md](AUSGANGSALTAR.md). Die Reise-API liegt in TheMob-Dungeon-API-1.3.1.jar; Haupt-API 1.1 und bestehende BattlePass-Ziele bleiben kompatibel.

## Additive API 1.3: Menüs, Itemprovider und Dungeonbau

TheMob 1.19.1 ergänzt `supportsAdminMenu()` und `openAdminMenu(Player)`. Letzteres öffnet nur für berechtigte Spieler das normale Adminmenü. `itemProviders()` liefert verfügbare Namespaces. `createItem` unterstützt zusätzlich `provider:ID`; der Aufrufer kümmert sich weiterhin um Berechtigung, Zustellung und Inventarüberläufe. Es entsteht keine Shoptransaktion.

`AdminMenuExtension` und `TheMobItemProvider` werden über den Bukkit ServicesManager als eigene Dienste registriert; beim Disable wieder abmelden. Nur aktive registrierte Plugins werden berücksichtigt. Menüerweiterungen müssen zusätzlich ihre eigenen Rechte und Mutationen prüfen. Itemfabriken werden auf dem Serverthread ausgeführt, müssen bei unbekannter ID `Optional.empty()` zurückgeben und dürfen keine Belohnung selbst zustellen. TheMob klont die Ergebnisse. `themob` ist als Itemnamespace reserviert; bei gleichnamigen Add-on-Providern gewinnt die höhere Bukkit-Servicepriorität. Eigene Anbieter sollten eindeutige IDs verwenden.

`TheMobDungeonPreCreateEvent` ist synchron und abbrechbar. Es wird vor Reservierung/Planung eines Neubaus und vor Fortsetzung aufgerufen. `getSender()` enthält Spieler oder Konsole; `getPlayer()` ist für Konsolen-Fortsetzungen null. `getRegion()` beschreibt die vollständige höhenunabhängige Grundfläche mit exklusiver Obergrenze, bei Neubau mit vorläufiger ID `pending`. `getStyle()` verwendet dieselben Stil-Labels wie Dungeonabschlüsse. Das Ereignis erlaubt Schutz-/Claimplugins den Bau abzulehnen; nachträgliche Änderungen an Claims sind damit nicht automatisch geschützt.

Die API-Artefaktversion ist 1.3.1; der Kompatibilitätsmajor `apiVersion()` bleibt 1. Neue 1.3-Klassen erfordern TheMob 1.19.1 oder eine entsprechende Capability-/Versionsbrücke. Alte API-1.0/1.1/1.2-Add-ons bleiben binär kompatibel. Native Fremditems behalten ihre Originalmetadaten; `inspectItem` kann ihre ID im begrenzten laufenden Fabrikcache erkennen, aber nicht nach Neustart aus jedem beliebigen Inventaritem rekonstruieren. Der persistierte Drop-/Loot-Snapshot am Boden-Entity erhält die ID unabhängig davon.

Beispiel und genaue Funktionsgrenzen: [SDK](DEVELOPER-GUIDE.md), [20 Anbindungen](INTEGRATIONEN-20.md), [Adminmenü](ADMIN-MENUE.md).
