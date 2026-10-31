package org.plugin.theMob.command;

import java.util.Arrays;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.control.command.MobDiagCommand;
import org.plugin.theMob.control.command.TheMobDebugCommand;
import org.plugin.theMob.hud.PlayerHudState;
import org.plugin.theMob.mob.MobManager;
import org.plugin.theMob.spawn.SpawnController;
import org.plugin.theMob.spawn.egg.SpawnEggItemFactory;
import org.plugin.theMob.spawn.type.SpawnMode;

public final class MobCommand implements CommandExecutor {
  private final TheMob plugin;
  private final MobManager mobs;
  private final SpawnController spawns;
  private final MobDiagCommand diag;
  private final TheMobDebugCommand debug;
  private final MobBase64Command base64;
  private final MobEggCommand eggCommand;
  private final MobSpawnerCommand spawnerCommand;

  public MobCommand(TheMob plugin, MobManager mobs, SpawnController spawns) {
    this.plugin = plugin;
    this.mobs = mobs;
    this.spawns = spawns;
    this.diag = new MobDiagCommand(plugin.automation());
    this.debug = new TheMobDebugCommand(plugin, mobs, spawns);
    this.base64 = new MobBase64Command(plugin);
    this.eggCommand = new MobEggCommand(new SpawnEggItemFactory(plugin.keys()), plugin.configs());
    this.spawnerCommand = new MobSpawnerCommand(plugin.keys(), plugin.configs());
  }

  public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
    if (args.length == 2 && args[0].equalsIgnoreCase("get") && args[1].equalsIgnoreCase("base64")) {
      return this.base64.onCommand(sender, cmd, label, args);
    } else if (args.length == 0) {
      if (sender instanceof Player player
          && org.plugin.theMob.admin.AdminMenus.mayOpen(player)
          && plugin.adminMenus() != null) plugin.adminMenus().open(player);
      else this.help(sender);
      return true;
    } else if (java.util.Set.of("item", "items", "mobs", "performance")
        .contains(args[0].toLowerCase(java.util.Locale.ROOT))) {
      return MobContentCommand.execute(plugin, sender, args);
    } else if (args[0].equalsIgnoreCase("abilities")) {
      org.plugin.theMob.core.CombatAbilities.command(plugin, sender, args);
      return true;
    } else if (args[0].equalsIgnoreCase("dungeon")) {
      return org.plugin.theMob.dungeon.DungeonCommand.execute(plugin.dungeons(), sender, args);
    } else if (args[0].equalsIgnoreCase("battlepass")) {
      MobBattlePassCommand.show(plugin, sender);
      return true;
    } else if (args[0].equalsIgnoreCase("integrations") || args[0].equalsIgnoreCase("connect")) {
      MobConnectCommand.execute(plugin, sender, args);
      return true;
    } else if (args[0].equalsIgnoreCase("spawner")) {
      return this.spawnerCommand.onCommand(sender, cmd, label, args);
    } else if (args[0].equalsIgnoreCase("egg")) {
      if (sender instanceof Player player) {
        return this.eggCommand.handle(player, args);
      } else {
        sender.sendMessage("§cOnly players can use spawn eggs.");
        return true;
      }
    } else if (args[0].equalsIgnoreCase("debug")) {
      this.debug.execute(sender);
      return true;
    } else if (args[0].equalsIgnoreCase("toggle")
        && args.length >= 2
        && args[1].equalsIgnoreCase("hud")) {
      if (!sender.hasPermission("themob.hud.toggle")) {
        sender.sendMessage("§cNo permission.");
        return true;
      }
      if (sender instanceof Player p) {
        boolean var36 = PlayerHudState.toggle(p.getUniqueId());
        p.sendMessage(var36 ? "§aNavigation HUD enabled." : "§cNavigation HUD disabled.");
        return true;
      } else {
        sender.sendMessage("§cOnly players can toggle HUD.");
        return true;
      }
    } else if (args[0].equalsIgnoreCase("diag")) {
      this.diag.execute(sender, args);
      return true;
    } else if (args[0].equalsIgnoreCase("reload")) {
      if (!sender.hasPermission("themob.reload")) {
        sender.sendMessage("§cNo permission.");
        return true;
      } else {
        this.plugin.reloadPlugin();
        sender.sendMessage("§a[TheMob] Reloaded.");
        return true;
      }
    } else if (args[0].equalsIgnoreCase("spawn")) {
      if (!sender.hasPermission("themob.spawn")) {
        sender.sendMessage("§cNo permission.");
        return true;
      }
      if (sender instanceof Player p) {
        if (args.length < 2) {
          p.sendMessage("§e/mob spawn <mob-id>");
          return true;
        } else {
          String id = args[1].toLowerCase();
          if (!this.mobs.mobExists(id)) {
            p.sendMessage("§cUnknown mob: §e" + id);
            return true;
          } else {
            Location base =
                p.getLocation()
                    .clone()
                    .add(p.getLocation().getDirection().normalize().multiply(2.2));
            Location safe = base;
            if (this.mobs.spawnCustomMob(id, null, safe) != null)
              p.sendMessage("§aSpawned mob: §e" + id);
            else p.sendMessage("§eSpawn blockiert: Limit, Cooldown, Region oder API.");
            return true;
          }
        }
      } else {
        sender.sendMessage("§cOnly players can spawn mobs.");
        return true;
      }
    } else if (args[0].equalsIgnoreCase("autospawn")) {
      if (sender instanceof Player px) {
        if (!sender.hasPermission("themob.spawn.set")) {
          sender.sendMessage("§cNo permission.");
          return true;
        } else if (args.length < 4) {
          sender.sendMessage("§e/mob autospawn <mob-id> <seconds> <maxSpawns>");
          return true;
        } else {
          String id = args[1].toLowerCase();
          if (!this.mobs.mobExists(id)) {
            sender.sendMessage("§cUnknown mob: §e" + id);
            return true;
          } else {
            int seconds;
            int maxSpawns;
            try {
              seconds = Integer.parseInt(args[2]);
              maxSpawns = Integer.parseInt(args[3]);
            } catch (NumberFormatException var16) {
              sender.sendMessage("§cSeconds and maxSpawns must be numbers.");
              return true;
            }

            boolean ok =
                this.spawns.setAutoSpawnFixedPoint(id, px.getLocation(), seconds, maxSpawns);
            sender.sendMessage(
                ok ? "§aAuto-spawn created for §e" + id : "§cFailed to create auto-spawn.");
            return true;
          }
        }
      } else {
        sender.sendMessage("§cOnly players can use this command.");
        return true;
      }
    } else if (args[0].equalsIgnoreCase("set")) {
      if (sender instanceof Player pxx) {
        if (!sender.hasPermission("themob.spawn.set")) {
          sender.sendMessage("§cNo permission.");
          return true;
        } else if (args.length < 2) {
          this.setHelp(sender);
          return true;
        } else {
          String sub = args[1].toLowerCase();
          if (sub.equals("autospawn")) {
            if (args.length < 5) {
              sender.sendMessage("§e/mob set autospawn <mob-id> <seconds> <maxSpawns>");
              return true;
            } else {
              String mobId = args[2].toLowerCase();
              if (!this.mobs.mobExists(mobId)) {
                sender.sendMessage("§cUnknown mob: §e" + mobId);
                return true;
              } else {
                int secondsx;
                int maxSpawns;
                try {
                  secondsx = Integer.parseInt(args[3]);
                  maxSpawns = Integer.parseInt(args[4]);
                } catch (NumberFormatException var17) {
                  sender.sendMessage("§cSeconds and maxSpawns must be numbers.");
                  return true;
                }

                boolean ok =
                    this.spawns.setAutoSpawnFixedPoint(
                        mobId, pxx.getLocation(), secondsx, maxSpawns);
                sender.sendMessage(ok ? "§aFIXED_POINT spawn saved." : "§cFailed.");
                return true;
              }
            }
          } else if (sub.equals("randomradius")) {
            if (args.length < 7) {
              sender.sendMessage(
                  "§e/mob set randomradius <mob-id> <seconds> <maxSpawns> <minradius> <maxradius>");
              return true;
            } else {
              String mobId = args[2].toLowerCase();
              if (!this.mobs.mobExists(mobId)) {
                sender.sendMessage("§cUnknown mob: §e" + mobId);
                return true;
              } else {
                int secondsxx;
                int maxSpawns;
                int minR;
                int maxR;
                try {
                  secondsxx = Integer.parseInt(args[3]);
                  maxSpawns = Integer.parseInt(args[4]);
                  minR = Integer.parseInt(args[5]);
                  maxR = Integer.parseInt(args[6]);
                } catch (NumberFormatException var18) {
                  sender.sendMessage("§cNumbers required.");
                  return true;
                }

                boolean ok =
                    this.spawns.setRandomRadius(
                        mobId, pxx.getLocation(), secondsxx, maxSpawns, minR, maxR);
                sender.sendMessage(ok ? "§aRANDOM_RADIUS spawn saved." : "§cFailed.");
                return true;
              }
            }
          } else if (sub.equals("followplayer")) {
            if (args.length < 10) {
              sender.sendMessage(
                  "§e/mob set followplayer <player> <mob-id> <seconds> <maxSpawns>"
                      + " <onetime/endless> <mindistance> <maxdistance> <message>");
              return true;
            } else {
              String playerName = args[2];
              String mobId = args[3].toLowerCase();
              if (!this.mobs.mobExists(mobId)) {
                sender.sendMessage("§cUnknown mob: §e" + mobId);
                return true;
              } else {
                SpawnMode mode = SpawnMode.fromString(args[6]);

                int secondsxxx;
                int maxSpawnsx;
                int minD;
                int maxD;
                try {
                  secondsxxx = Integer.parseInt(args[4]);
                  maxSpawnsx = Integer.parseInt(args[5]);
                  minD = Integer.parseInt(args[7]);
                  maxD = Integer.parseInt(args[8]);
                } catch (NumberFormatException var19) {
                  sender.sendMessage("§cNumbers required.");
                  return true;
                }

                String message = this.joinFrom(args, 9);
                boolean ok =
                    this.spawns.setFollowPlayer(
                        playerName, mobId, secondsxxx, maxSpawnsx, mode, minD, maxD, message);
                sender.sendMessage(ok ? "§aFOLLOW_PLAYER spawn saved." : "§cFailed.");
                return true;
              }
            }
          } else if (sub.equals("randomworld")) {
            if (args.length < 8) {
              sender.sendMessage(
                  "§e/mob set randomworld <mob-id> <seconds> <maxSpawns> <onetime/endless>"
                      + " <message-timer> <message>");
              sender.sendMessage("§7World is taken from your current world.");
              return true;
            } else {
              String mobId = args[2].toLowerCase();
              if (!this.mobs.mobExists(mobId)) {
                sender.sendMessage("§cUnknown mob: §e" + mobId);
                return true;
              } else {
                SpawnMode mode = SpawnMode.fromString(args[5]);

                int secondsxxxx;
                int maxSpawnsxx;
                int msgTimer;
                try {
                  secondsxxxx = Integer.parseInt(args[3]);
                  maxSpawnsxx = Integer.parseInt(args[4]);
                  msgTimer = Integer.parseInt(args[6]);
                } catch (NumberFormatException var20) {
                  sender.sendMessage("§cNumbers required.");
                  return true;
                }

                String message = this.joinFrom(args, 7);
                World w = pxx.getWorld();
                boolean ok =
                    this.spawns.setRandomWorld(
                        w.getName(), mobId, secondsxxxx, maxSpawnsxx, mode, msgTimer, message);
                sender.sendMessage(ok ? "§aRANDOM_WORLD spawn saved." : "§cFailed.");
                return true;
              }
            }
          } else {
            this.setHelp(sender);
            return true;
          }
        }
      } else {
        sender.sendMessage("§cOnly players can use this command.");
        return true;
      }
    } else if (args[0].equalsIgnoreCase("list")) {
      if (!sender.hasPermission("themob.list")) {
        sender.sendMessage("§cNo permission.");
        return true;
      } else if (args.length >= 2 && args[1].equalsIgnoreCase("autospawn")) {
        List<SpawnController.AutoSpawnInfo> list = this.spawns.listAutoSpawns();
        if (list.isEmpty()) {
          sender.sendMessage("§7No auto-spawns configured.");
          return true;
        } else {
          sender.sendMessage("§6§lAuto-Spawns:");

          for (SpawnController.AutoSpawnInfo s : list) {
            sender.sendMessage(
                "§e- " + s.mobId() + " §7" + s.world() + " §f" + s.x() + ", " + s.y() + ", "
                    + s.z());
          }

          return true;
        }
      } else if (args.length >= 2 && args[1].equalsIgnoreCase("all")) {
        List<String> lines = this.spawns.listAllLines();
        if (lines.isEmpty()) {
          sender.sendMessage("§7No spawns configured.");
          return true;
        } else {
          sender.sendMessage("§6§lAll Spawns:");

          for (String line : lines) {
            sender.sendMessage("§e- §f" + line);
          }

          return true;
        }
      } else {
        sender.sendMessage("§e/mob list autospawn");
        sender.sendMessage("§e/mob list all");
        return true;
      }
    } else if (args[0].equalsIgnoreCase("del")) {
      if (!sender.hasPermission("themob.spawn.delete")) {
        sender.sendMessage("§cNo permission.");
        return true;
      } else if (args.length < 2) {
        this.delHelp(sender);
        return true;
      } else {
        String sub = args[1].toLowerCase();
        if (sub.equals("autospawn")) {
          if (args.length < 3) {
            sender.sendMessage("§e/mob del autospawn <mob-id>");
            return true;
          } else {
            String mobId = args[2].toLowerCase();
            boolean ok = this.spawns.deleteAutoSpawnByMobId(mobId);
            sender.sendMessage(
                ok
                    ? "§aAll FIXED_POINT auto-spawns removed for §e" + mobId
                    : "§cNo FIXED_POINT auto-spawn found for §e" + mobId);
            return true;
          }
        } else if (sub.equals("followplayer")) {
          if (args.length < 4) {
            sender.sendMessage("§e/mob del followplayer <player> <mob-id>");
            return true;
          } else {
            boolean ok = this.spawns.deleteFollowPlayer(args[2], args[3]);
            sender.sendMessage(ok ? "§aFOLLOW_PLAYER removed." : "§cNot found.");
            return true;
          }
        } else if (sub.equals("randomworld")) {
          if (args.length < 4) {
            sender.sendMessage("§e/mob del randomworld <world> <mob-id>");
            return true;
          } else {
            boolean ok = this.spawns.deleteRandomWorld(args[2], args[3]);
            sender.sendMessage(ok ? "§aRANDOM_WORLD removed." : "§cNot found.");
            return true;
          }
        } else if (sub.equals("randomradius")) {
          if (sender instanceof Player pxxx) {
            if (args.length < 3) {
              sender.sendMessage("§e/mob del randomradius <mob-id>");
              sender.sendMessage(
                  "§7Deletes the RANDOM_RADIUS spawn at your current block position.");
              return true;
            } else {
              boolean ok = this.spawns.deleteRandomRadiusAt(args[2], pxxx.getLocation());
              sender.sendMessage(ok ? "§aRANDOM_RADIUS removed." : "§cNot found at your position.");
              return true;
            }
          } else {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
          }
        } else {
          this.delHelp(sender);
          return true;
        }
      }
    } else if (args[0].equalsIgnoreCase("killall")) {
      if (!sender.hasPermission("themob.killall")) {
        sender.sendMessage("§cNo permission.");
        return true;
      } else {
        this.mobs.killAll();
        this.spawns.getAutoSpawnManager().onKillAll();
        sender.sendMessage("§aAll custom mobs have been removed.");
        return true;
      }
    } else {
      this.help(sender);
      return true;
    }
  }

  private void help(CommandSender s) {
    s.sendMessage("§e/mob abilities [status|Schalter an|aus]");
    s.sendMessage("§e/mob spawn <mob-id>");
    s.sendMessage("§e/mob killall");
    s.sendMessage("§e/mob set autospawn <mob-id> <seconds> <maxSpawns>");
    s.sendMessage(
        "§e/mob set followplayer <player> <mob-id> <seconds> <maxSpawns> <onetime/endless>"
            + " <mindistance> <maxdistance> <message>");
    s.sendMessage("§e/mob set randomradius <mob-id> <seconds> <maxSpawns> <minradius> <maxradius>");
    s.sendMessage(
        "§e/mob set randomworld <mob-id> <seconds> <maxSpawns> <onetime/endless> <message-timer>"
            + " <message>");
    s.sendMessage("§7Placeholders: {world} {x} {y} {z}");
    s.sendMessage("§e/mob del autospawn <mob-id>");
    s.sendMessage("§e/mob del followplayer <player> <mob-id>");
    s.sendMessage("§e/mob del randomradius <mob-id>  §7(uses your position)");
    s.sendMessage("§e/mob del randomworld <world> <mob-id>");
    s.sendMessage("§e/mob list autospawn");
    s.sendMessage("§e/mob list all");
    s.sendMessage("§e/mob reload");
    s.sendMessage("§e/mob battlepass");
    s.sendMessage("§e/mob dungeon [Stil] | list | enter ID | leave | edit ID/off");
    s.sendMessage("§e/mob connect [check|Anbieter] | /mob integrations");
    s.sendMessage("§e/mob item <ID> [Spieler] | /mob items [Seite] | /mob performance");
    s.sendMessage("§e/mob toggle hud");
    s.sendMessage("§e/mob debug");
    s.sendMessage("§e/mob egg <base-type>");
    s.sendMessage("§7Legacy alias: /mob autospawn <mob-id> <seconds> <maxSpawns>");
  }

  private void setHelp(CommandSender s) {
    s.sendMessage("§e/mob set autospawn <mob-id> <seconds> <maxSpawns>");
    s.sendMessage(
        "§e/mob set followplayer <player> <mob-id> <seconds> <maxSpawns> <onetime/endless>"
            + " <mindistance> <maxdistance> <message>");
    s.sendMessage("§e/mob set randomradius <mob-id> <seconds> <maxSpawns> <minradius> <maxradius>");
    s.sendMessage(
        "§e/mob set randomworld <mob-id> <seconds> <maxSpawns> <onetime/endless> <message-timer>"
            + " <message>");
  }

  private void delHelp(CommandSender s) {
    s.sendMessage("§e/mob del autospawn <mob-id>");
    s.sendMessage("§e/mob del followplayer <player> <mob-id>");
    s.sendMessage("§e/mob del randomradius <mob-id>  §7(uses your position)");
    s.sendMessage("§e/mob del randomworld <world> <mob-id>");
  }

  private String joinFrom(String[] args, int start) {
    return start >= args.length
        ? ""
        : String.join(" ", Arrays.copyOfRange(args, start, args.length));
  }
}
