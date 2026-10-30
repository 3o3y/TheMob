package org.plugin.theMob.mob.stats;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.plugin.theMob.core.KeyRegistry;

public final class BaseMobStatApplier {
  private static final double MIN_MOVE_SPEED = 0.05;
  private static final double MAX_MOVE_SPEED = 0.45;
  private final KeyRegistry keys;

  public BaseMobStatApplier(KeyRegistry keys) {
    this.keys = keys;
  }

  public void apply(LivingEntity mob, ConfigurationSection stats) {
    if (mob != null && stats != null) {
      if (stats.contains("health.max")) {
        double max = stats.getDouble("health.max");
        AttributeInstance hp = org.plugin.theMob.compat.BukkitTypes.instance(mob, "MAX_HEALTH");
        if (hp != null) {
          hp.setBaseValue(Math.max(1.0, max));
          double current =
              stats.contains("health.current") ? stats.getDouble("health.current") : max;
          mob.setHealth(Math.min(hp.getValue(), Math.max(0.0, current)));
        }
      }

      if (stats.contains("attack-damage")) {
        this.setAttrOverride(
            mob,
            org.plugin.theMob.compat.BukkitTypes.attribute("ATTACK_DAMAGE"),
            stats.getDouble("attack-damage"));
      }

      if (stats.contains("attack.damage")) {
        this.setAttrOverride(
            mob,
            org.plugin.theMob.compat.BukkitTypes.attribute("ATTACK_DAMAGE"),
            stats.getDouble("attack.damage"));
      }

      if (stats.contains("attack.speed")) {
        this.setAttrOverride(
            mob,
            org.plugin.theMob.compat.BukkitTypes.attribute("ATTACK_SPEED"),
            stats.getDouble("attack.speed"));
      }

      if (stats.contains("defense.armor")) {
        this.setAttrOverride(
            mob,
            org.plugin.theMob.compat.BukkitTypes.attribute("ARMOR"),
            stats.getDouble("defense.armor"));
      }

      if (stats.contains("defense.armor-toughness")) {
        this.setAttrOverride(
            mob,
            org.plugin.theMob.compat.BukkitTypes.attribute("ARMOR_TOUGHNESS"),
            stats.getDouble("defense.armor-toughness"));
      }

      if (stats.contains("defense.knockback-resistance")) {
        double v = stats.getDouble("defense.knockback-resistance");
        v = Math.max(0.0, Math.min(1.0, v));
        this.setAttrOverride(
            mob, org.plugin.theMob.compat.BukkitTypes.attribute("KNOCKBACK_RESISTANCE"), v);
      }

      if (stats.contains("movement-speed")) {
        AttributeInstance ms = org.plugin.theMob.compat.BukkitTypes.instance(mob, "MOVEMENT_SPEED");
        if (ms != null) {
          double value = stats.getDouble("movement-speed");
          value = Math.max(0.05, Math.min(0.45, value));
          ms.setBaseValue(value);
        }
      }

      if (stats.contains("follow-range")) {
        this.setAttrOverride(
            mob,
            org.plugin.theMob.compat.BukkitTypes.attribute("FOLLOW_RANGE"),
            stats.getDouble("follow-range"));
      }

      if (stats.contains("scale")) {
        AttributeInstance scale = org.plugin.theMob.compat.BukkitTypes.instance(mob, "SCALE");
        if (scale != null) {
          double v = stats.getDouble("scale");
          v = Math.max(0.25, Math.min(5.0, v));
          scale.setBaseValue(v);
        }
      }

      stats
          .getValues(true)
          .forEach(
              (k, vx) -> {
                if (vx instanceof Number n) {
                  NamespacedKey key = this.keys.mobStat(k);
                  if (key != null) {
                    mob.getPersistentDataContainer()
                        .set(key, PersistentDataType.DOUBLE, n.doubleValue());
                  }
                }
              });
    }
  }

  private void setAttrOverride(LivingEntity mob, Attribute attr, double value) {
    AttributeInstance inst = mob.getAttribute(attr);
    if (inst != null) {
      inst.setBaseValue(value);
    }
  }
}
