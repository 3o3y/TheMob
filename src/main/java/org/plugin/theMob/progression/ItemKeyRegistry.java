package org.plugin.theMob.progression;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

public final class ItemKeyRegistry {

    public final NamespacedKey ITEM_ID;

    public ItemKeyRegistry(Plugin plugin) {
        this.ITEM_ID = new NamespacedKey(plugin, "themob_item_id");
    }
}
