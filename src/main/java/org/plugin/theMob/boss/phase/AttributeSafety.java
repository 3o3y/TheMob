package org.plugin.theMob.boss.phase;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.attribute.Attribute;

public final class AttributeSafety {
  private static final Map<Attribute, AttributeSafety.Limits> LIMITS = new HashMap<>();

  private AttributeSafety() {}

  public static double clamp(Attribute attr, double value) {
    if (attr == null) {
      return value;
    } else {
      AttributeSafety.Limits l = LIMITS.get(attr);
      return l == null ? value : Math.max(l.min, Math.min(l.max, value));
    }
  }

  static {
    LIMITS.put(
        org.plugin.theMob.compat.BukkitTypes.attribute("MOVEMENT_SPEED"),
        new AttributeSafety.Limits(0.05, 0.45));
    LIMITS.put(
        org.plugin.theMob.compat.BukkitTypes.attribute("FLYING_SPEED"),
        new AttributeSafety.Limits(0.05, 0.8));
    LIMITS.put(
        org.plugin.theMob.compat.BukkitTypes.attribute("ATTACK_DAMAGE"),
        new AttributeSafety.Limits(0.0, 2048.0));
    LIMITS.put(
        org.plugin.theMob.compat.BukkitTypes.attribute("ATTACK_SPEED"),
        new AttributeSafety.Limits(0.1, 1024.0));
    LIMITS.put(
        org.plugin.theMob.compat.BukkitTypes.attribute("ARMOR"),
        new AttributeSafety.Limits(0.0, 100.0));
    LIMITS.put(
        org.plugin.theMob.compat.BukkitTypes.attribute("ARMOR_TOUGHNESS"),
        new AttributeSafety.Limits(0.0, 50.0));
    LIMITS.put(
        org.plugin.theMob.compat.BukkitTypes.attribute("KNOCKBACK_RESISTANCE"),
        new AttributeSafety.Limits(0.0, 1.0));
    LIMITS.put(
        org.plugin.theMob.compat.BukkitTypes.attribute("MAX_HEALTH"),
        new AttributeSafety.Limits(1.0, 100000.0));
    LIMITS.put(
        org.plugin.theMob.compat.BukkitTypes.attribute("FOLLOW_RANGE"),
        new AttributeSafety.Limits(1.0, 128.0));
  }

  private record Limits(double min, double max) {}
}
