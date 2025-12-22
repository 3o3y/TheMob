package org.plugin.theMob.combat.pipeline;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class StatKeys {

    private StatKeys() {}
    public static double getNumber(Plugin plugin, ItemMeta meta, String key) {
        if (plugin == null || meta == null || key == null) return 0.0;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey k1 = new NamespacedKey(plugin, key);
        Double d1 = pdc.get(k1, PersistentDataType.DOUBLE);
        if (d1 != null) return d1;
        Integer i1 = pdc.get(k1, PersistentDataType.INTEGER);
        if (i1 != null) return i1.doubleValue();
        NamespacedKey k2 = new NamespacedKey("themob", key);
        Double d2 = pdc.get(k2, PersistentDataType.DOUBLE);
        if (d2 != null) return d2;
        Integer i2 = pdc.get(k2, PersistentDataType.INTEGER);
        if (i2 != null) return i2.doubleValue();
        return 0.0;
    }
}
