package org.plugin.theMob.combat;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class CombatDebugCommand implements CommandExecutor {
  private static final String PERMISSION = "themob.combat.debug";
  private final CombatDebugService debug;

  public CombatDebugCommand(CombatDebugService debug) {
    this.debug = debug;
  }

  public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
    if (sender instanceof Player p) {
      if (!p.hasPermission("themob.combat.debug")) {
        p.sendMessage("§cYou don't have permission.");
        return true;
      } else if (this.debug == null) {
        p.sendMessage("§cCombat debug system not available.");
        return true;
      } else {
        boolean enable;
        if (args.length == 0) {
          enable = !this.debug.isEnabled(p);
        } else if (args[0].equalsIgnoreCase("on")) {
          enable = true;
        } else {
          if (!args[0].equalsIgnoreCase("off")) {
            p.sendMessage("§7Usage: §e/combatdebug [on|off]");
            return true;
          }

          enable = false;
        }

        this.debug.setEnabled(p, enable);
        p.sendMessage(enable ? "§aCombat debug enabled." : "§cCombat debug disabled.");
        return true;
      }
    } else {
      sender.sendMessage("This command is player-only.");
      return true;
    }
  }
}
