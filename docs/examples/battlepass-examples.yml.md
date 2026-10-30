# battlepass-examples.yml — configuration example

This is a reference file from TheMob 1.19.0. Keep your tuned production configuration when upgrading. Use materials and entity types supported by your Minecraft version.

```yaml
# Beispiele fuer AdvancedPlugins BattlePass 5.0.12.
# Gewuenschte Eintraege unter quests: in eine AKTIVE Datei unter
# plugins/BattlePass/quests/ uebernehmen (z.B. week-1-quests.yml).
# IDs 9101... vorher auf Kollision pruefen. Bestehende Aufgaben behalten.
# Danach /bpa reload. Nicht diese Beispieldatei ueber die echte Woche kopieren.
quests:
  9107:
    name: 'TheMob - Dungeon bezwingen'
    type: themob_dungeon_complete
    variable: none
    required-progress: 1
    points: 30
    exclusive: free
    item:
      material: netherite_sword
      name: '&6Dungeon bezwingen'
      lore: ['&7Besiege den festgelegten Dungeon-Endboss.', '&e%total_progress%&7/&e%required_progress%']
  9108:
    name: 'TheMob - Gottesturm bezwingen'
    type: themob_dungeon_complete
    variable:
      root: none
      style: gottesturm
    required-progress: 3
    points: 50
    exclusive: free
    item:
      material: beacon
      name: '&6Gottesturm bezwingen'
      lore: ['&7Bezwinge 3 Gottesturm-Endbosse.', '&e%total_progress%&7/&e%required_progress%']
  9101:
    name: 'TheMob - Mobjaeger'
    type: themob_kill
    variable: none
    required-progress: 20
    points: 10
    exclusive: free
    item:
      material: iron_sword
      name: '&6Mobjaeger'
      lore: ['&7Besiege 20 TheMob-Mobs.', '&e%total_progress%&7/&e%required_progress%']
  9102:
    name: 'TheMob - Zombiekoenig'
    type: themob_boss_kill
    variable: zombie_boss_template
    required-progress: 1
    points: 25
    exclusive: free
    item:
      material: zombie_head
      name: '&6Der Zombiekoenig'
      lore: ['&7Besiege den Zombiekoenig.', '&e%total_progress%&7/&e%required_progress%']
  9103:
    name: 'TheMob - Untotenjagd'
    type: themob_objective
    variable: untotenjagd
    required-progress: 15
    points: 15
    item:
      material: bone
      name: '&6Untotenjagd'
      lore: ['&7Besiege 15 passende Untote.', '&e%total_progress%&7/&e%required_progress%']
  9104:
    name: 'TheMob - Wilde Jagd'
    type: themob_objective
    variable: wilde_jagd
    required-progress: 10
    points: 15
    item:
      material: compass
      name: '&6Wilde Jagd'
      lore: ['&7Besiege 10 automatisch gespawnte Mobs.', '&e%total_progress%&7/&e%required_progress%']
  9105:
    name: 'TheMob - Boss mit Schwert'
    type: themob_boss_kill
    variable:
      root: none
      holding:
        item: diamond_sword
    required-progress: 3
    points: 30
    exclusive: premium
    item:
      material: diamond_sword
      name: '&6Boss mit Schwert'
      lore: ['&7Besiege 3 Bosse mit Diamantschwert.', '&e%total_progress%&7/&e%required_progress%']
  9106:
    name: 'TheMob - Minionjaeger'
    type: themob_minion_kill
    variable: zombie_boss_template
    required-progress: 20
    points: 10
    item:
      material: rotten_flesh
      name: '&6Minionjaeger'
      lore: ['&7Besiege 20 Minions des Zombiekoenigs.', '&e%total_progress%&7/&e%required_progress%']
# Aufgabe 9106 erfordert integrations.track-minion-kills: true in TheMob/config.yml.
# Weitere Filter unter variable: moeglich: mob, world, phase, spawn-reason,
# entity-type, boss, minion. Beispiel: root: none; spawn-reason: auto_spawn.
```
