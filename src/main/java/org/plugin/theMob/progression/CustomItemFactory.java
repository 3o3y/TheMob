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
    if (meta == null) {
      return item;
    } else {
      meta.getPersistentDataContainer().set(this.keys.ITEM_ID, PersistentDataType.STRING, itemId);
      item.setItemMeta(meta);
      return item;
    }
  }

  public String getItemId(ItemStack item) {
    return item != null && item.hasItemMeta()
        ? (String)
            item.getItemMeta()
                .getPersistentDataContainer()
                .get(this.keys.ITEM_ID, PersistentDataType.STRING)
        : null;
  }
}
