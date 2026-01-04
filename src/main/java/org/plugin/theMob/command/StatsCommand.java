package org.plugin.theMob.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.plugin.theMob.player.stats.PlayerStatCache;
import org.plugin.theMob.player.stats.menu.StatsMenuService;

public final class StatsCommand implements CommandExecutor {

    private final StatsMenuService menu;
    private final PlayerStatCache cache;

    public StatsCommand(StatsMenuService menu, PlayerStatCache cache) {
        this.menu = menu;
        this.cache = cache;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        cache.invalidate(p);
        menu.open(p);
        return true;
    }
}
