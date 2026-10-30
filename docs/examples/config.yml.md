# config.yml — configuration example

This is a reference file from TheMob 1.19.0. Keep your tuned production configuration when upgrading. Use materials and entity types supported by your Minecraft version.

```yaml
# =========================================================
#  TheMob â€“ Global Configuration
# =========================================================
#
#  High-performance RPG mob & boss framework
#  for survival worlds, arenas and large networks.
#
#  Focus:
#   â€¢ Automation & scaling
#   â€¢ Zero exploit potential
#   â€¢ Predictable performance
#   â€¢ Admin transparency
#
# ---------------------------------------------------------
#  Version
# ---------------------------------------------------------
#  Plugin Version: v1.7
#  Config Version: v1.7
#  Status: Stable / Network Ready
#
# =========================================================

plugin:

  # -------------------------------------------------------
  # Debug & Logging
  # -------------------------------------------------------
  debug: false
  # Enables detailed logging for:
  #  â€¢ Spawn decisions
  #  â€¢ Budget blocks
  #  â€¢ Throttle state changes
  #  â€¢ Boss cooldown checks

use_spawn_egg: true

# =========================================================
#  GLOBAL SPAWN BUDGETS
# =========================================================
# Hard limits that protect server performance.
# Budgets never force-despawn mobs â€“ they only block spawns.
# =========================================================

spawn:
  offset:
    x: 0.0
    y: 0.6
    z: 0.0
  snap-to-ground: true
  scan-down: 8
  scan-up: 6

death-messages:
  disable-vanilla: true
  custom:
    enabled: true
    message: "&c{player} &7was slain by &6{mob}"



global-budgets:
  enabled: true
  total-mobs: 500
  bosses: 10
  minions: 200
world-budgets:
  enabled: true
  worlds:
    world:
      total-mobs: 300
    world_nether:
      total-mobs: 150
    world_the_end:
      total-mobs: 100
# =========================================================
#  AUTO-SPAWN THROTTLING (TPS SAFETY)
# =========================================================
throttling:
  enabled: true
  tps-thresholds:
    normal: 19.5
    warning: 18.0
    critical: 16.0
  behavior:
    warning-multiplier: 1.25
    critical-multiplier: 1.75
    hard-stop-below-critical: true # true = no new auto spawns below critical TPS
# TPS warning is part of the built-in spawn protection system (v1.7). It prevents lag by disabling auto-spawns when TPS drops.
automation:
  feedback:
    show-to-players: false # just shown to Admins

# =========================================================
#  DYNAMIC SCALING BY PLAYER COUNT
# =========================================================

scaling:
  enabled: true
  player-ranges:
    players-1-10:
      spawn-multiplier: 0.5
    players-11-50:
      spawn-multiplier: 1.0
    players-51-150:
      spawn-multiplier: 1.3
    players-151-plus:
      spawn-multiplier: 1.5
# =========================================================
#  BOSS COOLDOWNS (PERSISTENT)
# =========================================================
# Cooldowns survive server restarts.
# Used to prevent boss farming via restarts.
# =========================================================
boss-cooldowns:
  enabled: true
  default-seconds: 0 # 0 preserves existing unlimited timing; override per mob with boss-cooldown-seconds
  storage: file
  # file | sqlite
  data:
    zombie_king:
      next-spawn: 1700000000
# =========================================================
#  ADMIN DIAGNOSTICS
# =========================================================
diagnostics:
  enabled: true
  commands:
    show-block-reasons: true
    show-budgets: true
    show-throttle-state: true
    show-alive-mobs: true
# =========================================================
#  NAVIGATION HUD / MOB RADAR
# =========================================================
navigation-hud:
  enabled: true
  # coinoverlay = identischer BetterHud-Regenbogenkompass; legacy = bisherige Textleiste.
  compass-style: coinoverlay
  update-interval-ticks: 5
  hide-in-combat: false
  hide-when-idle: false
  hide-in-creative: false
  hide-in-spectator: true
  resource-pack:
    # Mit aktivem CoinOverlay wird dessen gemeinsames HUD-Paket verwendet.
    # Diese Optionen gelten nur beim Betrieb ohne CoinOverlay-Ressourcen-HUD.
    offer-on-join: true
    join-delay-ticks: 30
    # Leer = vorhandenes Paket aus server.properties verwenden.
    url: ""
    sha1: ""
    # Plugin-Angebote bleiben optional. Auch server.properties: require-resource-pack=false.
    required: false
    prompt: "Server-Ressourcenpaket fuer den Kompass laden?"
mob-radar:
  scan-radius: 20
  scan-chunks:
    horizontal: 1
    vertical: 1
  priority:
    plugin-mobs: 1
    aggressive: 2
    neutral: 3
    passive: 4
  colors:
    plugin-mob: PURPLE
    aggressive-mob: RED
    neutral-mob: YELLOW
    passive-mob: GREEN
    empty: WHITE
  ignore:
    bats: true
    villagers: true
    named-mobs: false
# =========================================================
#  COMBAT SETTINGS
# =========================================================
# Damage Indicator
combat:
  damage-indicator:
    enabled: false
    show-to:
      - op
      - admin
# Max Critical multiplier for damage
  crit:
    max-multiplier: 10.0

# Optionale Anbieter; ohne deren JAR bleibt TheMob eigenständig nutzbar.
integrations:
  track-minion-kills: false
  jobs:
    enabled: true
  citizens:
    enabled: true
    click-cooldown-seconds: 10
    spawn-on-click: {}
  quests:
    enabled: true
  battlepass:
    enabled: true
  placeholderapi:
    enabled: true
  vault:
    enabled: true
  worldguard:
    enabled: true
    protect-dungeons: true
  luckperms:
    enabled: true
  essentialsx:
    enabled: true
  multiverse:
    enabled: true
  mythicmobs:
    enabled: true
  mmoitems:
    enabled: true
  auraskills:
    enabled: true
  mcmmo:
    enabled: true
  griefprevention:
    enabled: true
    protect-dungeons: true
  towny:
    enabled: true
    protect-dungeons: true
  lands:
    enabled: true
    protect-dungeons: true
  advancedcrates:
    enabled: true
  economyshopgui:
    enabled: true
    protect-custom-items: true
  decentholograms:
    enabled: true
    boss-holograms: false

# Hot/Cold betrifft ausschliesslich TheMob-Mobs. Keine Chunk-Erzeugung fuer Arenen.
performance:
  arena-ticket-radius-chunks: 2
  nameplates-per-cycle: 128
  activity:
    enabled: true
    hot-radius: 64
    cold-radius: 96
    cold-grace-seconds: 10
    combat-keepalive-seconds: 20
    entities-per-cycle: 64
```
