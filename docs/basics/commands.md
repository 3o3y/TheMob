# Commands

**Mob, item, dungeon and administration commands remain supported.** The former player `/stats` command was removed in 1.19.1. `/mob` without arguments opens the admin menu for authorized players; console and users without menu access receive help. Tab completion exposes available IDs.

| Command | Purpose |
|---|---|
| `/mob spawn <id>` | Spawn a definition at your location |
| `/mob mobs [page]`, `/mob items [page]` | Browse IDs |
| `/mob item <id> [player]` | Give an equipment/provider item |
| `/mob egg <id> [amount]` | Give custom spawn eggs |
| `/mob set ...`, `/mob del ...`, `/mob list ...` | Existing spawn scheduling commands |
| `/mob dungeon` | Admin dungeon generation; destructive footprint confirmation in menu |
| `/mob dungeon enter <id>`, `/mob dungeon leave` | Multiplayer access/return |
| `/mob dungeon boss <id> <mob-id\|off>` | Assign the completion boss |
| `/mob dungeon altar <id> [here]` | Place/repair the exit altar |
| `/mob abilities` | Inspect/toggle boss/item ability switches for restart |
| `/mob connect check`, `/mob connect <provider> enable\|disable` | Inspect/configure providers |
| `/mob battlepass` | Native objective status and examples |
| `/mob performance`, `/mob diag ...` | Diagnostics |
| `/mob get base64` | Export held item data |
| `/mob reload`, `/mob killall` | Authorized maintenance; removes active managed mobs |
| `/mobkompass`, `/kompass` | Personal/server compass controls |

The [shipped command descriptor](../examples/plugin.yml.md) lists full usage and permission defaults. Menu actions enforce the same underlying permissions. [Admin menu](../administration/menu.md), [dungeons](../dungeons/overview.md).
