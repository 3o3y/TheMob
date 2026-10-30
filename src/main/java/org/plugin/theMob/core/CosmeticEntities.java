package org.plugin.theMob.core;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

/** Cosmetics owns these actors; they are neither radar targets nor TheMob bosses. */
public final class CosmeticEntities {
  private static final NamespacedKey VISUAL = new NamespacedKey("cosmetics", "visual");
  private static final NamespacedKey PET = new NamespacedKey("cosmetics", "pet");
  private static final NamespacedKey TEMPORARY = new NamespacedKey("themob", "temporary_visual");

  private CosmeticEntities() {}

  public static boolean isVisual(Entity entity) {
    return entity != null
        && entity.getPersistentDataContainer() != null
        && (entity.getPersistentDataContainer().has(VISUAL, PersistentDataType.BYTE)
            || entity.getPersistentDataContainer().has(TEMPORARY, PersistentDataType.BYTE));
  }

  public static boolean isPet(Entity entity) {
    return entity instanceof org.bukkit.entity.LivingEntity
        && entity.getPersistentDataContainer() != null
        && entity.getPersistentDataContainer().has(PET, PersistentDataType.BYTE);
  }
}
