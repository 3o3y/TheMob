package org.plugin.theMob.control;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.plugin.theMob.control.debug.SpawnStatistics;

import java.util.EnumSet;

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
            TpsTracker tps
    ) {
        this.budgets = budgets;
        this.throttling = throttling;
        this.scaling = scaling;
        this.tps = tps;
    }

    public SpawnGateResult check(World world, SpawnRole role) {

        stats.incAttempt();

        double tps1m = tps.tps1m();
        int players = Bukkit.getOnlinePlayers().size();
        double scaleMult = scaling.multiplierForPlayers(players);

        ThrottleManager.State tpsState = throttling.state(tps1m);
        EnumSet<SpawnGateResult.Reason> reasons =
                EnumSet.noneOf(SpawnGateResult.Reason.class);

        // HARD STOP
        if (tpsState == ThrottleManager.State.HARD_STOP) {
            stats.incBlockedThrottle();
            return SpawnGateResult.deny(
                    EnumSet.of(SpawnGateResult.Reason.THROTTLE_HARD_STOP),
                    tps1m, scaleMult
            );
        }

        BudgetConfig cfg = budgets.config();

        if (cfg.globalEnabled) {

            if (budgets.aliveTotal() >= cfg.globalTotal) {
                reasons.add(SpawnGateResult.Reason.GLOBAL_BUDGET_TOTAL);
                stats.incBlockedBudget();
            }

            if (role == SpawnRole.BOSS &&
                    budgets.aliveBosses() >= cfg.globalBosses) {
                reasons.add(SpawnGateResult.Reason.GLOBAL_BUDGET_BOSSES);
                stats.incBlockedBudget();
            }

            if (role == SpawnRole.MINION &&
                    budgets.aliveMinions() >= cfg.globalMinions) {
                reasons.add(SpawnGateResult.Reason.GLOBAL_BUDGET_MINIONS);
                stats.incBlockedBudget();
            }
        }

        if (cfg.worldEnabled && world != null) {
            int limit = cfg.worldTotalBudget(world.getName());
            if (limit > 0 && budgets.aliveInWorld(world.getName()) >= limit) {
                reasons.add(SpawnGateResult.Reason.WORLD_BUDGET_TOTAL);
                stats.incBlockedBudget();
            }
        }

        if (tpsState == ThrottleManager.State.WARNING)
            reasons.add(SpawnGateResult.Reason.THROTTLE_WARNING);

        if (tpsState == ThrottleManager.State.CRITICAL)
            reasons.add(SpawnGateResult.Reason.THROTTLE_CRITICAL);

        if (!reasons.isEmpty()) {
            boolean softOnly = reasons.stream().allMatch(r ->
                    r == SpawnGateResult.Reason.THROTTLE_WARNING ||
                            r == SpawnGateResult.Reason.THROTTLE_CRITICAL
            );

            if (softOnly) {
                stats.incSuccess();
                return SpawnGateResult.allow(tps1m, scaleMult);
            }

            return SpawnGateResult.deny(reasons, tps1m, scaleMult);
        }

        stats.incSuccess();
        return SpawnGateResult.allow(tps1m, scaleMult);
    }

    public double effectiveIntervalMultiplier() {
        double tps1m = tps.tps1m();
        double tpsMult = throttling.intervalMultiplier(throttling.state(tps1m));
        double scaleMult = scaling.multiplierForPlayers(Bukkit.getOnlinePlayers().size());
        if (scaleMult <= 0) return Double.POSITIVE_INFINITY;
        return tpsMult / scaleMult;
    }

    // ---- v1.8 API ----
    public SpawnStatistics stats() { return stats; }
    public SpawnBudgetManager budgets() { return budgets; }
}
