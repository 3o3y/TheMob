package org.plugin.theMob.boss.phase;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.plugin.theMob.core.KeyRegistry;

public final class PhaseBuffEngine {
  private final KeyRegistry keys;
  private final NamespacedKey SNAPSHOT_KEY;

  public PhaseBuffEngine(KeyRegistry keys) {
    this.keys = keys;
    this.SNAPSHOT_KEY = new NamespacedKey(keys.DAMAGE.getNamespace(), "attribute_snapshot");
  }

  public void applyPhase(LivingEntity mob, ConfigurationSection phaseCfg) {
    if (mob != null && phaseCfg != null) {
      ConfigurationSection buffs = phaseCfg.getConfigurationSection("buffs");
      if (buffs != null && !buffs.getKeys(false).isEmpty()) {
        PersistentDataContainer pdc = mob.getPersistentDataContainer();
        if (!pdc.has(this.SNAPSHOT_KEY, PersistentDataType.TAG_CONTAINER)) {
          AttributeSnapshot snapshot = AttributeSnapshot.capture(mob);
          PersistentDataContainer tag = pdc.getAdapterContext().newPersistentDataContainer();
          snapshot.writeTo(tag);
          pdc.set(this.SNAPSHOT_KEY, PersistentDataType.TAG_CONTAINER, tag);
        }

        for (String key : buffs.getKeys(false)) {
          double delta = this.parse(buffs.get(key));
          if (delta != 0.0) {
            Attribute attr = this.mapAttribute(key);
            if (attr != null) {
              AttributeInstance inst = mob.getAttribute(attr);
              if (inst != null) {
                double target = AttributeSafety.clamp(attr, inst.getBaseValue() + delta);
                inst.setBaseValue(target);
              }
            } else {
              NamespacedKey statKey = this.keys.mobStat(key);
              if (statKey != null) {
                double current = (Double) pdc.getOrDefault(statKey, PersistentDataType.DOUBLE, 0.0);
                pdc.set(statKey, PersistentDataType.DOUBLE, current + delta);
              }
            }
          }
        }
      }
    }
  }

  public void rollbackPhase(LivingEntity mob) {
    if (mob != null) {
      PersistentDataContainer pdc = mob.getPersistentDataContainer();
      if (pdc.has(this.SNAPSHOT_KEY, PersistentDataType.TAG_CONTAINER)) {
        AttributeSnapshot snapshot =
            AttributeSnapshot.readFrom(
                (PersistentDataContainer)
                    pdc.get(this.SNAPSHOT_KEY, PersistentDataType.TAG_CONTAINER));
        snapshot.restore(mob);
        pdc.remove(this.SNAPSHOT_KEY);
      }
    }
  }

  private double parse(Object o) {
    try {
      return Double.parseDouble(o.toString().replace("+", "").trim());
    } catch (Exception var3) {
      return 0.0;
    }
  }

  private Attribute mapAttribute(String key) {
    String var2 = key.toLowerCase();

    return switch (var2) {
      case "damage", "attack-damage" ->
          org.plugin.theMob.compat.BukkitTypes.attribute("ATTACK_DAMAGE");
      case "attack-speed" -> org.plugin.theMob.compat.BukkitTypes.attribute("ATTACK_SPEED");
      case "movement-speed" -> org.plugin.theMob.compat.BukkitTypes.attribute("MOVEMENT_SPEED");
      case "armor" -> org.plugin.theMob.compat.BukkitTypes.attribute("ARMOR");
      case "armor-toughness" -> org.plugin.theMob.compat.BukkitTypes.attribute("ARMOR_TOUGHNESS");
      case "max-health" -> org.plugin.theMob.compat.BukkitTypes.attribute("MAX_HEALTH");
      case "knockback-resistance" ->
          org.plugin.theMob.compat.BukkitTypes.attribute("KNOCKBACK_RESISTANCE");
      case "follow-range" -> org.plugin.theMob.compat.BukkitTypes.attribute("FOLLOW_RANGE");
      default -> null;
    };
  }
}
