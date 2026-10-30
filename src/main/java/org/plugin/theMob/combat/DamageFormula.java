package org.plugin.theMob.combat;

import java.util.Map;

/** Pure combat arithmetic. Damage returned here is event RAW damage. */
public final class DamageFormula {
  private DamageFormula() {}

  public static double finite(double value, double fallback) {
    return Double.isFinite(value) ? value : fallback;
  }

  public static double clamp(double value, double min, double max) {
    return org.plugin.theMob.compat.Numbers.clamp(finite(value, min), min, max);
  }

  public static double stat(Map<String, Double> stats, String key, double fallback) {
    return finite(stats.getOrDefault(key, fallback), fallback);
  }

  public static double defenseMultiplier(double defense, double defenseK) {
    double k = Math.max(1, finite(defenseK, 200));
    return k / (k + Math.max(0, finite(defense, 0)));
  }

  public static DamageResult calculate(
      double vanilla,
      Map<String, Double> stats,
      double multiplier,
      double defense,
      double defenseK,
      double maxCrit,
      double roll) {
    double base =
        Math.max(0, finite(vanilla, 0) + stat(stats, "damage", 0) + stat(stats, "extra_damage", 0));
    double chance =
        clamp(stats.getOrDefault("crit_chance", stats.getOrDefault("crit", 0.0)) / 100, 0, .75);
    double critMultiplier = stat(stats, "crit_multiplier", 1);
    if (critMultiplier > 0 && critMultiplier < 1) critMultiplier += 1;
    critMultiplier = clamp(critMultiplier, 1, Math.max(1, finite(maxCrit, 10)));
    boolean crit = roll < chance;
    double raw = base * (crit ? critMultiplier : 1) * clamp(multiplier, 0, 50);
    // RPG defense is separate from the server's vanilla ARMOR reduction.
    raw *= defenseMultiplier(defense, defenseK);
    raw = clamp(raw, 0, 1_000_000);
    double steal = clamp(stat(stats, "lifesteal", 0) / 100, 0, .5);
    return new DamageResult(
        vanilla,
        base,
        crit,
        chance,
        critMultiplier,
        multiplier,
        1,
        raw,
        raw * steal,
        Map.of("BaseSource", "VANILLA+ITEM", "RawDamage", String.valueOf(raw)));
  }
}
