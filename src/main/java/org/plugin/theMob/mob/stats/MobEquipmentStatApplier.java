package org.plugin.theMob.mob.stats;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.plugin.theMob.core.KeyRegistry;
import org.plugin.theMob.item.ItemStatReader;

public final class MobEquipmentStatApplier {
  private final ItemStatReader statReader;
  private final KeyRegistry keys;

  public MobEquipmentStatApplier(ItemStatReader statReader, KeyRegistry keys) {
    this.statReader = statReader;
    this.keys = keys;
  }

  public void apply(LivingEntity mob, List<ItemStack> equipment) {
    if (mob != null && equipment != null && !equipment.isEmpty()) {
      Map<String, Double> aggregated = new HashMap<>();

      for (ItemStack it : equipment) {
        if (it != null && !it.getType().isAir()) {
          Map<String, Double> stats = this.statReader.read(it);
          if (stats != null && !stats.isEmpty()) {
            stats.forEach((stat, value) -> aggregated.merge(stat, value, Double::sum));
          }
        }
      }

      if (!aggregated.isEmpty()) {
        aggregated.forEach(
            (stat, value) -> {
              NamespacedKey key = this.keys.mobStat(stat);
              if (key != null) {
                mob.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, value);
              }
            });
        this.applyAttribute(
            mob, org.plugin.theMob.compat.BukkitTypes.attribute("ARMOR"), aggregated.get("armor"));
        this.applyAttribute(
            mob,
            org.plugin.theMob.compat.BukkitTypes.attribute("MAX_HEALTH"),
            aggregated.get("health"));
        this.applyAttribute(
            mob,
            org.plugin.theMob.compat.BukkitTypes.attribute("KNOCKBACK_RESISTANCE"),
            aggregated.get("knockback_resistance"));
        if (aggregated.containsKey("movement_speed")) {
          AttributeInstance inst =
              org.plugin.theMob.compat.BukkitTypes.instance(mob, "MOVEMENT_SPEED");
          if (inst != null) {
            double base = inst.getDefaultValue();
            double bonus = aggregated.get("movement_speed");
            inst.setBaseValue(Math.min(0.7, base + bonus));
          }
        }
      }
    }
  }

  private void resetAttribute(LivingEntity mob, Attribute attr) {
    AttributeInstance inst = mob.getAttribute(attr);
    if (inst != null) {
      inst.setBaseValue(inst.getDefaultValue());
    }
  }

  private void applyAttribute(LivingEntity mob, Attribute attr, Double value) {
    if (value != null && value != 0.0) {
      AttributeInstance inst = mob.getAttribute(attr);
      if (inst != null) {
        inst.setBaseValue(inst.getBaseValue() + value);
        if (attr == org.plugin.theMob.compat.BukkitTypes.attribute("MAX_HEALTH")) {
          mob.setHealth(Math.min(inst.getBaseValue(), mob.getHealth() + value));
        }
      }
    }
  }
}
