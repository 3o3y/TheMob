package org.plugin.theMob.spawn.egg;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.plugin.theMob.core.KeyRegistry;

public final class SpawnEggItemFactory {
  private final KeyRegistry keys;

  public SpawnEggItemFactory(KeyRegistry keys) {
    this.keys = keys;
  }

  public ItemStack createEgg(String eggKey, String displayName, List<String> lore) {
    ItemStack egg = new ItemStack(Material.EGG);
    ItemMeta meta = egg.getItemMeta();
    if (meta == null) {
      return egg;
    } else {
      meta.setDisplayName(displayName);
      meta.setLore(lore);
      meta.getPersistentDataContainer()
          .set(this.keys.SPAWN_EGG_MOB_ID, PersistentDataType.STRING, eggKey.toLowerCase());
      egg.setItemMeta(meta);
      return egg;
    }
  }
}
