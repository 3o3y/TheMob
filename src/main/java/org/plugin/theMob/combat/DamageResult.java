package org.plugin.theMob.combat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record DamageResult(
    double vanillaBase,
    double computedBase,
    boolean crit,
    double critChance,
    double critMultiplier,
    double mobMultiplier,
    double conditionalMultiplier,
    double finalDamage,
    double lifestealAmount,
    Map<String, String> debug) {
  public DamageResult(
      double vanillaBase,
      double computedBase,
      boolean crit,
      double critChance,
      double critMultiplier,
      double mobMultiplier,
      double conditionalMultiplier,
      double finalDamage,
      double lifestealAmount,
      Map<String, String> debug) {
    debug =
        debug != null
            ? Collections.unmodifiableMap(new LinkedHashMap<>(debug))
            : Collections.emptyMap();
    this.vanillaBase = vanillaBase;
    this.computedBase = computedBase;
    this.crit = crit;
    this.critChance = critChance;
    this.critMultiplier = critMultiplier;
    this.mobMultiplier = mobMultiplier;
    this.conditionalMultiplier = conditionalMultiplier;
    this.finalDamage = finalDamage;
    this.lifestealAmount = lifestealAmount;
    this.debug = debug;
  }

  public static DamageResult empty(double vanillaBase) {
    double v = Math.max(0.0, vanillaBase);
    return new DamageResult(v, v, false, 0.0, 1.0, 1.0, 1.0, v, 0.0, Collections.emptyMap());
  }
}
