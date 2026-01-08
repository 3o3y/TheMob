package org.plugin.theMob.control.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.control.AutomationScalingSystem;
import org.plugin.theMob.mob.MobManager;
import org.plugin.theMob.spawn.SpawnController;

public final class TheMobDebugCommand {

    private final AutomationScalingSystem sys;

    // Original
    public TheMobDebugCommand(AutomationScalingSystem sys) {
        this.sys = sys;
    }

    // Compatibility overload
    public TheMobDebugCommand(TheMob plugin, MobManager mobManager, SpawnController spawnController) {
        this.sys = plugin == null ? null : plugin.automation();
    }

    public void execute(CommandSender sender) {

        if (sys == null) {
            sender.sendMessage(ChatColor.RED + "Debug system not available.");
            return;
        }

        if (!sender.hasPermission("themob.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return;
        }

        var stats = sys.gate().stats();
        var tps = sys.tps();
        var throttle = sys.throttling();

        sender.sendMessage(ChatColor.GOLD + "=== TheMob – Debug ===");

        sender.sendMessage(ChatColor.YELLOW + "TPS: " +
                ChatColor.WHITE + String.format("%.2f", tps.tps1m()) +
                ChatColor.GRAY + " | MSPT: " +
                ChatColor.WHITE + String.format("%.2f", tps.mspt1m()));

        sender.sendMessage(ChatColor.YELLOW + "Throttle: " +
                ChatColor.WHITE + throttle.state(tps.tps1m()).name());

        sender.sendMessage(ChatColor.YELLOW + "Spawn statistics:");
        sender.sendMessage(ChatColor.GRAY + " - Attempts: " + stats.getAttempts());
        sender.sendMessage(ChatColor.GRAY + " - Success: " + stats.getSuccess());
        sender.sendMessage(ChatColor.GRAY + " - Blocked (budget): " + stats.getBlockedBudget());
        sender.sendMessage(ChatColor.GRAY + " - Blocked (throttle): " + stats.getBlockedThrottle());
        sender.sendMessage(ChatColor.GRAY + " - Blocked (cooldown): " + stats.getBlockedCooldown());
    }
}
