package org.plugin.theMob.item;

import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.plugin.theMob.TheMob;

import java.util.HashMap;
import java.util.Map;

public final class ItemStatReader {

    private final TheMob plugin;
    public ItemStatReader(TheMob plugin) {
        this.plugin = plugin;
    }
    public Map<String, Double> read(ItemStack it) {
        if (it == null || it.getType().isAir()) return null;
        if (!it.hasItemMeta()) return null;
        PersistentDataContainer pdc = it.getItemMeta().getPersistentDataContainer();
        Map<String, Double> stats = new HashMap<>();
        for (var e : plugin.keys().ALL_STATS.entrySet()) {
            if (pdc.has(e.getValue(), PersistentDataType.DOUBLE)) {
                stats.put(e.getKey(), pdc.get(e.getValue(), PersistentDataType.DOUBLE));
            }
        }
        return stats.isEmpty() ? null : stats;
    }
}
