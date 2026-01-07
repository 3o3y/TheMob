package org.plugin.theMob.control.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.plugin.theMob.control.AutomationScalingSystem;

public final class TheMobDebugCommand {

    private final AutomationScalingSystem sys;

    public TheMobDebugCommand(AutomationScalingSystem sys) {
        this.sys = sys;
    }

    public void execute(CommandSender sender) {

        if (!sender.hasPermission("themob.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return;
        }

        var stats = sys.gate().stats();
        var tps = sys.tps();
        var throttle = sys.throttling();

        sender.sendMessage(ChatColor.GOLD + "=== TheMob v1.8 – Debug ===");

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
