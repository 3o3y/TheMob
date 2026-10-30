package org.plugin.theMob.command;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.plugin.theMob.TheMob;

final class MobContentCommand {
  static boolean execute(TheMob plugin, CommandSender sender, String[] args) {
    if (!sender.hasPermission("themob.admin")) {
      sender.sendMessage("§cNo permission.");
      return true;
    }
    if (args[0].equalsIgnoreCase("performance")) {
      sender.sendMessage(
          "§6TheMob §f"
              + plugin.activity().trackedCount()
              + " verfolgt, "
              + plugin.activity().coldCount()
              + " kalt");
      sender.sendMessage(
          "§7TPS: "
              + String.format(Locale.ROOT, "%.2f", plugin.automation().tps().tps1m())
              + "; KI-Aktualisierungen pro Durchlauf begrenzt.");
      return true;
    }
    if (args[0].equalsIgnoreCase("items") || args[0].equalsIgnoreCase("mobs")) {
      int page = 1;
      try {
        if (args.length > 1) page = Math.max(1, Integer.parseInt(args[1]));
      } catch (NumberFormatException invalid) {
        sender.sendMessage("§e/mob items [Seite]");
        return true;
      }
      boolean mobs = args[0].equalsIgnoreCase("mobs");
      var ids =
          (mobs ? plugin.mobs().registeredIds() : plugin.gear().ids()).stream().sorted().toList();
      int pages = Math.max(1, (ids.size() + 11) / 12);
      page = Math.min(page, pages);
      sender.sendMessage(
          "§6"
              + (mobs ? "Mobs " : "Items ")
              + page
              + "/"
              + pages
              + " §7| "
              + (mobs ? "/mob spawn <ID> | /mob egg <ID>" : "/mob item <ID> [Spieler]"));
      ids.stream().skip((page - 1L) * 12).limit(12).forEach(id -> sender.sendMessage("§f" + id));
      return true;
    }
    if (args.length < 2) {
      sender.sendMessage("§e/mob item <ID> [Spieler]");
      return true;
    }
    Player receiver =
        args.length > 2 ? Bukkit.getPlayerExact(args[2]) : sender instanceof Player p ? p : null;
    if (receiver == null) {
      sender.sendMessage("§cSpieler nicht online.");
      return true;
    }
    var item = plugin.api().createItem(args[1]).orElse(null);
    if (item == null) {
      sender.sendMessage("§cUnbekanntes Item. /mob items");
      return true;
    }
    if (receiver.getInventory().firstEmpty() < 0) {
      sender.sendMessage("§cInventar ist voll.");
      return true;
    }
    receiver.getInventory().addItem(item);
    sender.sendMessage("§aItem erhalten: " + args[1]);
    return true;
  }
}
