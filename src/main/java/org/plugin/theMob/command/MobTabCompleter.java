package org.plugin.theMob.command;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.plugin.theMob.core.ConfigService;
import org.plugin.theMob.mob.MobManager;

public final class MobTabCompleter implements TabCompleter {
  private final MobManager mobs;
  private final ConfigService configs;

  public MobTabCompleter(MobManager mobs, ConfigService configs) {
    this.mobs = mobs;
    this.configs = configs;
  }

  public List<String> onTabComplete(
      CommandSender sender, Command command, String alias, String[] args) {
    if (args.length == 1) {
      return this.filter(
          args[0],
          "spawn",
          "set",
          "list",
          "del",
          "killall",
          "reload",
          "toggle",
          "diag",
          "debug",
          "get",
          "egg",
          "spawner",
          "integrations",
          "connect",
          "skills",
          "battlepass",
          "item",
          "items",
          "mobs",
          "performance",
          "dungeon");
    } else if (args.length >= 2
        && (this.eq(args, 0, "connect") || this.eq(args, 0, "integrations"))) {
      if (!MobConnectCommand.canView(sender) && !MobConnectCommand.canManage(sender))
        return List.of();
      if (args.length == 2) {
        var choices = new ArrayList<>(MobConnectCommand.targets());
        choices.addAll(List.of("check", "status", "help"));
        return filter(args[1], choices);
      }
      if (args.length == 3
          && MobConnectCommand.targets().contains(args[1].toLowerCase(java.util.Locale.ROOT)))
        return MobConnectCommand.canManage(sender)
            ? filter(args[2], "check", "enable", "disable")
            : filter(args[2], "check");
      return List.of();
    } else if (args.length >= 2 && this.eq(args, 0, "skills")) {
      if (!org.plugin.theMob.core.TheMobSkills.mayManage(sender)) return List.of();
      if (args.length == 2) {
        var targets = new ArrayList<>(org.plugin.theMob.core.TheMobSkills.targets());
        targets.add("status");
        return filter(args[1], targets);
      }
      if (args.length == 3 && org.plugin.theMob.core.TheMobSkills.targets().contains(args[1]))
        return filter(args[2], "an", "aus");
      return List.of();
    } else if (args.length >= 2 && this.eq(args, 0, "dungeon")) {
      if (args.length == 2)
        return this.filter(
            args[1],
            "random",
            "krypta",
            "turm",
            "mine",
            "weltenbaum",
            "gottesturm",
            "labyrinth",
            "list",
            "info",
            "boss",
            "altar",
            "status",
            "enter",
            "leave",
            "edit",
            "cancel",
            "resume",
            "help");
      if (args.length == 3
          && java.util.Set.of("enter", "tp", "info", "edit", "resume", "boss", "altar")
              .contains(args[1].toLowerCase(java.util.Locale.ROOT))) {
        var owner = Bukkit.getPluginManager().getPlugin("TheMob");
        if (owner instanceof org.plugin.theMob.TheMob plugin && plugin.dungeons() != null) {
          List<String> ids =
              new ArrayList<>(
                  plugin.dungeons().records().stream()
                      .filter(
                          r ->
                              args[1].equalsIgnoreCase("info")
                                  || args[1].equalsIgnoreCase("edit")
                                  || (args[1].equalsIgnoreCase("resume")
                                      ? r.state
                                          != org.plugin.theMob.dungeon.DungeonRecord.State.READY
                                      : r.state
                                          == org.plugin.theMob.dungeon.DungeonRecord.State.READY))
                      .map(r -> r.id)
                      .toList());
          if (args[1].equalsIgnoreCase("edit")) ids.add("off");
          return this.filter(args[2], ids);
        }
      }
      if (args.length == 4 && args[1].equalsIgnoreCase("boss")) {
        var owner = Bukkit.getPluginManager().getPlugin("TheMob");
        if (owner instanceof org.plugin.theMob.TheMob plugin) {
          List<String> ids =
              new ArrayList<>(
                  plugin.api().definitions().stream()
                      .filter(org.plugin.theMob.api.MobDefinition::boss)
                      .map(org.plugin.theMob.api.MobDefinition::id)
                      .sorted()
                      .toList());
          ids.add("off");
          return this.filter(args[3], ids);
        }
      }
      if (args.length == 4 && args[1].equalsIgnoreCase("altar"))
        return this.filter(args[3], "here");
      return List.of();
    } else if (args.length == 2 && this.eq(args, 0, "item")) {
      var plugin = Bukkit.getPluginManager().getPlugin("TheMob");
      return plugin instanceof org.plugin.theMob.TheMob owner && owner.gear() != null
          ? this.filter(args[1], owner.gear().ids().stream().sorted().toList())
          : List.of();
    } else if (args.length == 3 && this.eq(args, 0, "item")) {
      return onlinePlayers(args[2]);
    } else if (args.length == 2 && this.eq(args, 0, "spawner")) {
      return List.of("1", "4", "8", "16", "32", "64");
    } else if (args.length == 2 && this.eq(args, 0, "diag")) {
      return this.filter(args[1], "status", "tps", "budgets", "throttle", "alive", "cooldown");
    } else if (args.length == 2 && this.eq(args, 0, "egg")) {
      return this.filter(args[1], this.spawnEggKeys());
    } else if (args.length == 3 && this.eq(args, 0, "egg")) {
      return this.numbers(args[2], "1", "4", "8", "16", "32", "64");
    } else if (args.length == 2 && this.eq(args, 0, "toggle")) {
      return this.filter(args[1], "hud");
    } else if (args.length == 2 && this.eq(args, 0, "spawn")) {
      return this.mobIds(args[1]);
    } else if (args.length == 2 && this.eq(args, 0, "set")) {
      return this.filter(args[1], "autospawn", "followplayer", "randomradius", "randomworld");
    } else {
      if (this.isSet(args, "autospawn")) {
        if (args.length == 3) {
          return this.mobIds(args[2]);
        }

        if (args.length == 4) {
          return this.numbers(args[3], "10", "30", "60", "120");
        }

        if (args.length == 5) {
          return this.numbers(args[4], "1", "3", "5", "10");
        }
      }

      if (this.isSet(args, "randomradius")) {
        if (args.length == 3) {
          return this.mobIds(args[2]);
        }

        if (args.length == 4) {
          return this.numbers(args[3], "30", "60", "120");
        }

        if (args.length == 5) {
          return this.numbers(args[4], "1", "3", "5");
        }

        if (args.length == 6) {
          return this.numbers(args[5], "8", "16", "24");
        }

        if (args.length == 7) {
          return this.numbers(args[6], "24", "32", "48");
        }
      }

      if (this.isSet(args, "followplayer")) {
        if (args.length == 3) {
          return this.onlinePlayers(args[2]);
        }

        if (args.length == 4) {
          return this.mobIds(args[3]);
        }

        if (args.length == 5) {
          return this.numbers(args[4], "10", "20", "30");
        }

        if (args.length == 6) {
          return this.numbers(args[5], "1", "3", "5");
        }

        if (args.length == 7) {
          return this.filter(args[6], "onetime", "endless");
        }

        if (args.length == 8) {
          return this.numbers(args[7], "6", "10", "16");
        }

        if (args.length == 9) {
          return this.numbers(args[8], "16", "24", "32");
        }
      }

      if (this.isSet(args, "randomworld")) {
        if (args.length == 3) {
          return this.mobIds(args[2]);
        }

        if (args.length == 4) {
          return this.numbers(args[3], "60", "120", "300");
        }

        if (args.length == 5) {
          return this.numbers(args[4], "1", "3", "5");
        }

        if (args.length == 6) {
          return this.filter(args[5], "onetime", "endless");
        }

        if (args.length == 7) {
          return this.numbers(args[6], "30", "60", "120");
        }
      }

      if (args.length == 2 && this.eq(args, 0, "list")) {
        return this.filter(args[1], "all", "autospawn");
      } else if (args.length == 2 && this.eq(args, 0, "del")) {
        return this.filter(args[1], "autospawn", "followplayer", "randomradius", "randomworld");
      } else if (this.isDel(args, "autospawn") && args.length == 3) {
        return this.mobIds(args[2]);
      } else {
        if (this.isDel(args, "followplayer")) {
          if (args.length == 3) {
            return this.onlinePlayers(args[2]);
          }

          if (args.length == 4) {
            return this.mobIds(args[3]);
          }
        }

        if (this.isDel(args, "randomworld")) {
          if (args.length == 3) {
            return Bukkit.getWorlds().stream().map(w -> w.getName()).collect(Collectors.toList());
          }

          if (args.length == 4) {
            return this.mobIds(args[3]);
          }
        }

        return args.length == 2 && this.eq(args, 0, "get")
            ? this.filter(args[1], "base64")
            : List.of();
      }
    }
  }

  private List<String> spawnEggKeys() {
    ConfigurationSection sec = this.configs.spawnEggs().getConfigurationSection("spawn-eggs");
    return (List<String>) (sec == null ? List.of() : new ArrayList<>(sec.getKeys(false)));
  }

  private boolean eq(String[] args, int i, String v) {
    return args.length > i && args[i].equalsIgnoreCase(v);
  }

  private boolean isSet(String[] args, String sub) {
    return args.length > 1 && this.eq(args, 0, "set") && this.eq(args, 1, sub);
  }

  private boolean isDel(String[] args, String sub) {
    return args.length > 1 && this.eq(args, 0, "del") && this.eq(args, 1, sub);
  }

  private List<String> mobIds(String prefix) {
    String p = prefix == null ? "" : prefix.toLowerCase();
    return this.mobs.registeredIds().stream()
        .filter(id -> id.startsWith(p))
        .sorted()
        .collect(Collectors.toList());
  }

  private List<String> onlinePlayers(String prefix) {
    String p = prefix == null ? "" : prefix.toLowerCase();
    List<String> out = new ArrayList<>();

    for (Player pl : Bukkit.getOnlinePlayers()) {
      if (pl.getName().toLowerCase().startsWith(p)) {
        out.add(pl.getName());
      }
    }

    return out;
  }

  private List<String> numbers(String prefix, String... values) {
    String p = prefix == null ? "" : prefix;
    List<String> out = new ArrayList<>();

    for (String v : values) {
      if (v.startsWith(p)) {
        out.add(v);
      }
    }

    return out;
  }

  private List<String> filter(String prefix, String... values) {
    String p = prefix == null ? "" : prefix.toLowerCase();
    List<String> out = new ArrayList<>();

    for (String v : values) {
      if (v.startsWith(p)) {
        out.add(v);
      }
    }

    return out;
  }

  private List<String> filter(String prefix, List<String> values) {
    String p = prefix == null ? "" : prefix.toLowerCase();
    List<String> out = new ArrayList<>();

    for (String v : values) {
      if (v.toLowerCase().startsWith(p)) {
        out.add(v);
      }
    }

    return out;
  }
}
