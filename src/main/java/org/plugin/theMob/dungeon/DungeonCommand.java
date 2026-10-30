package org.plugin.theMob.dungeon;

import java.util.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class DungeonCommand {
  public static boolean execute(DungeonService service, CommandSender sender, String[] args) {
    if (service == null) {
      sender.sendMessage("§cDungeon-System nicht verfuegbar.");
      return true;
    }
    String action = args.length < 2 ? "random" : args[1].toLowerCase(Locale.ROOT);
    switch (action) {
      case "status" -> sender.sendMessage("§6[TheMob] " + service.status());
      case "cancel" -> service.cancel(sender);
      case "boss" -> {
        if (args.length < 4) sender.sendMessage("§e/mob dungeon boss <ID> <Boss-Mob-ID|off>");
        else service.setBoss(sender, args[2], args[3]);
      }
      case "altar" -> {
        if (args.length < 3) sender.sendMessage("§e/mob dungeon altar <ID> [here]");
        else service.altar(sender, args[2], args.length > 3 && args[3].equalsIgnoreCase("here"));
      }
      case "resume" -> {
        if (args.length < 3) sender.sendMessage("§e/mob dungeon resume <ID>");
        else service.resume(sender, args[2]);
      }
      case "list" -> {
        int page = 1;
        if (args.length > 2)
          try {
            page = Integer.parseInt(args[2]);
          } catch (NumberFormatException ignored) {
          }
        var records =
            service.records().stream()
                .filter(r -> service.admin(sender) || r.state == DungeonRecord.State.READY)
                .toList();
        int pages = Math.max(1, (records.size() + 7) / 8);
        page = org.plugin.theMob.compat.Numbers.clamp(page, 1, pages);
        sender.sendMessage("§6Dungeons " + page + "/" + pages);
        records.stream()
            .skip((page - 1) * 8L)
            .limit(8)
            .forEach(
                r ->
                    sender.sendMessage(
                        "§e"
                            + r.id
                            + " §f"
                            + r.style.label()
                            + " §7"
                            + r.state
                            + " / "
                            + r.levels
                            + " Ebenen"));
      }
      case "info" -> {
        var r = args.length > 2 ? service.get(args[2]) : null;
        if (r == null) sender.sendMessage("§e/mob dungeon info <ID>");
        else {
          sender.sendMessage(
              "§7Endboss: "
                  + (r.completionBossId.isEmpty() ? "nicht festgelegt" : r.completionBossId));
          sender.sendMessage(
              "§6"
                  + r.id
                  + " §f"
                  + r.style.label()
                  + " §7"
                  + r.state
                  + " | "
                  + r.levels
                  + " Ebenen | "
                  + r.pathLength
                  + " Bloecke Wegenetz");
          sender.sendMessage(
              "§7Grundflaeche: "
                  + DungeonPlan.WIDTH
                  + " x "
                  + DungeonPlan.DEPTH
                  + " | Raeume: "
                  + r.rooms.size()
                  + " | Seed: "
                  + r.seed);
          if (service.admin(sender))
            sender.sendMessage(
                "§7Start: "
                    + (r.x() + 56)
                    + ", "
                    + (r.baseY + 9)
                    + ", "
                    + (r.z() + 123)
                    + " in "
                    + r.worldName
                    + " | /mob dungeon edit "
                    + r.id);
        }
      }
      case "enter", "tp", "leave", "edit" -> {
        if (!(sender instanceof Player player))
          sender.sendMessage("§cDieser Befehl ist nur fuer Spieler.");
        else if (action.equals("leave")) service.leave(player);
        else if (args.length < 3)
          sender.sendMessage(
              "§e/mob dungeon " + action + " <" + (action.equals("edit") ? "ID|off" : "ID") + ">");
        else if (action.equals("edit")) service.edit(player, args[2]);
        else service.enter(player, args[2]);
      }
      case "help" ->
          sender.sendMessage(
              "§e/mob dungeon [random|krypta|turm|mine|weltenbaum|gottesturm|labyrinth] §7| list |"
                  + " status | info ID | enter ID | leave | edit ID/off | boss ID Mob-ID | cancel |"
                  + " resume ID");
      default -> {
        try {
          service.create(sender, action.equals("random") ? null : DungeonStyle.parse(action));
        } catch (IllegalArgumentException failure) {
          sender.sendMessage("§e" + failure.getMessage());
        }
      }
    }
    return true;
  }

  private DungeonCommand() {}
}
