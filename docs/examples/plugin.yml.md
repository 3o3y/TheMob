# plugin.yml — command and permission reference

```yaml
name: TheMob
version: "1.19.1"
main: org.plugin.theMob.TheMob
api-version: "1.18"
author: 3o3y
softdepend: [BetterHud, CoinOverlay, Jobs, Citizens, Quests, BattlePass, PlaceholderAPI, Vault, WorldGuard, LuckPerms, Essentials, Multiverse-Core, MythicMobs, MMOItems, AuraSkills, mcMMO, GriefPrevention, Towny, Lands, AdvancedCrates, EconomyShopGUI, EconomyShopGUI-Premium, DecentHolograms]
description: >
  High-performance custom mob system with YAML-based mobs,
  boss phases, automation & scaling, global spawn budgets,
  TPS throttling and admin diagnostics.

commands:

  mobkompass:
    description: Regenbogen-Kompass wie in CoinOverlay verwalten
    aliases: [kompass]
    usage: /mobkompass [an|aus|status|pack|reload|spieler NAME an|server aus]

  mob:
    description: Manage custom mobs and all spawn systems
    usage: |
      /mob
      /mob spawn <mob-id>
      /mob killall
      /mob reload
      /mob integrations
      /mob connect [check|Anbieter]
      /mob connect <Anbieter> <enable|disable>
      /mob battlepass
      /mob abilities [status|Schalter an|aus]
      /mob mobs [Seite]
      /mob items [Seite]
      /mob item <ID> [Spieler]
      /mob performance
      /mob dungeon [random|krypta|turm|mine|weltenbaum|gottesturm|labyrinth]
      /mob dungeon list|status|info|enter|leave|edit|cancel|resume
      /mob dungeon boss <ID> <Boss-Mob-ID|off>
      /mob dungeon altar <ID> [here]
      /mob toggle hud

      /mob diag status
      /mob diag tps
      /mob diag budgets
      /mob diag throttle
      /mob diag alive
      /mob diag cooldown <boss-id>

      /mob debug

      /mob autospawn <mob-id> <seconds> <maxSpawns>   (legacy)

      /mob set autospawn <mob-id> <seconds> <maxSpawns>
      /mob set followplayer <player> <mob-id> <seconds> <maxSpawns> <onetime/endless> <mindistance> <maxdistance> <message>
      /mob set randomradius <mob-id> <seconds> <maxSpawns> <minradius> <maxradius>
      /mob set randomworld <mob-id> <seconds> <maxSpawns> <onetime/endless> <message-timer> <message>

      /mob del autospawn <mob-id>
      /mob del followplayer <player> <mob-id>
      /mob del randomradius <mob-id>
      /mob del randomworld <world> <mob-id>

      /mob list autospawn
      /mob list all
      
      /mob get base64
    permission: themob.command
    permission-message: "&cYou do not have permission to use this command."


permissions:
  themob.admin.menu:
    description: Das TheMob-Adminmenue oeffnen; Aktionen pruefen eigene Rechte
    default: op
  themob.admin.edit:
    description: Vollzugriff auf TheMob-Konfigurationsdateien im Adminmenue
    default: op
  themob.abilities.manage:
    description: Boss- und Itemfaehigkeiten fuer den Serverneustart konfigurieren
    default: op

  themob.integrations.view:
    description: Verbindungen und sichere Einrichtungshinweise lesen
    default: op

  themob.integrations.manage:
    description: Anbindungen fuer den naechsten Serverneustart konfigurieren
    default: op
    children:
      themob.integrations.view: true

  themob.dungeon.admin:
    description: Dungeons erzeugen, Bau abbrechen und ausdruecklichen Baumodus aktivieren
    default: op

  themob.dungeon.enter:
    description: Fertige Dungeons gemeinsam betreten und wieder verlassen
    default: true

  themob.dungeon.altar:
    description: Ausgangsaltar und Dungeon-Reiseziele benutzen
    default: true

  themob.npc.use:
    description: Konfigurierte Citizens-NPCs zum Mob-Spawnen nutzen
    default: true

  themob.compass:
    description: Eigenen Regenbogen-Kompass umschalten
    default: true

  themob.compass.admin:
    description: Kompass fuer andere Spieler und serverweit verwalten
    default: op

  # =========================
  # ROOT / ADMIN
  # =========================
  themob.command:
    description: Allows entry to the mob command; each subcommand checks its own permission
    default: true

  themob.base64:
    description: Allows exporting custom item data
    default: op

  themob.command.egg:
    description: Allows receiving custom spawn eggs
    default: op

  themob.spawner:
    description: Allows receiving custom spawners
    default: op

  themob.admin:
    description: Full access to all TheMob commands and systems
    default: op
    children:
      themob.use: true
      themob.integrations.view: true
      themob.integrations.manage: true
      themob.abilities.manage: true
      themob.admin.menu: true
      themob.admin.edit: true
      themob.base64: true
      themob.command.egg: true
      themob.spawner: true
      themob.debug: true
      themob.spawn: true
      themob.spawn.set: true
      themob.spawn.delete: true
      themob.killall: true
      themob.reload: true
      themob.list: true
      themob.hud.toggle: true
      themob.compass: true
      themob.compass.admin: true
      themob.dungeon.admin: true
      themob.dungeon.enter: true
      themob.dungeon.altar: true

  # =========================
  # BASE ACCESS
  # =========================
  themob.use:
    description: Allows usage of base /mob command
    default: op

  # =========================
  # DEBUG / MONITORING (v1.8)
  # =========================
  themob.debug:
    description: Allows access to /mob debug live diagnostics
    default: op

  # =========================
  # SPAWN Ã¢â‚¬â€œ MANUAL
  # =========================
  themob.spawn:
    description: Allows spawning custom mobs manually (/mob spawn)
    default: op

  # =========================
  # SPAWN Ã¢â‚¬â€œ AUTOSPAWN MANAGEMENT
  # =========================
  themob.spawn.set:
    description: Allows creating and modifying all auto spawn types
    default: op

  themob.spawn.delete:
    description: Allows deleting auto spawns
    default: op

  # =========================
  # LIST / INFO
  # =========================
  themob.list:
    description: Allows listing all registered spawns (/mob list)
    default: op

  # =========================
  # MANAGEMENT
  # =========================
  themob.killall:
    description: Allows removing all active custom mobs
    default: op

  themob.reload:
    description: Allows reloading TheMob configuration
    default: op

  # =========================
  # HUD / UI
  # =========================
  themob.hud.toggle:
    description: Allows toggling the navigation HUD
    default: true

  # =========================
  # PLAYER FEATURES
  # =========================
```
