# Permissions

TheMob uses Bukkit permissions. Administrative rights default to OP. Give only the required nodes; `themob.command` allows command entry and does not grant every subcommand.

| Node | Access |
|---|---|
| `themob.admin.menu` | Open admin menu |
| `themob.admin.edit` | Edit/save configuration drafts |
| `themob.admin` | Existing broad administration plus menu/edit rights |
| `themob.spawn`, `themob.spawn.set`, `themob.spawn.delete` | Spawn and schedule operations |
| `themob.dungeon.admin` | Create/manage protected dungeons and explicit build mode |
| `themob.dungeon.enter`, `themob.dungeon.altar` | Player dungeon entry and exit altar |
| `themob.integrations.view`, `themob.integrations.manage` | Inspect/configure providers |
| `themob.skills.manage` | Save skill switches |
| `themob.reload`, `themob.killall` | Maintenance |
| `themob.stats` | Player statistics |
| `themob.compass`, `themob.compass.admin` | Compass controls |

Every menu action rechecks its own permission. Opening a menu alone does not authorize editing, spawning or terrain changes. Read the [complete permission tree](../examples/plugin.yml.md) for inherited nodes and other legacy commands.
