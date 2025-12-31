package org.plugin.theMob.combat.pipeline;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class StatKeys {

    private static final Map<String, NamespacedKey> CACHE = new ConcurrentHashMap<>();

    private StatKeys() {}

    public static double getNumber(Plugin plugin, ItemMeta meta, String key) {
        if (plugin == null || meta == null || key == null) return 0.0;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String normKey = key.toLowerCase();

        NamespacedKey k1 = CACHE.computeIfAbsent(
                plugin.getName() + ":" + normKey,
                s -> new NamespacedKey(plugin, normKey)
        );

        Double d = pdc.get(k1, PersistentDataType.DOUBLE);
        if (d != null) return d;

        Integer i = pdc.get(k1, PersistentDataType.INTEGER);
        if (i != null) return i.doubleValue();

        NamespacedKey k2 = CACHE.computeIfAbsent(
                "themob:" + normKey,
                NamespacedKey::fromString
        );

        if (k2 != null) {
            Double d2 = pdc.get(k2, PersistentDataType.DOUBLE);
            if (d2 != null) return d2;

            Integer i2 = pdc.get(k2, PersistentDataType.INTEGER);
            if (i2 != null) return i2.doubleValue();
        }

        return 0.0;
    }
}
