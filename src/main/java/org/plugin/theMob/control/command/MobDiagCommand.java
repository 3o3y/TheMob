package org.plugin.theMob.control.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.plugin.theMob.control.AutomationScalingSystem;
import org.plugin.theMob.control.ThrottleManager;

import java.util.HashSet;
import java.util.Set;

public final class MobDiagCommand {

    private final AutomationScalingSystem automation;

    public MobDiagCommand(AutomationScalingSystem automation) {
        this.automation = automation;
    }

    public void execute(CommandSender sender, String[] args) {

        if (!sender.hasPermission("themob.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return;
        }

        String sub = (args.length >= 2 ? args[1].toLowerCase() : "status");

        switch (sub) {

            // =====================================================
            // STATUS
            // =====================================================
            case "status" -> {
                var tps = automation.tps();
                var gate = automation.gate();
                var budgets = automation.budgets();
                var cfg = budgets.config();
                var throttle = automation.throttling();

                int players = Bukkit.getOnlinePlayers().size();

                double tps1m = tps.tps1m();
                double mspt1m = tps.mspt1m();

                var throttleState = throttle.state(tps1m);
                double intervalMult = gate.effectiveIntervalMultiplier();
                double scaleMult = automation.scaling().multiplierForPlayers(players);

                int aliveTotal = budgets.aliveTotal();
                int aliveBosses = budgets.aliveBosses();
                int aliveMinions = budgets.aliveMinions();

                sender.sendMessage(ChatColor.GOLD + "=== TheMob – System Status ===");

                sender.sendMessage(ChatColor.YELLOW + "Players: " +
                        ChatColor.WHITE + players +
                        ChatColor.GRAY + " | Scaling x" + fmt(scaleMult));

                sender.sendMessage(ChatColor.YELLOW + "Performance: " +
                        ChatColor.WHITE + fmt(tps1m) + " TPS" +
                        ChatColor.GRAY + " / " +
                        ChatColor.WHITE + fmt(mspt1m) + " MSPT " +
                        ChatColor.GRAY + "(" + colorStatus(tps.status()) + ChatColor.GRAY + ")");

                sender.sendMessage(ChatColor.YELLOW + "Throttle: " +
                        ChatColor.WHITE + throttleState.name() +
                        ChatColor.GRAY + " | Interval x" + fmt(throttle.intervalMultiplier(throttleState)));

                sender.sendMessage(ChatColor.YELLOW + "Effective spawn interval: " +
                        ChatColor.WHITE + "x" + fmt(intervalMult));

                sender.sendMessage(ChatColor.YELLOW + "Alive entities: " +
                        ChatColor.WHITE + aliveTotal +
                        ChatColor.GRAY + " (Bosses " + aliveBosses +
                        ", Minions " + aliveMinions + ")");

                if (cfg.globalEnabled) {
                    sender.sendMessage(ChatColor.GRAY + "Global caps: " +
                            aliveTotal + "/" + cfg.globalTotal +
                            " mobs | " +
                            aliveBosses + "/" + cfg.globalBosses + " bosses");
                }

                if (cfg.worldEnabled && cfg.worldCapSum() > 0) {
                    sender.sendMessage(ChatColor.GRAY + "World caps (sum): " +
                            aliveTotal + "/" + cfg.worldCapSum());
                }

                if (tps.isDropping()) {
                    sender.sendMessage(ChatColor.RED + "⚠ TPS is dropping – spawns slowed.");
                }

                if (throttleState == ThrottleManager.State.HARD_STOP) {
                    sender.sendMessage(ChatColor.DARK_RED + "⛔ HARD STOP ACTIVE – spawning blocked!");
                }
            }

            // =====================================================
            // TPS
            // =====================================================
            case "tps" -> {
                var t = automation.tps();

                sender.sendMessage(ChatColor.GOLD + "=== TPS Diagnostics ===");
                sender.sendMessage(ChatColor.YELLOW + "TPS (1s/1m): " +
                        ChatColor.WHITE + fmt(t.tps1s()) +
                        ChatColor.GRAY + " / " +
                        ChatColor.WHITE + fmt(t.tps1m()));

                sender.sendMessage(ChatColor.YELLOW + "MSPT (now/1s/1m): " +
                        ChatColor.WHITE + fmt(t.mspt()) +
                        ChatColor.GRAY + " / " +
                        ChatColor.WHITE + fmt(t.mspt1s()) +
                        ChatColor.GRAY + " / " +
                        ChatColor.WHITE + fmt(t.mspt1m()));

                sender.sendMessage(ChatColor.YELLOW + "Status: " + colorStatus(t.status()));

                var tm = automation.throttling();
                var state = tm.state(t.tps1m());

                sender.sendMessage(ChatColor.YELLOW + "Throttle: " +
                        ChatColor.WHITE + state.name() +
                        ChatColor.GRAY + " (x" + fmt(tm.intervalMultiplier(state)) + ")");

                if (t.isDropping()) {
                    sender.sendMessage(ChatColor.RED + "⚠ TPS is dropping.");
                }
            }

            // =====================================================
            // BUDGETS
            // =====================================================
            case "budgets" -> {
                var bm = automation.budgets();
                var cfg = bm.config();

                sender.sendMessage(ChatColor.GOLD + "=== Spawn Budgets ===");

                sender.sendMessage(ChatColor.YELLOW + "Global caps:");
                sender.sendMessage(ChatColor.GRAY + " - Enabled: " + ChatColor.WHITE + cfg.globalEnabled);
                sender.sendMessage(ChatColor.GRAY + " - Total: " +
                        ChatColor.WHITE + (cfg.globalEnabled ? cfg.globalTotal : "∞"));
                sender.sendMessage(ChatColor.GRAY + " - Bosses: " +
                        ChatColor.WHITE + (cfg.globalEnabled ? cfg.globalBosses : "∞"));
                sender.sendMessage(ChatColor.GRAY + " - Minions: " +
                        ChatColor.WHITE + (cfg.globalEnabled ? cfg.globalMinions : "∞"));

                sender.sendMessage(ChatColor.YELLOW + "World caps:");

                if (!cfg.worldEnabled || cfg.worldTotals.isEmpty()) {
                    sender.sendMessage(ChatColor.GRAY + " - Disabled");
                    return;
                }

                int sum = 0;
                for (var e : cfg.worldTotals.entrySet()) {
                    if (e.getValue() > 0) {
                        sum += e.getValue();
                        sender.sendMessage(ChatColor.GRAY + " - " + e.getKey() +
                                ": " + ChatColor.WHITE + e.getValue());
                    }
                }

                sender.sendMessage(ChatColor.YELLOW + "World cap sum: " +
                        ChatColor.WHITE + sum);
            }

            // =====================================================
            // THROTTLE
            // =====================================================
            case "throttle" -> {
                var tm = automation.throttling();
                var tps1m = automation.tps().tps1m();

                var state = tm.state(tps1m);

                sender.sendMessage(ChatColor.GOLD + "=== Throttle Status ===");
                sender.sendMessage(ChatColor.YELLOW + "State: " + ChatColor.WHITE + state.name());

                if (state == ThrottleManager.State.HARD_STOP) {
                    sender.sendMessage(ChatColor.DARK_RED + "Spawning BLOCKED");
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "Interval x" +
                            ChatColor.WHITE + fmt(tm.intervalMultiplier(state)));
                }

                sender.sendMessage(ChatColor.GRAY + "Thresholds:");
                sender.sendMessage(ChatColor.GRAY + " NORMAL ≥ " + tm.tpsNormal());
                sender.sendMessage(ChatColor.GRAY + " WARNING < " + tm.tpsNormal());
                sender.sendMessage(ChatColor.GRAY + " CRITICAL < " + tm.tpsWarning());
                sender.sendMessage(ChatColor.GRAY + " HARD_STOP < " + tm.tpsCritical());
            }

            // =====================================================
            // COOLDOWN
            // =====================================================
            case "cooldown" -> {
                var cd = automation.bossCooldowns();
                var budgets = automation.budgets();
                var stateSvc = automation.bossArenaState();

                if (!cd.enabled()) {
                    sender.sendMessage(ChatColor.GRAY + "Boss cooldowns disabled.");
                    return;
                }

                sender.sendMessage(ChatColor.GOLD + "=== Boss Cooldowns ===");

                Set<String> all = new HashSet<>();
                all.addAll(cd.allBossIds());
                all.addAll(budgets.aliveBossIds());

                if (all.isEmpty()) {
                    sender.sendMessage(ChatColor.GRAY + "NONE");
                    return;
                }

                for (String bossId : all) {
                    sender.sendMessage(ChatColor.YELLOW + bossId + ": " +
                            stateSvc.stateOf(bossId, null));
                }
            }

            // =====================================================
            // ALIVE
            // =====================================================
            case "alive" -> {
                var b = automation.budgets();

                int total = b.aliveTotal();
                int bosses = b.aliveBosses();
                int minions = b.aliveMinions();
                int mobs = Math.max(0, total - bosses - minions);

                sender.sendMessage(ChatColor.GOLD + "=== Alive Entities ===");
                sender.sendMessage(ChatColor.YELLOW + "Total: " + ChatColor.WHITE + total);
                sender.sendMessage(ChatColor.YELLOW + "Mobs: " + ChatColor.WHITE + mobs);
                sender.sendMessage(ChatColor.YELLOW + "Minions: " + ChatColor.WHITE + minions);
                sender.sendMessage(ChatColor.YELLOW + "Bosses: " + ChatColor.WHITE + bosses);
            }

            // =====================================================
            // DEFAULT
            // =====================================================
            default -> sender.sendMessage(ChatColor.RED +
                    "Usage: /mob diag [status|tps|budgets|throttle|alive|cooldown]");
        }
    }

    private String fmt(double v) {
        return String.format("%.2f", v);
    }

    private String colorStatus(String s) {
        return switch (s) {
            case "OK" -> ChatColor.GREEN + s;
            case "WARN" -> ChatColor.YELLOW + s;
            case "CRITICAL" -> ChatColor.RED + s;
            case "DANGER" -> ChatColor.DARK_RED + s + " ☠";
            default -> s;
        };
    }
}
