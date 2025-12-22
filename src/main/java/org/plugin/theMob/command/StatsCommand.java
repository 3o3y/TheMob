package org.plugin.theMob.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.plugin.theMob.player.stats.menu.StatsMenuService;

public final class StatsCommand implements CommandExecutor {

    private final StatsMenuService menu;
    public StatsCommand(StatsMenuService menu) {
        this.menu = menu;
    }
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (sender instanceof Player p) {
            menu.open(p);
        }
        return true;
    }
}
