# zombie-boss.yml — configuration example

This is a reference file from TheMob 1.19.0. Keep your tuned production configuration when upgrading. Use materials and entity types supported by your Minecraft version.

```yaml
# Existing boss_template.yml, HP thresholds, stats, loot and BattlePass IDs are preserved.
# Set enabled: false or zombie-behavior-disabled: true in one mob to opt out.
enabled: true
include-warlords: true
# Explicit behavior.* values in a mob phase override these shared defaults.
# Ticks: 20 = one second. Damage is raw damage before normal phase/armor modifiers.
stages:
  royal:
    intro: '&2Der König hebt seine Klinge. Weiche seinem schweren Hieb seitlich aus!'
    windup-ticks: 30
    recovery-ticks: 30
    cooldown-ticks: 100
    attack-damage: 4.0
    range: 25
    leash-radius: 48
  hunt:
    intro: '&6Er wittert Blut. Sein Ansturm folgt einer festen Richtung!'
    windup-ticks: 20
    recovery-ticks: 30
    cooldown-ticks: 80
    attack-damage: 4.5
    range: 28
    leash-radius: 48
  frenzy:
    intro: '&cKönigliche Raserei! Springe über die Bodenwelle und nutze seine Erschöpfung.'
    windup-ticks: 30
    recovery-ticks: 30
    cooldown-ticks: 70
    attack-damage: 5.0
    range: 30
    leash-radius: 48
  desperate:
    intro: '&4Der König taumelt zurück. Vorsicht vor seinem verzweifelten Gegenhieb!'
    windup-ticks: 20
    recovery-ticks: 40
    cooldown-ticks: 60
    attack-damage: 5.5
    range: 30
    leash-radius: 48
```
