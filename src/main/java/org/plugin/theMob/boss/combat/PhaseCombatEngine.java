package org.plugin.theMob.boss.combat;

import java.util.*;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.plugin.theMob.core.KeyRegistry;

public final class PhaseCombatEngine {
  private final KeyRegistry keys;
  private final Map<UUID, Map<NamespacedKey, Double>> previous = new HashMap<>();

  public PhaseCombatEngine(KeyRegistry keys) {
    this.keys = keys;
  }

  public void apply(LivingEntity mob, ConfigurationSection phase) {
    if (mob == null || phase == null) return;
    var combat = phase.getConfigurationSection("combat");
    if (combat == null) return;
    rollback(mob);
    var before = new HashMap<NamespacedKey, Double>();
    var pdc = mob.getPersistentDataContainer();
    for (String raw : combat.getKeys(false)) {
      var key = keys.mobStat(raw);
      if (key == null) continue;
      try {
        double value = Double.parseDouble(String.valueOf(combat.get(raw)).replace("+", ""));
        if (!Double.isFinite(value)) continue;
        before.put(key, pdc.get(key, PersistentDataType.DOUBLE));
        pdc.set(key, PersistentDataType.DOUBLE, value);
      } catch (NumberFormatException ignored) {
      }
    }
    previous.put(mob.getUniqueId(), before);
  }

  public void rollback(LivingEntity mob) {
    if (mob == null) return;
    var before = previous.remove(mob.getUniqueId());
    if (before == null) return;
    var pdc = mob.getPersistentDataContainer();
    before.forEach(
        (key, value) -> {
          if (value == null) pdc.remove(key);
          else pdc.set(key, PersistentDataType.DOUBLE, value);
        });
  }
}
