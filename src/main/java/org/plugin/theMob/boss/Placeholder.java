package org.plugin.theMob.boss;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.plugin.theMob.core.KeyRegistry;

public final class Placeholder {
  private static KeyRegistry keys;
  private static boolean PAPI_PRESENT;
  private static Method PAPI_SET_PLACEHOLDERS;

  private Placeholder() {}

  public static void init(KeyRegistry registry) {
    keys = registry;

    try {
      if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
        Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
        PAPI_SET_PLACEHOLDERS = papi.getMethod("setPlaceholders", Player.class, String.class);
        PAPI_PRESENT = true;
      }
    } catch (Throwable var2) {
      PAPI_PRESENT = false;
      PAPI_SET_PLACEHOLDERS = null;
    }
  }

  public static String resolve(String input, LivingEntity mob, BossPhase phase, Player viewer) {
    if (input == null || input.isEmpty()) {
      return input;
    } else if (keys == null) {
      Bukkit.getLogger().warning("[TheMob] Placeholder used before init()");
      return input;
    } else {
      Map<String, Supplier<String>> values = buildValues(mob, phase, viewer);
      String out = input;

      for (Entry<String, Supplier<String>> e : values.entrySet()) {
        out = out.replace("{" + e.getKey() + "}", safe(e.getValue()));
      }

      return applyPlaceholderAPI(out, viewer);
    }
  }

  private static Map<String, Supplier<String>> buildValues(
      LivingEntity mob, BossPhase phase, Player viewer) {
    Map<String, Supplier<String>> map = new LinkedHashMap<>(32);
    map.put("mob_key", () -> mob != null ? spawnEggKey(mob) : "");
    map.put("mob_name", () -> mob != null ? baseName(mob) : "");
    map.put("mob_type", () -> mob != null ? mob.getType().name().toLowerCase(Locale.ROOT) : "");
    map.put("mob_uuid", () -> mob != null ? mob.getUniqueId().toString() : "");
    map.put("health", () -> mob != null ? String.valueOf((int) Math.ceil(safeHealth(mob))) : "0");
    map.put(
        "max_health",
        () -> {
          if (mob == null) {
            return "0";
          } else {
            AttributeInstance a = org.plugin.theMob.compat.BukkitTypes.instance(mob, "MAX_HEALTH");
            return a != null ? String.valueOf((int) a.getValue()) : "0";
          }
        });
    map.put("phase_id", () -> phase != null ? phase.id() : "none");
    map.put("phase_title", () -> phase != null ? phase.title() : "");
    map.put("player", () -> viewer != null ? viewer.getName() : "");
    map.put("player_uuid", () -> viewer != null ? viewer.getUniqueId().toString() : "");
    map.put("world", () -> mob != null ? safeWorld(mob) : "");
    map.put("x", () -> mob != null ? String.valueOf(mob.getLocation().getBlockX()) : "0");
    map.put("y", () -> mob != null ? String.valueOf(mob.getLocation().getBlockY()) : "0");
    map.put("z", () -> mob != null ? String.valueOf(mob.getLocation().getBlockZ()) : "0");
    return map;
  }

  private static String baseName(LivingEntity mob) {
    String name =
        (String) mob.getPersistentDataContainer().get(keys.BASE_NAME, PersistentDataType.STRING);
    if (name != null && !name.isBlank()) {
      return name;
    } else {
      String raw = mob.getCustomName();
      if (raw != null && !raw.isBlank()) {
        int heart = raw.indexOf(10084);
        return heart > 0 ? raw.substring(0, heart).trim() : raw;
      } else {
        return mob.getType().name().toLowerCase(Locale.ROOT);
      }
    }
  }

  private static String applyPlaceholderAPI(String input, Player player) {
    if (PAPI_PRESENT && PAPI_SET_PLACEHOLDERS != null && player != null) {
      try {
        return (String) PAPI_SET_PLACEHOLDERS.invoke(null, player, input);
      } catch (Throwable var3) {
        return input;
      }
    } else {
      return input;
    }
  }

  private static String safe(Supplier<String> s) {
    try {
      return Optional.ofNullable(s.get()).orElse("");
    } catch (Throwable var2) {
      return "";
    }
  }

  private static double safeHealth(LivingEntity mob) {
    try {
      return Math.max(0.0, mob.getHealth());
    } catch (Throwable var2) {
      return 0.0;
    }
  }

  private static String safeWorld(LivingEntity mob) {
    try {
      Location l = mob.getLocation();
      return l.getWorld() != null ? l.getWorld().getName() : "unknown";
    } catch (Throwable var2) {
      return "unknown";
    }
  }

  private static String spawnEggKey(LivingEntity mob) {
    try {
      String key =
          (String)
              mob.getPersistentDataContainer()
                  .get(keys.SPAWN_EGG_MOB_ID, PersistentDataType.STRING);
      return key != null && !key.isBlank() ? key : baseName(mob);
    } catch (Throwable var2) {
      return "";
    }
  }
}
