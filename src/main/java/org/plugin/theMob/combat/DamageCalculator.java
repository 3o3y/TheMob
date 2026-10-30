package org.plugin.theMob.combat;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.plugin.theMob.player.stats.PlayerStatCache;

public final class DamageCalculator {
  private final MobMultiplierService multipliers;
  private final PlayerStatCache cache;

  public DamageCalculator(MobMultiplierService multipliers) {
    this(multipliers, null);
  }

  public DamageCalculator(MobMultiplierService multipliers, PlayerStatCache cache) {
    this.multipliers = multipliers;
    this.cache = cache;
  }

  public DamageResult calculate(
      Player attacker,
      LivingEntity target,
      double vanillaDamage,
      Map<String, Double> stats,
      ConfigurationSection config) {
    if (target == null || stats == null) return DamageResult.empty(vanillaDamage);
    double defense = 0;
    if (target instanceof Player player && cache != null)
      defense = cache.get(player).getOrDefault("defense", 0.0);
    else
      defense =
          target
              .getPersistentDataContainer()
              .getOrDefault(new NamespacedKey("themob", "defense"), PersistentDataType.DOUBLE, 0.0);
    double multiplier = multipliers == null ? 1 : multipliers.multiplierFor(target, config, true);
    double receive =
        target
            .getPersistentDataContainer()
            .getOrDefault(
                new NamespacedKey("themob", "receive_damage_multiplier"),
                PersistentDataType.DOUBLE,
                1.0);
    return DamageFormula.calculate(
        vanillaDamage,
        stats,
        multiplier * receive,
        defense,
        config == null ? 200 : config.getDouble("defense.k", 200),
        config == null ? 10 : config.getDouble("crit.max-multiplier", 10),
        ThreadLocalRandom.current().nextDouble());
  }
}
