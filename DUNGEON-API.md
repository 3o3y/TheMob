# Dungeon-API 1.3.1 / TheMob 1.19.0

Die API-1.3.1-Distribution erhält sämtliche bisherigen Dungeon-Verträge. SDK, Quellen, JavaDoc und Beispielprojekt: [DEVELOPER-GUIDE.md](DEVELOPER-GUIDE.md). Die folgenden Versionsangaben beschreiben die Einführung der jeweiligen Funktion; bestehende PK-Anbindungen bleiben kompatibel.

## Zusammenspiel mit PKPlugin

Mit **TheMob 1.14.0 und PKPlugin 0.7.1** erkennt PK die gespeicherten Dungeonflächen über die öffentliche TheMob-API. Bestehende Dungeons müssen nicht erneut gebaut werden.

- Teleport zu einem fertigen Dungeon, Laufen auf allen Stockwerken, Fahrzeuge, Login und Respawn im fertigen Dungeon benötigen kein eigenes PK-Gebiet.
- Innerhalb der Fläche werden keine PK-Grenzpartikel gezeichnet. Das vergibt keine zusätzlichen Bau- oder Abbaurechte; der TheMob-Bauschutz bleibt aktiv.
- Außerhalb gelten wieder die normalen PK-Regeln. Es wird kein persönliches `/pk bypass` aktiviert und kein Bannergebiet vergrößert.
- PK-Banner, auch neutrale Admin-Banner, dürfen die reservierten Dungeonflächen nicht beanspruchen. Auch nicht fertige Anlagen bleiben reserviert. PK-Gebäude dürfen diese Flächen nicht überschreiben.
- Beide Plugins ersetzen und vollständig neu starten. Die Entwickler-API-JARs gehören nicht in den Serverordner `plugins`; die benötigten Klassen sind bereits in TheMob enthalten.

PK verwendet `TheMobApi` aus **TheMob-API-1.3.1.jar** mit `supportsDungeonRegions()`, `dungeonAt(Location)` und `dungeons()`. `DungeonRegion` ist ein unveränderlicher Snapshot nach Welt-UUID mit exklusiven oberen X/Z-Grenzen. `ready()` ist nur bei einer fertigen, verfügbaren Anlage wahr. Die Abfrage gilt unabhängig von der Höhe und lädt keine Chunks. Ältere API-Clients bleiben binär kompatibel.

## Endboss und Dungeonabschluss

Ein Dungeon zählt erst nach dem **Spielerkill des festgelegten TheMob-Endbosses** als bezwungen. Das bloße Betreten erzeugt keinen Erfolg. Als Admin beispielsweise:

```text
/mob dungeon boss d00001 tm_boss_warlord_lvl_5
/mob dungeon info d00001
```

Der Befehl merkt sich die Boss-Mob-ID, setzt aber keinen Boss ein. Diesen platziert ihr anschließend selbst mit TheMob-Spawns, Eiern oder einem eingerichteten Spawner im Dungeon. Mit `/mob dungeon boss d00001 off` schaltet ihr die automatische Abschlusswertung wieder aus. Gewöhnliche Mobs, Minions, andere Bosstypen, Umwelttode oder Kills von außerhalb der Dungeonfläche werten keinen Dungeonabschluss.

Beim passenden Kill erhalten **alle online anwesenden, lebenden Spieler innerhalb derselben Dungeonfläche** den Abschluss. Zuschauer und Citizens-/NPC-Spieler sind ausgeschlossen. Der Killer muss ebenfalls im Dungeon sein. Die Gruppe ist hier die Menge der anwesenden Spieler; ein Party-Plugin ist nicht erforderlich. Jeder neu gespawnte passende Endboss kann einen neuen Durchlauf abschließen. Die UUID des besiegten Bosses ist die Durchlauf-ID.

Der Abschluss wird vor dem öffentlichen Ereignis gespeichert. Die letzten **256 Durchlauf-IDs je Dungeon** werden gegen Wiederholung abgesichert, einschließlich Reload/Neustart. API-Aufrufer müssen für jeden tatsächlichen neuen Durchlauf eine neue UUID verwenden und dürfen alte IDs nicht wiederverwenden. Endboss-Zuordnung und Abschlussdaten ergänzen die vorhandene `dungeons.yml`; alte Dungeons behalten ihre Positionen und Bauten.

## BattlePass einrichten

Neuer Aufgabentyp: **`themob_dungeon_complete`**. `variable: d00001` zählt nur diese Anlage; `variable: none` zählt jeden gültigen Dungeonabschluss. Die Beispiele werden unter `plugins/TheMob/battlepass-dungeons.yml` neu angelegt. Bestehende aktive BattlePass-Wochen und Spielerstände werden nicht überschrieben.

Den gewünschten Eintrag mit einer freien Quest-ID in eine aktive BattlePass-Woche unter `quests:` übernehmen und `/bpa reload` ausführen:

```yaml
quests:
  9107:
    name: 'Dungeon bezwingen'
    type: themob_dungeon_complete
    variable: d00001
    required-progress: 1
    points: 30
    exclusive: free
    item:
      material: netherite_sword
      name: '&6Dungeon bezwingen'
      lore: ['&7Besiege den festgelegten Endboss.', '&e%total_progress%&7/&e%required_progress%']
```

Weitere native BattlePass-Filter: `dungeon`, `style`, `world`, `boss`, `levels`. Stilwerte sind `krypta`, `turm`, `mine`, `weltenbaum`, `gottesturm`, `labyrinth`. Beispiel für beliebige Gottestürme:

```yaml
type: themob_dungeon_complete
variable:
  root: none
  style: gottesturm
required-progress: 3
```

Normale Kill-/Bosskillquests laufen zusätzlich weiter. BattlePass muss aktiv sein, und die Aufgabe muss zur aktuellen Woche/Saison des Spielers gehören. Questabschluss, Punkte, Belohnungen und Rücksetzungen werden weiterhin von BattlePass verwaltet. Der neue Typ wurde gegen die echte BattlePass-5.0.12-Questpipeline bis zum nativen Spielerfortschritt geprüft; ein Live-Servertest bleibt erforderlich.

## Erweiterte API für andere Plugins

Für Abschlussereignisse und autoritative Abschlüsse zusätzlich **TheMob-Dungeon-API-1.3.1.jar** als `compileOnly` einbinden, zusammen mit TheMob-API-1.3.1 und Bukkit. Keine dieser APIs in das Add-on schattieren/kopieren. `depend: [TheMob]` verwenden. Alle Abfragen und Abschlüsse erfolgen auf dem Serverthread.

```java
TheMobDungeonApi dungeons = Bukkit.getServicesManager().load(TheMobDungeonApi.class);
// org.plugin.theMob.api.dungeon.TheMobDungeonApi
boolean occupied = dungeons.blocksClaim(location, 32);
var region = dungeons.at(location);
// Nur aus einem vertrauenswürdigen Plugin nach dessen echtem Boss-/Encounterabschluss:
boolean completed = dungeons.complete("d00001", runUuid, participantUuids);
```

`at`, `regions`, `intersecting` verwenden denselben `org.plugin.theMob.api.DungeonRegion`-Typ wie die PK-Anbindung. `blocksClaim` prüft Kreis-/Rechtecküberschneidungen einschließlich reservierter, unfertiger Anlagen. Die räumlichen Abfragen laden keine Welt oder Chunks. `complete` filtert die übergebenen UUIDs auf online anwesende, lebende Spieler im fertigen Dungeon; ohne gültigen Teilnehmer oder bei doppelter Durchlauf-ID liefert es `false`.

Eigene Quest-, Jobs-, Party- oder Boss-Plugins können `DungeonCompletedEvent` abonnieren:

```java
@EventHandler
public void completed(DungeonCompletedEvent event) {
    String dungeonId = event.getDungeon().id();
    UUID run = event.getRunId();
    for (Player player : event.getParticipants()) {
        // Eigene Fortschrittslogik. BattlePass verarbeitet das Ereignis bereits automatisch.
    }
}
```

Das Ereignis liefert außerdem Boss-Mob-ID, Stil, Weltname und Ebenenzahl. Die Teilnehmerliste ist unveränderlich. Bei einem fremden Pluginabschluss lautet der Boss-Filter `api`; die aufrufende Erweiterung ist für die tatsächliche Erfüllung ihres Boss-/Encounterziels verantwortlich. Der Service bleibt über `/mob reload` identisch. Nach Deaktivieren des Plugins oder bei Zugriff vom falschen Thread schlagen API-Zugriffe ausdrücklich fehl.

## Ausgangsaltar ab 1.15.0

Im Bossraum gibt es jetzt einen physischen Ausgangsaltar mit PK-Zielauswahl und Rückreise. Vorhandene besuchte Dungeons werden nachgerüstet. `/mob dungeon altar ID [here]` ergänzt, repariert oder versetzt den Altar. TheMob 1.19.0 und eine passende PKPlugin-Version (mindestens 0.7.2 für Reisen) verwenden. Anleitung: [AUSGANGSALTAR.md](AUSGANGSALTAR.md). Die Reise-API liegt in TheMob-Dungeon-API-1.3.1.jar; Haupt-API 1.1 und bestehende BattlePass-Ziele bleiben kompatibel.
