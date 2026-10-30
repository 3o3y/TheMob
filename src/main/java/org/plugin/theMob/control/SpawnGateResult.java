package org.plugin.theMob.control;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public final class SpawnGateResult {
  private final boolean allowed;
  private final Set<SpawnGateResult.Reason> reasons;
  private final double tps;
  private final double multiplier;

  private SpawnGateResult(
      boolean allowed, Set<SpawnGateResult.Reason> reasons, double tps, double multiplier) {
    this.allowed = allowed;
    this.reasons = reasons == null ? Collections.emptySet() : Collections.unmodifiableSet(reasons);
    this.tps = tps;
    this.multiplier = multiplier;
  }

  public static SpawnGateResult allow(double tps, double multiplier) {
    return new SpawnGateResult(true, EnumSet.noneOf(SpawnGateResult.Reason.class), tps, multiplier);
  }

  public static SpawnGateResult deny(
      Set<SpawnGateResult.Reason> reasons, double tps, double multiplier) {
    return new SpawnGateResult(false, reasons, tps, multiplier);
  }

  public boolean allowed() {
    return this.allowed;
  }

  public Set<SpawnGateResult.Reason> reasons() {
    return this.reasons;
  }

  public double tps() {
    return this.tps;
  }

  public double multiplier() {
    return this.multiplier;
  }

  public static enum Reason {
    GLOBAL_BUDGET_TOTAL,
    GLOBAL_BUDGET_BOSSES,
    GLOBAL_BUDGET_MINIONS,
    WORLD_BUDGET_TOTAL,
    THROTTLE_HARD_STOP,
    THROTTLE_WARNING,
    THROTTLE_CRITICAL;
  }
}
