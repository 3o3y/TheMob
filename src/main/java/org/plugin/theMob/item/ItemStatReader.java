package org.plugin.theMob.item;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class ItemStatReader {

    private final Plugin plugin;

    public ItemStatReader(Plugin plugin) {
        this.plugin = plugin;
    }

    // ============================================
    // READ SINGLE ITEM
    // ============================================
    public Map<String, Double> read(ItemStack it) {
        if (it == null || it.getType().isAir()) return null;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return null;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Map<String, Double> stats = new HashMap<>();

        for (NamespacedKey key : pdc.getKeys()) {

            if (!key.getNamespace().equalsIgnoreCase(plugin.getName())
                    && !key.getNamespace().equalsIgnoreCase("themob")) {
                continue;
            }

            if (pdc.has(key, PersistentDataType.DOUBLE)) {
                Double v = pdc.get(key, PersistentDataType.DOUBLE);
                if (v != null) stats.put(key.getKey(), v);
            }
            else if (pdc.has(key, PersistentDataType.INTEGER)) {
                Integer v = pdc.get(key, PersistentDataType.INTEGER);
                if (v != null) stats.put(key.getKey(), v.doubleValue());
            }
        }

        return stats.isEmpty() ? null : stats;
    }

    // ============================================
    // READ & MERGE MULTIPLE ITEMS  🔥
    // ============================================
    public Map<String, Double> readAll(Collection<ItemStack> items) {
        if (items == null || items.isEmpty()) return Map.of();

        Map<String, Double> merged = new HashMap<>();

        for (ItemStack it : items) {
            Map<String, Double> stats = read(it);
            if (stats == null) continue;

            for (Map.Entry<String, Double> e : stats.entrySet()) {
                merged.merge(e.getKey(), e.getValue(), Double::sum);
            }
        }

        return merged;
    }
}
