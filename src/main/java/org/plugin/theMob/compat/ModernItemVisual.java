package org.plugin.theMob.compat;

import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.plugin.theMob.core.KeyRegistry;

/** Loaded exclusively after Displays.available(). */
public final class ModernItemVisual {
  private ModernItemVisual() {}

  public static Entity spawn(LivingEntity mob, Material material, KeyRegistry keys) {
    ItemDisplay actor =
        (ItemDisplay) mob.getWorld().spawnEntity(mob.getLocation(), EntityType.ITEM_DISPLAY);
    actor.setItemStack(new ItemStack(material));
    actor.getPersistentDataContainer().set(keys.VISUAL_HEAD, PersistentDataType.INTEGER, 1);
    return actor;
  }
}
