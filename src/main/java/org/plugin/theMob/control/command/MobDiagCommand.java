package org.plugin.theMob.control.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.plugin.theMob.control.AutomationScalingSystem;
import org.plugin.theMob.control.ThrottleManager;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

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

        String sub = args.length < 2 ? "status" : args[1].toLowerCase();

        switch (sub) {

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

                sender.sendMessage(ChatColor.GOLD + "=== TheMob v1.8 – System Status ===");

                // -------------------------
                // PLAYERS / SCALING
                // -------------------------
                sender.sendMessage(ChatColor.YELLOW + "Players: " +
                        ChatColor.WHITE + players +
                        ChatColor.GRAY + " | Scaling x" + fmt(scaleMult));

                // -------------------------
                // PERFORMANCE
                // -------------------------
                sender.sendMessage(ChatColor.YELLOW + "Performance: " +
                        ChatColor.WHITE + fmt(tps1m) + " TPS" +
                        ChatColor.GRAY + " / " +
                        ChatColor.WHITE + fmt(mspt1m) + " MSPT " +
                        ChatColor.GRAY + "(" + colorStatus(tps.status()) + ChatColor.GRAY + ")");

                // -------------------------
                // THROTTLE
                // -------------------------
                sender.sendMessage(ChatColor.YELLOW + "Throttle: " +
                        ChatColor.WHITE + throttleState.name() +
                        ChatColor.GRAY + " | Interval x" + fmt(throttle.intervalMultiplier(throttleState)));

                // -------------------------
                // SPAWN EFFECTIVE RESULT
                // -------------------------
                sender.sendMessage(ChatColor.YELLOW + "Effective spawn interval: " +
                        ChatColor.WHITE + "x" + fmt(intervalMult));

                // -------------------------
                // BUDGETS (LIVE)
                // -------------------------
                sender.sendMessage(ChatColor.YELLOW + "Alive entities: " +
                        ChatColor.WHITE + aliveTotal +
                        ChatColor.GRAY + " (Bosses " + aliveBosses +
                        ", Minions " + aliveMinions + ")");

                // -------------------------
                // BUDGET CAPS (SUMMARY)
                // -------------------------
                if (cfg.globalEnabled) {
                    sender.sendMessage(ChatColor.GRAY + "Global caps: " +
                            aliveTotal + "/" + cfg.globalTotal +
                            " mobs | " +
                            aliveBosses + "/" + cfg.globalBosses + " bosses");
                }

                if (cfg.worldEnabled && cfg.worldCapSum() > 0) {
                    sender.sendMessage(ChatColor.GRAY + "World caps (sum): " +
                            budgets.aliveTotal() + "/" + cfg.worldCapSum());
                }

                // -------------------------
                // WARNINGS
                // -------------------------
                if (tps.isDropping()) {
                    sender.sendMessage(ChatColor.RED + "⚠ TPS is dropping – spawns are being slowed.");
                }

                if (throttleState == ThrottleManager.State.HARD_STOP) {
                    sender.sendMessage(ChatColor.DARK_RED + "⛔ HARD STOP ACTIVE – spawning blocked!");
                }
            }


            case "tps" -> {
                var t = automation.tps();

                double tps1s = t.tps1s();
                double tps1m = t.tps1m();
                double msptNow = t.mspt();
                double mspt1s = t.mspt1s();
                double mspt1m = t.mspt1m();

                sender.sendMessage(ChatColor.GOLD + "=== TPS Diagnostics ===");
                sender.sendMessage(ChatColor.YELLOW + "TPS (1s/1m): " +
                        ChatColor.WHITE + fmt(tps1s) + ChatColor.GRAY + " / " + ChatColor.WHITE + fmt(tps1m));
                sender.sendMessage(ChatColor.YELLOW + "MSPT (now/1s/1m): " +
                        ChatColor.WHITE + fmt(msptNow) + ChatColor.GRAY + " / " + ChatColor.WHITE + fmt(mspt1s) + ChatColor.GRAY + " / " + ChatColor.WHITE + fmt(mspt1m));

                sender.sendMessage(ChatColor.YELLOW + "Status: " + colorStatus(t.status()));

                // show throttle interpretation
                var tm = automation.throttling();
                ThrottleManager.State state = tm.state(tps1m);
                sender.sendMessage(ChatColor.YELLOW + "Throttle: " + ChatColor.WHITE + state.name()
                        + ChatColor.GRAY + " (x" + fmt(tm.intervalMultiplier(state)) + " interval)");

                if (t.isDropping()) {
                    sender.sendMessage(ChatColor.RED + "⚠ TPS is dropping (recent).");
                }

                sender.sendMessage(ChatColor.GRAY + "Tip: Keep MSPT < 50 for stable gameplay.");
            }

            case "budgets" -> {
                var bm = automation.budgets();
                var cfg = bm.config();

                sender.sendMessage(ChatColor.GOLD + "=== Spawn Budgets (Caps) ===");

                // =========================
                // GLOBAL CAPS
                // =========================
                sender.sendMessage(ChatColor.YELLOW + "Global caps:");
                sender.sendMessage(ChatColor.GRAY + " - Enabled: " +
                        ChatColor.WHITE + cfg.globalEnabled);

                sender.sendMessage(ChatColor.GRAY + " - Total: " +
                        ChatColor.WHITE + (cfg.globalEnabled ? cfg.globalTotal : "∞"));

                sender.sendMessage(ChatColor.GRAY + " - Bosses: " +
                        ChatColor.WHITE + (cfg.globalEnabled ? cfg.globalBosses : "∞"));

                sender.sendMessage(ChatColor.GRAY + " - Minions: " +
                        ChatColor.WHITE + (cfg.globalEnabled ? cfg.globalMinions : "∞"));

                // =========================
                // WORLD CAPS
                // =========================
                sender.sendMessage(ChatColor.YELLOW + "World caps:");

                if (!cfg.worldEnabled || cfg.worldTotals.isEmpty()) {
                    sender.sendMessage(ChatColor.GRAY + " - Disabled or not configured");
                    return;
                }

                int worldCapSum = 0;

                for (var e : cfg.worldTotals.entrySet()) {
                    int cap = e.getValue();
                    if (cap > 0) {
                        worldCapSum += cap;
                        sender.sendMessage(ChatColor.GRAY + " - " +
                                e.getKey() + ": " + ChatColor.WHITE + cap);
                    }
                }

                sender.sendMessage(ChatColor.YELLOW + "World cap sum: " +
                        ChatColor.WHITE + worldCapSum);
            }



            case "throttle" -> {
                var tm = automation.throttling();
                var tps = automation.tps().tps1m();

                ThrottleManager.State state = tm.state(tps);
                double mult = tm.intervalMultiplier(state);

                sender.sendMessage(ChatColor.GOLD + "=== Throttle Status ===");

                // -------------------------
                // CURRENT STATE
                // -------------------------
                sender.sendMessage(ChatColor.YELLOW + "State: " +
                        ChatColor.WHITE + state.name());

                if (state == ThrottleManager.State.HARD_STOP) {
                    sender.sendMessage(ChatColor.YELLOW + "Spawn interval: " +
                            ChatColor.DARK_RED + "BLOCKED");
                    sender.sendMessage(ChatColor.YELLOW + "Effect: " +
                            ChatColor.RED + "Spawning completely disabled");
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "Spawn interval: " +
                            ChatColor.WHITE + "x" + fmt(mult));

                    String effect = switch (state) {
                        case NORMAL -> ChatColor.GREEN + "No throttling";
                        case WARNING -> ChatColor.YELLOW + "Spawns slowed slightly";
                        case CRITICAL -> ChatColor.RED + "Spawns heavily slowed";
                        default -> "";
                    };

                    sender.sendMessage(ChatColor.YELLOW + "Effect: " + effect);
                }

                // -------------------------
                // THRESHOLDS (STATIC INFO)
                // -------------------------
                sender.sendMessage(ChatColor.GRAY + "Thresholds:");
                sender.sendMessage(ChatColor.GRAY + " - NORMAL ≥ " + tm.tpsNormal() + " TPS");
                sender.sendMessage(ChatColor.GRAY + " - WARNING < " + tm.tpsNormal() + " TPS");
                sender.sendMessage(ChatColor.GRAY + " - CRITICAL < " + tm.tpsWarning() + " TPS");
                sender.sendMessage(ChatColor.GRAY + " - HARD STOP < " + tm.tpsCritical() + " TPS");

                sender.sendMessage(ChatColor.GRAY + "Hard stop enabled: " +
                        ChatColor.WHITE + tm.hardStopBelowCritical());

                if (state == ThrottleManager.State.HARD_STOP) {
                    sender.sendMessage(ChatColor.DARK_RED +
                            "⚠ Server protection active – wait for TPS recovery.");
                }
            }


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
                    String state = stateSvc.stateOf(bossId, null);
                    sender.sendMessage(ChatColor.YELLOW + bossId + ": " + state);
                }
            }
            case "alive" -> {
                var b = automation.budgets();

                int total = b.aliveTotal();
                int bosses = b.aliveBosses();
                int minions = b.aliveMinions();
                int mobs = Math.max(0, total - bosses - minions);

                sender.sendMessage(ChatColor.GOLD + "=== Alive Entities ===");
                sender.sendMessage(ChatColor.YELLOW + "Total: " +
                        ChatColor.WHITE + total);
                sender.sendMessage(ChatColor.YELLOW + "Mobs: " +
                        ChatColor.WHITE + mobs);
                sender.sendMessage(ChatColor.YELLOW + "Minions: " +
                        ChatColor.WHITE + minions);
                sender.sendMessage(ChatColor.YELLOW + "Bosses: " +
                        ChatColor.WHITE + bosses);
            }



            default -> sender.sendMessage(ChatColor.RED +
                    "Usage: /mob diag [status|tps|budgets|throttle|alive]");
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
