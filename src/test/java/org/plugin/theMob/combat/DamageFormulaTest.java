package org.plugin.theMob.combat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DamageFormulaTest {
  private DamageResult calculate(
      double vanilla, Map<String, Double> stats, double defense, double roll) {
    return DamageFormula.calculate(vanilla, stats, 1, defense, 200, 10, roll);
  }

  @Test
  void ordinaryWeaponKeepsVanillaDamage() {
    assertEquals(7, calculate(7, Map.of(), 0, 1).finalDamage());
  }

  @Test
  void weaponAndExtraStatsAddExactlyOnce() {
    assertEquals(
        42, calculate(7, Map.of("damage", 25.0, "extra_damage", 10.0), 0, 1).finalDamage());
  }

  @Test
  void extraDamageWorksWithoutBaseStat() {
    assertEquals(12, calculate(7, Map.of("extra_damage", 5.0), 0, 1).finalDamage());
  }

  @Test
  void canonicalCritChanceAndLargeItemMultiplierWork() {
    var result = calculate(10, Map.of("crit_chance", 70.0, "crit_multiplier", 3.5), 0, 0);
    assertTrue(result.crit());
    assertEquals(35, result.finalDamage());
  }

  @Test
  void legacyCritAliasStillWorks() {
    assertTrue(calculate(10, Map.of("crit", 50.0), 0, 0).crit());
  }

  @Test
  void percentLifestealIsNotFullHealing() {
    assertEquals(2, calculate(10, Map.of("lifesteal", 20.0), 0, 1).lifestealAmount());
  }

  @Test
  void onlyCustomDefenseReducesRawDamage() {
    assertEquals(10, calculate(20, Map.of(), 200, 1).finalDamage());
  }

  @Test
  void invalidNumbersCannotPoisonDamage() {
    assertTrue(
        Double.isFinite(
            calculate(Double.NaN, Map.of("damage", Double.POSITIVE_INFINITY), 0, 1).finalDamage()));
  }

  @Test
  void defenseCurveHasDiminishingReturnsAndRejectsNonFiniteConfiguration() {
    assertEquals(1, DamageFormula.defenseMultiplier(0, 200));
    assertEquals(.5, DamageFormula.defenseMultiplier(200, 200));
    assertEquals(.25, DamageFormula.defenseMultiplier(600, 200));
    assertEquals(200.0 / 222, DamageFormula.defenseMultiplier(22, 200), .000001);
    assertEquals(.5, DamageFormula.defenseMultiplier(200, Double.NaN));
    assertEquals(1, DamageFormula.defenseMultiplier(Double.POSITIVE_INFINITY, 200));
  }
}
