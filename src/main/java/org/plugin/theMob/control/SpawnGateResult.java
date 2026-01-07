package org.plugin.theMob.control;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public final class SpawnGateResult {

    private final boolean allowed;
    private final Set<Reason> reasons;
    private final double tps;
    private final double multiplier;

    public enum Reason {
        GLOBAL_BUDGET_TOTAL,
        GLOBAL_BUDGET_BOSSES,
        GLOBAL_BUDGET_MINIONS,
        WORLD_BUDGET_TOTAL,

        THROTTLE_HARD_STOP,
        THROTTLE_WARNING,
        THROTTLE_CRITICAL
    }

    private SpawnGateResult(boolean allowed, Set<Reason> reasons, double tps, double multiplier) {
        this.allowed = allowed;
        this.reasons = reasons == null ? Collections.emptySet() : Collections.unmodifiableSet(reasons);
        this.tps = tps;
        this.multiplier = multiplier;
    }

    public static SpawnGateResult allow(double tps, double multiplier) {
        return new SpawnGateResult(true, EnumSet.noneOf(Reason.class), tps, multiplier);
    }

    public static SpawnGateResult deny(Set<Reason> reasons, double tps, double multiplier) {
        return new SpawnGateResult(false, reasons, tps, multiplier);
    }

    public boolean allowed() {
        return allowed;
    }

    public Set<Reason> reasons() {
        return reasons;
    }

    public double tps() {
        return tps;
    }

    public double multiplier() {
        return multiplier;
    }
}
