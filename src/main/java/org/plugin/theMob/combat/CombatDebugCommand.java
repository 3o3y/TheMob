package org.plugin.theMob.combat;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class CombatDebugCommand implements CommandExecutor {

    private final CombatDebugService debug;

    public CombatDebugCommand(CombatDebugService debug) {
        this.debug = debug;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        boolean on = args.length == 0 || args[0].equalsIgnoreCase("on");
        if (args.length > 0 && args[0].equalsIgnoreCase("off")) on = false;

        debug.setEnabled(p, on);
        p.sendMessage(on ? "§aCombat debug enabled." : "§cCombat debug disabled.");
        return true;
    }
}
