package org.plugin.theMob.boss.minion;

import java.util.EnumSet;
import java.util.Set;
import org.bukkit.entity.EntityType;

public final class MinionRules {
  public static final int MAX_MINIONS_GLOBAL = 200;
  private static final Set<EntityType> DISALLOWED =
      EnumSet.of(
          EntityType.ENDER_DRAGON, EntityType.WITHER, EntityType.ARMOR_STAND, EntityType.PLAYER);

  private MinionRules() {}

  public static boolean isAllowed(EntityType type) {
    if (type == null) {
      return false;
    } else if (!type.isAlive()) {
      return false;
    } else {
      return !type.isSpawnable() ? false : !DISALLOWED.contains(type);
    }
  }
}
