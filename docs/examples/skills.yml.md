# skills.yml — configuration example

This is a reference file from TheMob 1.19.0. Keep your tuned production configuration when upgrading. Use materials and entity types supported by your Minecraft version.

```yaml
# TheMob-eigene Boss-/Mobskills und Itemeffekte. SkilltreePlugin verwaltet seine eigenen Skills.
# Aenderungen gelten nach vollstaendigem Serverneustart, nicht mitten im Bosskampf.
# /mob skills <Schalter> an|aus erstellt vorher skills.yml.bak.
enabled: true
bosses:
  enabled: true
  zombie_king: true
  zombie-cleave: true
  zombie-lunge: true
  zombie-stomp: true
  zombie-retreat: true
  bruiser: true
  ranged: true
  guardian: true
  berserker: true
  aggressive: true
  passive: true
  flee: true
  phase-abilities: true
  phase-buffs: true
  phase-combat: true
  phase-effects: true
  world-effects: true
  summon-minions: true
items:
  enabled: true
  critical: true
  lifesteal: true
  poison: true
  ignite: true
  slow: true
  execute: true
  knockback: true
```
