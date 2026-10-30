package org.plugin.theMob.item;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

public final class CustomEnchantSystem {
  private final Plugin plugin;
  private static final ThreadLocal<Boolean> SECONDARY = ThreadLocal.withInitial(() -> false);

  public static boolean isSecondaryHit() {
    return SECONDARY.get();
  }

  private final Random rnd = new Random();

  public CustomEnchantSystem(Plugin plugin) {
    this.plugin = plugin;
  }

  public Map<String, Double> collect(ItemMeta meta) {
    Map<String, Double> stats = new HashMap<>();
    if (meta == null) {
      return stats;
    } else {
      PersistentDataContainer pdc = meta.getPersistentDataContainer();

      for (NamespacedKey key : pdc.getKeys()) {
        if (!key.getNamespace().equalsIgnoreCase(this.plugin.getName())) continue;
        if (pdc.has(key, PersistentDataType.DOUBLE)) {
          Double v = (Double) pdc.get(key, PersistentDataType.DOUBLE);
          if (v != null) {
            stats.put(key.getKey(), v);
          }
        } else if (pdc.has(key, PersistentDataType.INTEGER)) {
          Integer v = (Integer) pdc.get(key, PersistentDataType.INTEGER);
          if (v != null) {
            stats.put(key.getKey(), v.doubleValue());
          }
        }
      }

      return stats;
    }
  }

  public void trigger(
      Player p, LivingEntity target, Map<String, Double> stats, double finalDamage) {
    triggerEnabled(
        p,
        target,
        plugin instanceof org.plugin.theMob.TheMob owner
            ? owner.skills().itemEffects(stats)
            : stats,
        finalDamage);
  }

  private void triggerEnabled(
      Player p, LivingEntity target, Map<String, Double> stats, double finalDamage) {
    if (p != null && target != null && stats != null && !stats.isEmpty()) {
      if (target.isValid() && !target.isDead()) {
        // Lifesteal is applied exactly once by CombatListener after damage resolution.

        this.tryChance(
            stats,
            "poison_chance",
            () -> {
              int sec =
                  (int)
                      org.plugin.theMob.compat.Numbers.clamp(
                          this.get(stats, "poison_seconds", 3.0), 1, 30);
              target.addPotionEffect(
                  new PotionEffect(
                      org.plugin.theMob.compat.BukkitTypes.potion("POISON"),
                      sec * 20,
                      0,
                      true,
                      true));
            });
        this.tryChance(
            stats,
            "ignite_chance",
            () -> {
              int sec =
                  (int)
                      org.plugin.theMob.compat.Numbers.clamp(
                          this.get(stats, "ignite_seconds", 3.0), 1, 30);
              target.setFireTicks(Math.max(target.getFireTicks(), sec * 20));
            });
        this.tryChance(
            stats,
            "slow_chance",
            () -> {
              int sec =
                  (int)
                      org.plugin.theMob.compat.Numbers.clamp(
                          this.get(stats, "slow_seconds", 2.0), 1, 30);
              int amp =
                  (int)
                      org.plugin.theMob.compat.Numbers.clamp(
                          this.get(stats, "slow_amplifier", 0.0), 0, 4);
              target.addPotionEffect(
                  new PotionEffect(
                      org.plugin.theMob.compat.BukkitTypes.potion("SLOWNESS"),
                      sec * 20,
                      amp,
                      true,
                      true));
            });
        double threshold = this.get(stats, "execute_threshold");
        if (threshold > 0.0) {
          double max = Math.max(1.0, target.getMaxHealth());
          if (target.getHealth() / max <= this.clamp01(threshold)) {
            double bonus = this.get(stats, "execute_damage");
            if (bonus > 0.0) {
              try {
                SECONDARY.set(true);
                target.damage(bonus, p);
              } finally {
                SECONDARY.remove();
              }
            }
          }
        }

        double kb = this.get(stats, "knockback");
        if (kb > 0.0) {
          Vector dir = target.getLocation().toVector().subtract(p.getLocation().toVector());
          if (dir.lengthSquared() > 0.01) {
            dir.normalize().multiply(Math.min(2.5, kb));
            dir.setY(Math.min(0.6, dir.getY() + 0.2));
            target.setVelocity(target.getVelocity().add(dir));
          }
        }
      }
    }
  }

  private void tryChance(Map<String, Double> stats, String key, Runnable run) {
    double chance = this.get(stats, key);
    if (!(chance <= 0.0)) {
      if (this.rnd.nextDouble() <= this.clamp01(chance)) {
        run.run();
      }
    }
  }

  private double get(Map<String, Double> stats, String key) {
    return this.get(stats, key, 0.0);
  }

  private double get(Map<String, Double> stats, String key, double def) {
    if (stats == null) {
      return def;
    } else {
      Double v = stats.get(key);
      if (v == null) {
        v = stats.get(key.replace("-", "_"));
      }

      return v == null || !Double.isFinite(v) ? def : v;
    }
  }

  private double clamp01(double v) {
    return Math.max(0.0, Math.min(1.0, v));
  }

  private void heal(Player p, double amount) {
    if (!(amount <= 0.0)) {
      p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + amount));
    }
  }
}
