package org.plugin.theMob.item;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class ItemStatReader {
  private final Plugin plugin;

  public ItemStatReader(Plugin plugin) {
    this.plugin = plugin;
  }

  public Map<String, Double> read(ItemStack it) {
    if (it != null && !it.getType().isAir()) {
      ItemMeta meta = it.getItemMeta();
      if (meta == null) {
        return null;
      } else {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Map<String, Double> stats = new HashMap<>();

        for (NamespacedKey key : pdc.getKeys()) {
          if (key.getNamespace().equalsIgnoreCase(this.plugin.getName())
              || key.getNamespace().equalsIgnoreCase("themob")) {
            if (pdc.has(key, PersistentDataType.DOUBLE)) {
              Double v = (Double) pdc.get(key, PersistentDataType.DOUBLE);
              if (v != null && Double.isFinite(v)) {
                stats.put(key.getKey(), v);
              }
            } else if (pdc.has(key, PersistentDataType.INTEGER)) {
              Integer v = (Integer) pdc.get(key, PersistentDataType.INTEGER);
              if (v != null) {
                stats.put(key.getKey(), v.doubleValue());
              }
            }
          }
        }

        return stats.isEmpty() ? null : stats;
      }
    } else {
      return null;
    }
  }

  public Map<String, Double> readAll(Collection<ItemStack> items) {
    if (items != null && !items.isEmpty()) {
      Map<String, Double> merged = new HashMap<>();

      for (ItemStack it : items) {
        Map<String, Double> stats = this.read(it);
        if (stats != null) {
          for (Entry<String, Double> e : stats.entrySet()) {
            merged.merge(e.getKey(), e.getValue(), Double::sum);
          }
        }
      }

      return merged;
    } else {
      return Map.of();
    }
  }
}
