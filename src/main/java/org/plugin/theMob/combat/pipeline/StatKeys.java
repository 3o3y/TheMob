package org.plugin.theMob.combat.pipeline;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class StatKeys {
  private static final Map<String, NamespacedKey> CACHE = new ConcurrentHashMap<>();

  private StatKeys() {}

  public static double getNumber(Plugin plugin, ItemMeta meta, String key) {
    if (plugin != null && meta != null && key != null) {
      PersistentDataContainer pdc = meta.getPersistentDataContainer();
      String norm = key.toLowerCase(Locale.ROOT);
      NamespacedKey pluginKey =
          CACHE.computeIfAbsent(
              plugin.getName().toLowerCase(Locale.ROOT) + ":" + norm,
              s -> new NamespacedKey(plugin, norm));
      Double d = (Double) pdc.get(pluginKey, PersistentDataType.DOUBLE);
      if (d != null) {
        return d;
      } else {
        Integer i = (Integer) pdc.get(pluginKey, PersistentDataType.INTEGER);
        if (i != null) {
          return i.doubleValue();
        } else {
          NamespacedKey legacyKey =
              CACHE.computeIfAbsent("themob:" + norm, NamespacedKey::fromString);
          if (legacyKey != null) {
            Double d2 = (Double) pdc.get(legacyKey, PersistentDataType.DOUBLE);
            if (d2 != null) {
              return d2;
            }

            Integer i2 = (Integer) pdc.get(legacyKey, PersistentDataType.INTEGER);
            if (i2 != null) {
              return i2.doubleValue();
            }
          }

          return 0.0;
        }
      }
    } else {
      return 0.0;
    }
  }
}
