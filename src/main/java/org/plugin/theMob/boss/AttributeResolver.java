package org.plugin.theMob.boss;

import java.util.Locale;
import java.util.Map;
import org.bukkit.attribute.Attribute;

public final class AttributeResolver {
  private static final Map<String, Attribute> MAP =
      Map.ofEntries(
          Map.entry(
              "movement-speed", org.plugin.theMob.compat.BukkitTypes.attribute("MOVEMENT_SPEED")),
          Map.entry("speed", org.plugin.theMob.compat.BukkitTypes.attribute("MOVEMENT_SPEED")),
          Map.entry("damage", org.plugin.theMob.compat.BukkitTypes.attribute("ATTACK_DAMAGE")),
          Map.entry(
              "attack-damage", org.plugin.theMob.compat.BukkitTypes.attribute("ATTACK_DAMAGE")),
          Map.entry("armor", org.plugin.theMob.compat.BukkitTypes.attribute("ARMOR")),
          Map.entry(
              "armor-toughness", org.plugin.theMob.compat.BukkitTypes.attribute("ARMOR_TOUGHNESS")),
          Map.entry(
              "knockback-resistance",
              org.plugin.theMob.compat.BukkitTypes.attribute("KNOCKBACK_RESISTANCE")),
          Map.entry("max-health", org.plugin.theMob.compat.BukkitTypes.attribute("MAX_HEALTH")),
          Map.entry("health", org.plugin.theMob.compat.BukkitTypes.attribute("MAX_HEALTH")),
          Map.entry("follow-range", org.plugin.theMob.compat.BukkitTypes.attribute("FOLLOW_RANGE")),
          Map.entry("attack-speed", org.plugin.theMob.compat.BukkitTypes.attribute("ATTACK_SPEED")),
          Map.entry("luck", org.plugin.theMob.compat.BukkitTypes.attribute("LUCK")));

  private AttributeResolver() {}

  public static Attribute resolve(String yamlKey) {
    return yamlKey != null && !yamlKey.isBlank() ? MAP.get(yamlKey.toLowerCase(Locale.ROOT)) : null;
  }
}
