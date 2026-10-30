package org.plugin.theMob.control;

import java.util.EnumSet;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.plugin.theMob.control.debug.SpawnStatistics;

public final class SpawnGate {
  private final SpawnBudgetManager budgets;
  private final ThrottleManager throttling;
  private final ScalingManager scaling;
  private final TpsTracker tps;
  private final SpawnStatistics stats = new SpawnStatistics();

  public SpawnGate(
      SpawnBudgetManager budgets,
      ThrottleManager throttling,
      ScalingManager scaling,
      TpsTracker tps) {
    this.budgets = budgets;
    this.throttling = throttling;
    this.scaling = scaling;
    this.tps = tps;
  }

  public SpawnGateResult check(World world, SpawnRole role) {
    this.stats.incAttempt();
    double tps1m = this.tps.tps1m();
    int players = Bukkit.getOnlinePlayers().size();
    double scaleMult = this.scaling.multiplierForPlayers(players);
    ThrottleManager.State tpsState = this.throttling.state(tps1m);
    EnumSet<SpawnGateResult.Reason> reasons = EnumSet.noneOf(SpawnGateResult.Reason.class);
    if (tpsState == ThrottleManager.State.HARD_STOP) {
      this.stats.incBlockedThrottle();
      return SpawnGateResult.deny(
          EnumSet.of(SpawnGateResult.Reason.THROTTLE_HARD_STOP), tps1m, scaleMult);
    } else {
      BudgetConfig cfg = this.budgets.config();
      if (cfg.globalEnabled) {
        if (this.budgets.aliveTotal() >= cfg.globalTotal) {
          reasons.add(SpawnGateResult.Reason.GLOBAL_BUDGET_TOTAL);
          this.stats.incBlockedBudget();
        }

        if (role == SpawnRole.BOSS && this.budgets.aliveBosses() >= cfg.globalBosses) {
          reasons.add(SpawnGateResult.Reason.GLOBAL_BUDGET_BOSSES);
          this.stats.incBlockedBudget();
        }

        if (role == SpawnRole.MINION && this.budgets.aliveMinions() >= cfg.globalMinions) {
          reasons.add(SpawnGateResult.Reason.GLOBAL_BUDGET_MINIONS);
          this.stats.incBlockedBudget();
        }
      }

      if (cfg.worldEnabled && world != null) {
        int limit = cfg.worldTotalBudget(world.getName());
        if (limit >= 0 && this.budgets.aliveInWorld(world.getName()) >= limit) {
          reasons.add(SpawnGateResult.Reason.WORLD_BUDGET_TOTAL);
          this.stats.incBlockedBudget();
        }
      }

      if (tpsState == ThrottleManager.State.WARNING) {
        reasons.add(SpawnGateResult.Reason.THROTTLE_WARNING);
      }

      if (tpsState == ThrottleManager.State.CRITICAL) {
        reasons.add(SpawnGateResult.Reason.THROTTLE_CRITICAL);
      }

      if (!reasons.isEmpty()) {
        boolean softOnly =
            reasons.stream()
                .allMatch(
                    r ->
                        r == SpawnGateResult.Reason.THROTTLE_WARNING
                            || r == SpawnGateResult.Reason.THROTTLE_CRITICAL);
        if (softOnly) {
          this.stats.incSuccess();
          return SpawnGateResult.allow(tps1m, scaleMult);
        } else {
          return SpawnGateResult.deny(reasons, tps1m, scaleMult);
        }
      } else {
        this.stats.incSuccess();
        return SpawnGateResult.allow(tps1m, scaleMult);
      }
    }
  }

  public double effectiveIntervalMultiplier() {
    double tps1m = this.tps.tps1m();
    double tpsMult = this.throttling.intervalMultiplier(this.throttling.state(tps1m));
    double scaleMult = this.scaling.multiplierForPlayers(Bukkit.getOnlinePlayers().size());
    return scaleMult <= 0.0 ? Double.POSITIVE_INFINITY : tpsMult / scaleMult;
  }

  public SpawnStatistics stats() {
    return this.stats;
  }

  public SpawnBudgetManager budgets() {
    return this.budgets;
  }
}
