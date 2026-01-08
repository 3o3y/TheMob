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

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player p)) {
            sender.sendMessage("This command is player-only.");
            return true;
        }

        if (!p.hasPermission(PERMISSION)) {
            p.sendMessage("§cYou don't have permission.");
            return true;
        }

        if (debug == null) {
            p.sendMessage("§cCombat debug system not available.");
            return true;
        }

        boolean enable;

        if (args.length == 0) {
            enable = !debug.isEnabled(p); // TOGGLE
        } else if (args[0].equalsIgnoreCase("on")) {
            enable = true;
        } else if (args[0].equalsIgnoreCase("off")) {
            enable = false;
        } else {
            p.sendMessage("§7Usage: §e/combatdebug [on|off]");
            return true;
        }

        debug.setEnabled(p, enable);
        p.sendMessage(enable
                ? "§aCombat debug enabled."
                : "§cCombat debug disabled."
        );

        return true;
    }
}
