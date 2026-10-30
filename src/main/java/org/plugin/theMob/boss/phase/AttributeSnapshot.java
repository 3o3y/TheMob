package org.plugin.theMob.boss.phase;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class AttributeSnapshot {
  private final Map<String, Double> values = new HashMap<>();

  public static AttributeSnapshot capture(LivingEntity entity) {
    AttributeSnapshot snap = new AttributeSnapshot();

    for (Attribute attr : org.bukkit.Registry.ATTRIBUTE) {
      AttributeInstance inst = entity.getAttribute(attr);
      if (inst != null) {
        snap.values.put(
            org.plugin.theMob.compat.BukkitTypes.attributeKey(attr), inst.getBaseValue());
      }
    }

    return snap;
  }

  public void restore(LivingEntity entity) {
    for (Entry<String, Double> e : this.values.entrySet()) {
      try {
        Attribute attr = org.plugin.theMob.compat.BukkitTypes.attribute(e.getKey());
        if (attr == null) continue;
        AttributeInstance inst = entity.getAttribute(attr);
        if (inst != null) {
          inst.setBaseValue(e.getValue());
        }
      } catch (IllegalArgumentException var6) {
      }
    }
  }

  public void writeTo(PersistentDataContainer tag) {
    for (Entry<String, Double> e : this.values.entrySet()) {
      tag.set(
          new NamespacedKey("themob", "attr_" + e.getKey().toLowerCase()),
          PersistentDataType.DOUBLE,
          e.getValue());
    }
  }

  public static AttributeSnapshot readFrom(PersistentDataContainer tag) {
    AttributeSnapshot snap = new AttributeSnapshot();

    // Accept both historical generic.max_health keys and the current max_health keys.
    for (NamespacedKey key : tag.getKeys()) {
      if (!key.getNamespace().equals("themob") || !key.getKey().startsWith("attr_")) continue;
      Attribute attr = org.plugin.theMob.compat.BukkitTypes.attribute(key.getKey().substring(5));
      if (attr == null || !tag.has(key, PersistentDataType.DOUBLE)) continue;
      Double value = tag.get(key, PersistentDataType.DOUBLE);
      if (value != null && Double.isFinite(value)) {
        String canonical = org.plugin.theMob.compat.BukkitTypes.attributeKey(attr);
        if (key.getKey().equals("attr_" + canonical)) snap.values.put(canonical, value);
        else snap.values.putIfAbsent(canonical, value);
      }
    }

    return snap;
  }
}
