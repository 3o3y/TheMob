package org.plugin.theMob.control.command;

import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.plugin.theMob.control.AutomationScalingSystem;
import org.plugin.theMob.control.BossArenaStateService;
import org.plugin.theMob.control.BudgetConfig;
import org.plugin.theMob.control.SpawnBudgetManager;
import org.plugin.theMob.control.SpawnGate;
import org.plugin.theMob.control.ThrottleManager;
import org.plugin.theMob.control.TpsTracker;
import org.plugin.theMob.control.cooldown.BossCooldownService;

public final class MobDiagCommand {
  private final AutomationScalingSystem automation;

  public MobDiagCommand(AutomationScalingSystem automation) {
    this.automation = automation;
  }

  public void execute(CommandSender sender, String[] args) {
    if (!sender.hasPermission("themob.admin")) {
      sender.sendMessage(ChatColor.RED + "No permission.");
    } else {
      String sub = args.length >= 2 ? args[1].toLowerCase() : "status";
      switch (sub) {
        case "status":
          TpsTracker tps = this.automation.tps();
          SpawnGate gate = this.automation.gate();
          SpawnBudgetManager budgets = this.automation.budgets();
          BudgetConfig cfg = budgets.config();
          ThrottleManager throttle = this.automation.throttling();
          int players = Bukkit.getOnlinePlayers().size();
          double tps1m = tps.tps1m();
          double mspt1m = tps.mspt1m();
          ThrottleManager.State throttleState = throttle.state(tps1m);
          double intervalMult = gate.effectiveIntervalMultiplier();
          double scaleMult = this.automation.scaling().multiplierForPlayers(players);
          int aliveTotal = budgets.aliveTotal();
          int aliveBosses = budgets.aliveBosses();
          int aliveMinions = budgets.aliveMinions();
          sender.sendMessage(ChatColor.GOLD + "=== TheMob – System Status ===");
          sender.sendMessage(
              ChatColor.YELLOW
                  + "Players: "
                  + ChatColor.WHITE
                  + players
                  + ChatColor.GRAY
                  + " | Scaling x"
                  + this.fmt(scaleMult));
          sender.sendMessage(
              ChatColor.YELLOW
                  + "Performance: "
                  + ChatColor.WHITE
                  + this.fmt(tps1m)
                  + " TPS"
                  + ChatColor.GRAY
                  + " / "
                  + ChatColor.WHITE
                  + this.fmt(mspt1m)
                  + " MSPT "
                  + ChatColor.GRAY
                  + "("
                  + this.colorStatus(tps.status())
                  + ChatColor.GRAY
                  + ")");
          sender.sendMessage(
              ChatColor.YELLOW
                  + "Throttle: "
                  + ChatColor.WHITE
                  + throttleState.name()
                  + ChatColor.GRAY
                  + " | Interval x"
                  + this.fmt(throttle.intervalMultiplier(throttleState)));
          sender.sendMessage(
              ChatColor.YELLOW
                  + "Effective spawn interval: "
                  + ChatColor.WHITE
                  + "x"
                  + this.fmt(intervalMult));
          sender.sendMessage(
              ChatColor.YELLOW
                  + "Alive entities: "
                  + ChatColor.WHITE
                  + aliveTotal
                  + ChatColor.GRAY
                  + " (Bosses "
                  + aliveBosses
                  + ", Minions "
                  + aliveMinions
                  + ")");
          if (cfg.globalEnabled) {
            sender.sendMessage(
                ChatColor.GRAY
                    + "Global caps: "
                    + aliveTotal
                    + "/"
                    + cfg.globalTotal
                    + " mobs | "
                    + aliveBosses
                    + "/"
                    + cfg.globalBosses
                    + " bosses");
          }

          if (cfg.worldEnabled && cfg.worldCapSum() > 0) {
            sender.sendMessage(
                ChatColor.GRAY + "World caps (sum): " + aliveTotal + "/" + cfg.worldCapSum());
          }

          if (tps.isDropping()) {
            sender.sendMessage(ChatColor.RED + "⚠ TPS is dropping – spawns slowed.");
          }

          if (throttleState == ThrottleManager.State.HARD_STOP) {
            sender.sendMessage(ChatColor.DARK_RED + "⛔ HARD STOP ACTIVE – spawning blocked!");
          }
          break;
        case "tps":
          TpsTracker t = this.automation.tps();
          sender.sendMessage(ChatColor.GOLD + "=== TPS Diagnostics ===");
          sender.sendMessage(
              ChatColor.YELLOW
                  + "TPS (1s/1m): "
                  + ChatColor.WHITE
                  + this.fmt(t.tps1s())
                  + ChatColor.GRAY
                  + " / "
                  + ChatColor.WHITE
                  + this.fmt(t.tps1m()));
          sender.sendMessage(
              ChatColor.YELLOW
                  + "MSPT (now/1s/1m): "
                  + ChatColor.WHITE
                  + this.fmt(t.mspt())
                  + ChatColor.GRAY
                  + " / "
                  + ChatColor.WHITE
                  + this.fmt(t.mspt1s())
                  + ChatColor.GRAY
                  + " / "
                  + ChatColor.WHITE
                  + this.fmt(t.mspt1m()));
          sender.sendMessage(ChatColor.YELLOW + "Status: " + this.colorStatus(t.status()));
          ThrottleManager tm = this.automation.throttling();
          ThrottleManager.State state = tm.state(t.tps1m());
          sender.sendMessage(
              ChatColor.YELLOW
                  + "Throttle: "
                  + ChatColor.WHITE
                  + state.name()
                  + ChatColor.GRAY
                  + " (x"
                  + this.fmt(tm.intervalMultiplier(state))
                  + ")");
          if (t.isDropping()) {
            sender.sendMessage(ChatColor.RED + "⚠ TPS is dropping.");
          }
          break;
        case "budgets":
          SpawnBudgetManager bm = this.automation.budgets();
          BudgetConfig cfgx = bm.config();
          sender.sendMessage(ChatColor.GOLD + "=== Spawn Budgets ===");
          sender.sendMessage(ChatColor.YELLOW + "Global caps:");
          sender.sendMessage(
              ChatColor.GRAY + " - Enabled: " + ChatColor.WHITE + cfgx.globalEnabled);
          sender.sendMessage(
              ChatColor.GRAY
                  + " - Total: "
                  + ChatColor.WHITE
                  + (cfgx.globalEnabled ? cfgx.globalTotal : "∞"));
          sender.sendMessage(
              ChatColor.GRAY
                  + " - Bosses: "
                  + ChatColor.WHITE
                  + (cfgx.globalEnabled ? cfgx.globalBosses : "∞"));
          sender.sendMessage(
              ChatColor.GRAY
                  + " - Minions: "
                  + ChatColor.WHITE
                  + (cfgx.globalEnabled ? cfgx.globalMinions : "∞"));
          sender.sendMessage(ChatColor.YELLOW + "World caps:");
          if (!cfgx.worldEnabled || cfgx.worldTotals.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + " - Disabled");
            return;
          }

          int sum = 0;

          for (Entry<String, Integer> e : cfgx.worldTotals.entrySet()) {
            if (e.getValue() > 0) {
              sum += e.getValue();
              sender.sendMessage(
                  ChatColor.GRAY + " - " + e.getKey() + ": " + ChatColor.WHITE + e.getValue());
            }
          }

          sender.sendMessage(ChatColor.YELLOW + "World cap sum: " + ChatColor.WHITE + sum);
          break;
        case "throttle":
          ThrottleManager throttleManager = this.automation.throttling();
          double currentTps = this.automation.tps().tps1m();
          ThrottleManager.State diagnosticStateLocal = throttleManager.state(currentTps);
          sender.sendMessage(ChatColor.GOLD + "=== Throttle Status ===");
          sender.sendMessage(
              ChatColor.YELLOW + "State: " + ChatColor.WHITE + diagnosticStateLocal.name());
          if (diagnosticStateLocal == ThrottleManager.State.HARD_STOP) {
            sender.sendMessage(ChatColor.DARK_RED + "Spawning BLOCKED");
          } else {
            sender.sendMessage(
                ChatColor.YELLOW
                    + "Interval x"
                    + ChatColor.WHITE
                    + this.fmt(throttleManager.intervalMultiplier(diagnosticStateLocal)));
          }

          sender.sendMessage(ChatColor.GRAY + "Thresholds:");
          sender.sendMessage(ChatColor.GRAY + " NORMAL ≥ " + throttleManager.tpsNormal());
          sender.sendMessage(ChatColor.GRAY + " WARNING < " + throttleManager.tpsNormal());
          sender.sendMessage(ChatColor.GRAY + " CRITICAL < " + throttleManager.tpsWarning());
          sender.sendMessage(ChatColor.GRAY + " HARD_STOP < " + throttleManager.tpsCritical());
          break;
        case "cooldown":
          BossCooldownService cd = this.automation.bossCooldowns();
          SpawnBudgetManager budgetsx = this.automation.budgets();
          BossArenaStateService stateSvc = this.automation.bossArenaState();
          if (!cd.enabled()) {
            sender.sendMessage(ChatColor.GRAY + "Boss cooldowns disabled.");
            return;
          }

          sender.sendMessage(ChatColor.GOLD + "=== Boss Cooldowns ===");
          Set<String> all = new HashSet<>();
          all.addAll(cd.allBossIds());
          all.addAll(budgetsx.aliveBossIds());
          if (all.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "NONE");
            return;
          }

          for (String bossId : all) {
            sender.sendMessage(ChatColor.YELLOW + bossId + ": " + stateSvc.stateOf(bossId, null));
          }
          break;
        case "alive":
          SpawnBudgetManager b = this.automation.budgets();
          int total = b.aliveTotal();
          int bosses = b.aliveBosses();
          int minions = b.aliveMinions();
          int mobs = Math.max(0, total - bosses - minions);
          sender.sendMessage(ChatColor.GOLD + "=== Alive Entities ===");
          sender.sendMessage(ChatColor.YELLOW + "Total: " + ChatColor.WHITE + total);
          sender.sendMessage(ChatColor.YELLOW + "Mobs: " + ChatColor.WHITE + mobs);
          sender.sendMessage(ChatColor.YELLOW + "Minions: " + ChatColor.WHITE + minions);
          sender.sendMessage(ChatColor.YELLOW + "Bosses: " + ChatColor.WHITE + bosses);
          break;
        default:
          sender.sendMessage(
              ChatColor.RED + "Usage: /mob diag [status|tps|budgets|throttle|alive|cooldown]");
      }
    }
  }

  private String fmt(double v) {
    return String.format("%.2f", v);
  }

  private String colorStatus(String s) {
    return (String)
        (switch (s) {
          case "OK" -> ChatColor.GREEN + s;
          case "WARN" -> ChatColor.YELLOW + s;
          case "CRITICAL" -> ChatColor.RED + s;
          case "DANGER" -> ChatColor.DARK_RED + s + " ☠";
          default -> s;
        });
  }
}
