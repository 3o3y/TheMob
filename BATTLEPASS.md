# BattlePass mit TheMob 1.17.0

Native Anbindung für **AdvancedPlugins BattlePass 5.0.12**, Minecraft **26.3**, Java **25**. TheMob liefert Kampf- und Beuteereignisse an BattlePass. BattlePass verwaltet aktive Aufgaben, Fortschritt, Speicherung, Premiumzugang und Belohnungen. Eure gekaufte BattlePass-JAR wird unverändert verwendet und nicht mitgeliefert.

## Admin-Einrichtung

1. Bei gestopptem Server die TheMob-JAR austauschen; Datenordner behalten. Eigene BattlePass-JAR und deren Abhängigkeiten installieren.
2. Vollständig starten und `/mob connect check`, danach `/mob battlepass` ausführen. BattlePass muss ACTIVE sein.
3. Gewünschte Einträge **unter quests:** aus `plugins/TheMob/battlepass-combat-examples.yml` in eine **aktive** BattlePass-Woche übernehmen. Die Datei enthält 20 Vorlagen. Freie Quest-IDs wählen und Punkte/Mengen abstimmen. Vorhandene Wochen/Saisons/Spielerdaten behalten.
4. `/bpa reload` lädt die Aufgaben. Ein normaler Phasen-/Beutefilter braucht keinen TheMob-Reload. Nur geänderte Killgruppen in TheMob/battlepass.yml benötigen `/mob reload`; dieser entfernt aktive TheMob-Mobs.

Die bisherigen battlepass-examples.yml, battlepass-dungeons.yml und Killprofile bleiben verwendbar. Vorlagen werden niemals automatisch zu aktiven Aufgaben. `/mob connect battlepass enable|disable` merkt Verbindungsänderungen für den vollständigen Neustart vor. [Adminhilfe](ADMIN-VERBINDUNGEN.md).

## Neun Aufgabentypen

| Typ | Wurzel variable/root | Fortschritt |
|---|---|---|
| themob_kill | Mob-ID oder none | 1 pro berechtigtem Kill, auch Bosse |
| themob_boss_kill | Boss-ID oder none | 1 pro Bosskill |
| themob_minion_kill | Elternboss-ID oder none | 1 pro freigegebenem Minionkill |
| themob_objective | Killprofil-ID aus TheMob/battlepass.yml | 1 pro passendem Kill |
| themob_phase_enter | Neue Phasen-ID oder none | 1 pro echtem Phasenwechsel für aktive Kämpfer |
| themob_damage | Mob-ID oder none | Effektiver HP-Schaden mit Nachkommastellen |
| themob_drop | Material, z.B. diamond, oder none | Erzeugte Itemmenge aus eigenem Kill |
| themob_loot | Material, z.B. netherite_sword, oder none | Tatsächlich aufgenommene Itemmenge aus TheMob-Beute |
| themob_dungeon_complete | Dungeon-ID oder none | 1 pro bestätigtem Abschluss und Teilnehmer |

Ein Bosskill kann verschiedene Aufgabenfamilien bedienen; eine Quest hat einen Typ. Drop und Loot sind getrennte Ziele. Nutzt für dieselbe Belohnung einen gewünschten Weg. BattlePass-eigene Booster können Fortschritt multiplizieren.

## Filter

Unter variable: stehen root: und die gewünschten Subfilter. Alle Filter sind gemeinsam erforderlich. Werte als Strings angeben. none hebt nur den Rootfilter auf.

| Familien | Filter |
|---|---|
| Kills, Killprofile, Phasen, Schaden | mob, world, phase, spawn-reason, entity-type, boss, minion, level, min-level, max-level |
| Phasen zusätzlich | previous-phase |
| Drop und Loot | mob, world, source-world, phase, spawn-reason, boss, minion, level, min-level, max-level, item, rarity, stars, min-stars, max-stars, custom, own-kill |
| Dungeonabschluss | dungeon, style, world, boss (Boss-ID), levels |

**phase** ist die konfigurierte Phasen-ID, keine übersetzte Kampftaktik. Beim Zombie-König: phase1 bis phase4, passend zu Royal/Hunt/Frenzy/Desperate. Ohne Phase gilt unphased. Spawnarten: manual, auto_spawn, egg, spawner, minion, npc, api. Entitytypen in Kleinbuchstaben; Booleans als 'true'/'false'.

**level** ist das konfigurierte Moblevel, bei Minions das Elternboss-Level. Numerische Grenzen sind ganze Zahlen: Sterne 0–5, Level 1–1.000.000. Ungültige Grenzwerte zählen nicht. 0 Sterne bedeutet kein Sternenitem.

**rarity**: rare, epic, legendary, custom, vanilla. Rare/Epic kommen aus nativen Katalog-Metadaten, Legendary aus den bisherigen legendary-drops. Namen/Lore allein ergeben keine Seltenheit. **item**: z.B. epic_sword_5, essence:zombie:5 oder minecraft:diamond. **own-kill: 'true'** verlangt, dass der Sammler ursprünglicher Killer ist; bei Drop ist das bereits Voraussetzung. source-world ist die Todeswelt, world die aktuelle Drop-/Aufnahmewelt.

Beispiel: 5 Epic-Items mit mindestens drei Sternen aus eigenen Kills sammeln. Unter quests: einer aktiven Woche einsetzen:

```yaml
9221:
  name: 'TheMob - Eigene epische Beute'
  type: themob_loot
  variable:
    root: none
    rarity: epic
    min-stars: '3'
    own-kill: 'true'
  required-progress: 5
  points: 30
  exclusive: free
  item:
    material: netherite_sword
    name: '&5Epische Beute'
    lore: ['&7Sammle Epic-Beute deiner Kills.', '&e%total_progress%&7/&e%required_progress%']
```

Für Schaden in der letzten Zombie-König-Phase: type: themob_damage, variable.root: zombie_boss_template, variable.phase: phase4. Für den Wechsel selbst: type: themob_phase_enter, variable.root: phase4, variable.mob: zombie_boss_template.

BattlePass wertet globale Bedingungen wie holding selbst aus. Sie beziehen sich auf die Ausrüstung beim Verarbeiten der Aktion.

## Zählregeln

- **Kills:** Bukkit muss einen Spielerkiller zuordnen; NPC-Killer/-Opfer und Umwelttode zählen nicht. Minions standardmäßig gesperrt; integrations.track-minion-kills: true schaltet sie gemeinsam für die eingebauten Integrationen frei.
- **Phasen:** Kein Fortschritt beim Spawn. Ein echter Wechsel zählt für Spieler mit wirksamem Schaden innerhalb der letzten 120 Sekunden, die noch lebend im Arenaradius sind. Bloße Umstehende, Kreativspieler, Zuschauer und NPCs ausgeschlossen. Die vorhandene monotone Bosssteuerung verhindert Zurücksetzen durch Heilung.
- **Schaden:** Spielerangriffe, Spielerprojektile und gezähmte Tiere mit Spielerbesitzer. Finaler Gesundheitsschaden nach Abzügen, auf die verbleibenden HP begrenzt; Überkill zählt nicht. Abbruch liefert nichts. Im Folgetick erneute Abbruchprüfung.
- **Drop:** Finale TheMob-Todesdrops müssen einen zugelassenen nativen Itemspawn erzeugen. Menge zählt für den ursprünglichen online/lebend berechtigten Killer. Einsammeln nicht erforderlich.
- **Loot:** Nur zugelassene Spieleraufnahmen nachweisbarer TheMob-Beute. Teilaufnahmen zählen tatsächliche Menge: Stack mit 7 Items zählt 7. Andere Spieler dürfen sammeln, sofern own-kill nicht verlangt wird. Herkunft bleibt an der Boden-Entity über Chunkladen/Neustart erhalten, nicht am Inventaritem. Wegwerfen/erneut Aufheben zählt nicht nochmals. Admin-Give und api.createItem zählen nicht. Unterschiedliche Herkunft verschmilzt nicht; gleiche Quelleigenschaften dürfen stapeln.
- **Dungeon:** Nur festgelegter besiegter Endboss oder autorisierter API-Abschluss. Alle bestätigten Teilnehmer erhalten Fortschritt. Betreten allein zählt nicht. Letzte 256 Run-UUIDs je Dungeon dauerhaft dedupliziert. [Dungeon-Einrichtung](DUNGEON-API.md).
- Normale Vanilla-Mobs, administrative Entfernung, killall, API-Remove und Reload liefern keine künstlichen Kills/Beute. Die Aufgabenerweiterung erzeugt selbst weder Items noch Geld.

## Bestehende Killgruppen

TheMob/battlepass.yml bleibt für themob_objective zuständig. Profile filtern Kills nach mobs, kind, worlds, spawn-reasons, phases. Listen sind Alternativen; Felder werden gemeinsam geprüft. mobs: ['*'] bedeutet alle Definitionen; kind: any, mob, boss, minion. Leere Welt-/Phasen-/Spawnlisten erlauben alle Werte. Fehlerhafte Profile gesperrt und in Konsole sowie /mob battlepass angezeigt. Standardprofile: mobjaeger, bossjaeger, untotenjagd, wilde_jagd.

## Öffentliche Entwickler-API und Prüfung

[API.md](API.md) und [DEVELOPER-GUIDE.md](DEVELOPER-GUIDE.md) beschreiben TheMobDeathEvent, Phasenteilnehmer, TheMobDamageEvent, TheMobDropEvent, TheMobLootEvent, ItemSnapshot und LootSource. Dieselben Ereignisse speisen BattlePass und stehen weiteren Quest-/Jobs-Add-ons offen. API-Major bleibt 1, Artefaktversion 1.2.0. Keine API-JAR in den Server-Pluginordner kopieren.

Der lokale Gradle-Test liest eure echte BattlePass-5.0.12-JAR, ohne sie zu verändern oder zu verteilen:

```powershell
.\gradlew.bat build "-PbattlePassJar=C:\Pfad\BattlePass-5.0.12.jar"
```

Geprüft werden native Registrierung, Filter, Dezimalfortschritt, Stackmengen und echter QuestPipeline-Fortschritt für alle neun Familien ohne PlaceholderAPI und Vault. Ein realer Server mit euren aktiven Wochen und Belohnungen wurde nicht gestartet.
