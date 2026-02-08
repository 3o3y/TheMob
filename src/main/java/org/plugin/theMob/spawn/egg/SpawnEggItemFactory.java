package org.plugin.theMob.spawn.egg;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.plugin.theMob.core.KeyRegistry;

import java.util.List;

public final class SpawnEggItemFactory {

    private final KeyRegistry keys;

    public SpawnEggItemFactory(KeyRegistry keys) {
        this.keys = keys;
    }
    public ItemStack createEgg(String eggKey, String displayName, List<String> lore) {

        ItemStack egg = new ItemStack(Material.EGG);
        ItemMeta meta = egg.getItemMeta();
        if (meta == null) return egg;

        meta.setDisplayName(displayName);
        meta.setLore(lore);

        meta.getPersistentDataContainer().set(
                keys.SPAWN_EGG_MOB_ID,
                PersistentDataType.STRING,
                eggKey.toLowerCase()
        );

        egg.setItemMeta(meta);
        return egg;
    }
}
