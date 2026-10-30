# battlepass-combat-examples.yml — configuration example

This is a reference file from TheMob 1.19.0. Keep your tuned production configuration when upgrading. Use materials and entity types supported by your Minecraft version.

```yaml
# TheMob 1.17 / AdvancedPlugins BattlePass 5.0.12 – nur Beispiele.
# Gewuenschte Eintraege unter quests: in eine AKTIVE BattlePass-Woche uebernehmen.
# IDs vorher auf Kollision pruefen. Anschliessend /bpa reload.
# Keine bestehende Woche ersetzen. rewards/points und erforderliche Mengen selbst abstimmen.
# none = beliebige Wurzel. Unterfilter werden UND-verknuepft.
# Phase: aktive Kaempfer, keine Startphase. Schaden: effektive HP, kein Overkill.
# drop = vom Kill erzeugt; loot = wirklich aufgesammelt, Menge statt Stackzahl.
# Weggeworfene Spieleritems erzeugen keine TheMob-Beuteziele.
quests:
  9201:
    name: 'TheMob - Zorn des Zombiekoenigs'
    type: themob_phase_enter
    variable:
      root: 'phase2'
      mob: 'zombie_boss_template'
    required-progress: 3
    points: 20
    exclusive: free
    item:
      material: zombie_head
      name: '&6Zorn des Zombiekoenigs'
      lore: ['&7Erreiche im aktiven Kampf Phase 2.', '&e%total_progress%&7/&e%required_progress%']
  9202:
    name: 'TheMob - Letztes Aufbaeumen'
    type: themob_phase_enter
    variable:
      root: 'phase4'
      mob: 'zombie_boss_template'
    required-progress: 1
    points: 30
    exclusive: free
    item:
      material: totem_of_undying
      name: '&6Letztes Aufbaeumen'
      lore: ['&7Kaempfe bis zur letzten Bossphase.', '&e%total_progress%&7/&e%required_progress%']
  9203:
    name: 'TheMob - Phasenjaeger'
    type: themob_phase_enter
    variable:
      root: 'none'
      boss: 'true'
    required-progress: 10
    points: 25
    exclusive: free
    item:
      material: blaze_powder
      name: '&6Phasenjaeger'
      lore: ['&7Erlebe 10 echte Phasenwechsel als aktiver Kaempfer.', '&e%total_progress%&7/&e%required_progress%']
  9204:
    name: 'TheMob - Bossbrecher'
    type: themob_damage
    variable:
      root: 'none'
      boss: 'true'
    required-progress: 1000
    points: 30
    exclusive: free
    item:
      material: diamond_sword
      name: '&6Bossbrecher'
      lore: ['&7Verursache 1000 wirksamen Schaden an Bossen.', '&e%total_progress%&7/&e%required_progress%']
  9205:
    name: 'TheMob - Zombiekoenig verletzen'
    type: themob_damage
    variable: 'zombie_boss_template'
    required-progress: 500
    points: 20
    exclusive: free
    item:
      material: iron_sword
      name: '&6Zombiekoenig verletzen'
      lore: ['&7Fuege dem Zombiekoenig 500 Schaden zu.', '&e%total_progress%&7/&e%required_progress%']
  9206:
    name: 'TheMob - Die letzte Phase'
    type: themob_damage
    variable:
      root: 'zombie_boss_template'
      phase: 'phase4'
    required-progress: 100
    points: 20
    exclusive: free
    item:
      material: netherite_sword
      name: '&6Die letzte Phase'
      lore: ['&7Verursache Schaden in Phase 4.', '&e%total_progress%&7/&e%required_progress%']
  9207:
    name: 'TheMob - Level 5 Jaeger'
    type: themob_kill
    variable:
      root: 'none'
      level: '5'
      minion: 'false'
    required-progress: 20
    points: 25
    exclusive: free
    item:
      material: iron_sword
      name: '&6Level 5 Jaeger'
      lore: ['&7Besiege 20 Level-5-Mobs.', '&e%total_progress%&7/&e%required_progress%']
  9208:
    name: 'TheMob - Veteranenjagd'
    type: themob_kill
    variable:
      root: 'none'
      min-level: '3'
      max-level: '5'
      minion: 'false'
    required-progress: 40
    points: 25
    exclusive: free
    item:
      material: bow
      name: '&6Veteranenjagd'
      lore: ['&7Besiege Mobs der Level 3 bis 5.', '&e%total_progress%&7/&e%required_progress%']
  9209:
    name: 'TheMob - Boss in seiner letzten Phase'
    type: themob_boss_kill
    variable:
      root: 'zombie_boss_template'
      phase: 'phase4'
    required-progress: 1
    points: 30
    exclusive: free
    item:
      material: zombie_head
      name: '&6Boss in seiner letzten Phase'
      lore: ['&7Besiege den Zombiekoenig in Phase 4.', '&e%total_progress%&7/&e%required_progress%']
  9210:
    name: 'TheMob - Diamanten als Beute'
    type: themob_drop
    variable: 'diamond'
    required-progress: 10
    points: 15
    exclusive: free
    item:
      material: diamond
      name: '&6Diamanten als Beute'
      lore: ['&7Deine TheMob-Kills muessen 10 Diamanten droppen.', '&e%total_progress%&7/&e%required_progress%']
  9211:
    name: 'TheMob - Seltene Beute'
    type: themob_drop
    variable:
      root: 'none'
      rarity: 'rare'
    required-progress: 5
    points: 25
    exclusive: free
    item:
      material: iron_chestplate
      name: '&6Seltene Beute'
      lore: ['&7Erbeute 5 Rare-Items durch deine Kills.', '&e%total_progress%&7/&e%required_progress%']
  9212:
    name: 'TheMob - Epischer Fund'
    type: themob_loot
    variable:
      root: 'none'
      rarity: 'epic'
    required-progress: 3
    points: 30
    exclusive: free
    item:
      material: netherite_chestplate
      name: '&6Epischer Fund'
      lore: ['&7Sammle 3 frisch von TheMob gedroppte Epic-Items.', '&e%total_progress%&7/&e%required_progress%']
  9213:
    name: 'TheMob - Fuenf Sterne'
    type: themob_loot
    variable:
      root: 'none'
      stars: '5'
    required-progress: 1
    points: 30
    exclusive: free
    item:
      material: nether_star
      name: '&6Fuenf Sterne'
      lore: ['&7Sammle ein Item mit 5 Sternen.', '&e%total_progress%&7/&e%required_progress%']
  9214:
    name: 'TheMob - Mindestens drei Sterne'
    type: themob_loot
    variable:
      root: 'none'
      min-stars: '3'
      max-stars: '5'
    required-progress: 5
    points: 30
    exclusive: free
    item:
      material: gold_ingot
      name: '&6Mindestens drei Sterne'
      lore: ['&7Sammle 5 Items mit mindestens 3 Sternen.', '&e%total_progress%&7/&e%required_progress%']
  9215:
    name: 'TheMob - Eigenes episches Schwert'
    type: themob_loot
    variable:
      root: 'diamond_sword'
      item: 'epic_sword_5'
      own-kill: 'true'
    required-progress: 1
    points: 50
    exclusive: free
    item:
      material: netherite_sword
      name: '&6Eigenes episches Schwert'
      lore: ['&7Sammle das Epic-Schwert mit 5 Sternen aus deinem Kill.', '&e%total_progress%&7/&e%required_progress%']
  9216:
    name: 'TheMob - Zombieessenzen'
    type: themob_loot
    variable:
      root: 'amethyst_shard'
      item: 'essence:zombie:5'
    required-progress: 20
    points: 25
    exclusive: free
    item:
      material: amethyst_shard
      name: '&6Zombieessenzen'
      lore: ['&7Sammle 20 Zombieessenzen der Stufe 5.', '&e%total_progress%&7/&e%required_progress%']
  9217:
    name: 'TheMob - Legende des Koenigs'
    type: themob_loot
    variable:
      root: 'none'
      mob: 'zombie_boss_template'
      rarity: 'legendary'
      own-kill: 'true'
    required-progress: 1
    points: 50
    exclusive: free
    item:
      material: netherite_chestplate
      name: '&6Legende des Koenigs'
      lore: ['&7Sammle einen legendaeren Drop deines Zombiekoenig-Kills.', '&e%total_progress%&7/&e%required_progress%']
  9218:
    name: 'TheMob - Sammler der Bosse'
    type: themob_loot
    variable:
      root: 'none'
      boss: 'true'
    required-progress: 30
    points: 25
    exclusive: free
    item:
      material: chest
      name: '&6Sammler der Bosse'
      lore: ['&7Sammle 30 Items aus Bossbeute. Stackmenge zaehlt.', '&e%total_progress%&7/&e%required_progress%']
  9219:
    name: 'TheMob - Dungeonheld'
    type: themob_dungeon_complete
    variable: 'none'
    required-progress: 1
    points: 30
    exclusive: free
    item:
      material: beacon
      name: '&6Dungeonheld'
      lore: ['&7Besiege den festgelegten Dungeon-Endboss.', '&e%total_progress%&7/&e%required_progress%']
  9220:
    name: 'TheMob - Zehn Stockwerke'
    type: themob_dungeon_complete
    variable:
      root: 'none'
      style: 'gottesturm'
      levels: '10'
    required-progress: 3
    points: 50
    exclusive: free
    item:
      material: beacon
      name: '&6Zehn Stockwerke'
      lore: ['&7Bezwinge 3 Gottestuerme mit 10 Stockwerken.', '&e%total_progress%&7/&e%required_progress%']
```
