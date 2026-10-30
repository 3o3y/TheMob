package org.plugin.theMob.compat;

import java.util.*;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.potion.PotionEffectType;

/** Runtime names bridge Bukkit's enum/registry and naming changes without linking new fields. */
public final class BukkitTypes {
  private BukkitTypes() {}

  private static final Map<String, Optional<Attribute>> ATTRIBUTES =
      new java.util.concurrent.ConcurrentHashMap<>();
  private static final Map<String, Particle> PARTICLES =
      new java.util.concurrent.ConcurrentHashMap<>();
  private static final Map<String, Optional<Sound>> SOUNDS =
      new java.util.concurrent.ConcurrentHashMap<>();
  private static final Map<String, String> POTIONS =
      Map.of(
          "JUMP_BOOST",
          "JUMP",
          "RESISTANCE",
          "DAMAGE_RESISTANCE",
          "STRENGTH",
          "INCREASE_DAMAGE",
          "SLOWNESS",
          "SLOW",
          "HASTE",
          "FAST_DIGGING",
          "MINING_FATIGUE",
          "SLOW_DIGGING",
          "INSTANT_HEALTH",
          "HEAL",
          "INSTANT_DAMAGE",
          "HARM",
          "NAUSEA",
          "CONFUSION");

  private static <T> T field(Class<T> type, String... names) {
    for (String name : names)
      try {
        return type.cast(type.getField(name).get(null));
      } catch (ReflectiveOperationException ignored) {
      }
    return null;
  }

  public static Attribute attribute(String name) {
    name =
        name.toUpperCase(Locale.ROOT)
            .replace("MINECRAFT:", "")
            .replace("GENERIC.", "")
            .replace("GENERIC_", "")
            .replace("HORSE.", "")
            .replace("PLAYER.", "")
            .replace("ZOMBIE.", "");
    return ATTRIBUTES
        .computeIfAbsent(
            name,
            key ->
                Optional.ofNullable(
                    field(
                        Attribute.class,
                        key,
                        "GENERIC_" + key,
                        "HORSE_" + key,
                        "PLAYER_" + key,
                        "ZOMBIE_" + key)))
        .orElse(null);
  }

  public static AttributeInstance instance(LivingEntity entity, String name) {
    Attribute attr = attribute(name);
    return attr == null ? null : entity.getAttribute(attr);
  }

  public static PotionEffectType potion(String name) {
    return field(PotionEffectType.class, name, POTIONS.getOrDefault(name, name));
  }

  public static Particle particle(String name) {
    Particle known = PARTICLES.get(name);
    if (known != null) return known;
    String old =
        switch (name) {
          case "POOF" -> "EXPLOSION_NORMAL";
          case "SMOKE" -> "SMOKE_NORMAL";
          case "LARGE_SMOKE" -> "SMOKE_LARGE";
          case "DUST" -> "REDSTONE";
          default -> name;
        };
    Particle value = field(Particle.class, name, old);
    if (value == null) throw new IllegalArgumentException("Unknown particle: " + name);
    PARTICLES.put(name, value);
    return value;
  }

  public static ItemFlag flag(String name) {
    return field(
        ItemFlag.class,
        name,
        name.equals("HIDE_ADDITIONAL_TOOLTIP") ? "HIDE_POTION_EFFECTS" : name);
  }

  public static Sound sound(String name) {
    return SOUNDS
        .computeIfAbsent(
            name,
            key -> {
              for (Sound value : Registry.SOUNDS)
                if (((Keyed) value).getKey().getKey().replace('.', '_').equalsIgnoreCase(key))
                  return Optional.of(value);
              return Optional.empty();
            })
        .orElse(null);
  }

  public static String attributeKey(Attribute value) {
    return ((Keyed) value)
        .getKey()
        .getKey()
        .replace("generic.", "")
        .replace("player.", "")
        .replace("horse.", "")
        .replace("zombie.", "");
  }

  public static boolean hostile(Class<?> entityClass, String name) {
    try {
      return Class.forName("org.bukkit.entity.Enemy", false, EntityTypeLoader())
          .isAssignableFrom(entityClass);
    } catch (ClassNotFoundException older) {
      return org.bukkit.entity.Monster.class.isAssignableFrom(entityClass)
          || Set.of("SLIME", "MAGMA_CUBE", "GHAST", "PHANTOM", "HOGLIN", "ZOGLIN", "ENDER_DRAGON")
              .contains(name);
    }
  }

  private static ClassLoader EntityTypeLoader() {
    return org.bukkit.entity.EntityType.class.getClassLoader();
  }
}
