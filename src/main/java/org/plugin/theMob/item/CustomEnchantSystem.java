package org.plugin.theMob.item;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;

public final class CustomEnchantSystem {

    private final Plugin plugin;
    public CustomEnchantSystem(Plugin plugin) {
        this.plugin = plugin;
    }
// COLLECT ALL NUMERIC STATS (INT + DOUBLE SAFE)
    public Map<String, Double> collect(ItemMeta meta) {
        Map<String, Double> stats = new HashMap<>();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        for (NamespacedKey key : pdc.getKeys()) {
            if (pdc.has(key, PersistentDataType.DOUBLE)) {
                Double v = pdc.get(key, PersistentDataType.DOUBLE);
                if (v != null) stats.put(key.getKey(), v);
                continue;
            }
            if (pdc.has(key, PersistentDataType.INTEGER)) {
                Integer v = pdc.get(key, PersistentDataType.INTEGER);
                if (v != null) stats.put(key.getKey(), v.doubleValue());
            }
        }
        return stats;
    }
// TRIGGER (OPTIONAL)
    public void trigger(Player p,
                        LivingEntity target,
                        Map<String, Double> stats,
                        double damage) {
    }
}
