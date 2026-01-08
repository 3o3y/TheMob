package org.plugin.theMob.progression;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class CustomItemFactory {

    private final ItemKeyRegistry keys;

    public CustomItemFactory(ItemKeyRegistry keys) {
        this.keys = keys;
    }

    public ItemStack create(Material material, String itemId) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.getPersistentDataContainer().set(
                keys.ITEM_ID,
                PersistentDataType.STRING,
                itemId
        );

        item.setItemMeta(meta);
        return item;
    }

    public String getItemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta()
                .getPersistentDataContainer()
                .get(keys.ITEM_ID, PersistentDataType.STRING);
    }
}
