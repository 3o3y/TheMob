package org.plugin.theMob.content;

import java.io.File;
import java.util.*;
import org.bukkit.configuration.file.*;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The exact target API determines the species list; generated IDs never replace user definitions.
 */
public final class MobCatalog {
  private MobCatalog() {}

  public static List<EntityType> species() {
    return Arrays.stream(EntityType.values())
        .filter(
            type ->
                type.isAlive()
                    && type.isSpawnable()
                    && type.getEntityClass() != null
                    && Mob.class.isAssignableFrom(type.getEntityClass()))
        .sorted(Comparator.comparing(Enum::name))
        .toList();
  }

  public static FileConfiguration settings(JavaPlugin plugin) {
    File file = new File(plugin.getDataFolder(), "catalog.yml");
    if (!file.exists()) plugin.saveResource("catalog.yml", false);
    return YamlConfiguration.loadConfiguration(file);
  }

  public static Map<String, FileConfiguration> generate(JavaPlugin plugin) {
    var settings = settings(plugin);
    if (!settings.getBoolean("enabled", true)) return Map.of();
    var result = new LinkedHashMap<String, FileConfiguration>();
    for (EntityType type : species())
      for (int level = 1; level <= 5; level++) {
        String id = "tm_" + type.name().toLowerCase(Locale.ROOT) + "_lvl_" + level;
        var cfg = definition(type, id, level, settings);
        if (type == EntityType.WITHER || type == EntityType.ENDER_DRAGON)
          phases(cfg, "vanilla", false);
        result.put(id, cfg);
      }
    if (settings.getBoolean("bosses.enabled", true)) {
      Map<String, EntityType> bosses =
          Map.of(
              "warlord",
              EntityType.ZOMBIE,
              "marksman",
              EntityType.SKELETON,
              "broodmother",
              EntityType.SPIDER,
              "guardian",
              EntityType.IRON_GOLEM);
      for (var entry : bosses.entrySet())
        for (int level = 1; level <= 5; level++) {
          String id = "tm_boss_" + entry.getKey() + "_lvl_" + level;
          var cfg = definition(entry.getValue(), id, level, settings);
          cfg.set("name", "&6" + title(entry.getKey()) + " &e[Lv. " + level + "]");
          cfg.set(
              "stats.health.max",
              (100 + level * 45) * number(settings, "bosses.health-multiplier", 1, .1, 20));
          cfg.set("stats.attack-damage", 4 + level);
          cfg.set("xp", 15 * level);
          cfg.set("boss-cooldown-seconds", 120);
          String behavior =
              switch (entry.getKey()) {
                case "marksman" -> "ranged";
                case "guardian" -> "guardian";
                default -> "bruiser";
              };
          phases(cfg, behavior, entry.getKey().equals("broodmother"));
          result.put(id, cfg);
        }
    }
    return result;
  }

  private static YamlConfiguration definition(
      EntityType type, String id, int level, FileConfiguration settings) {
    var cfg = new YamlConfiguration();
    cfg.set("mob-id", id);
    cfg.set("base-type", type.name());
    cfg.set("name", "&f" + title(type.name()) + " &7[Lv. " + level + "]");
    cfg.set("level", level);
    cfg.set("catalog-generated", true);
    cfg.set("vanilla-drops", true);
    double multiplier =
        number(settings, "levels." + level + ".health", 1 + .35 * (level - 1), .1, 20);
    cfg.set(
        "stats.health.max",
        org.plugin.theMob.compat.Numbers.clamp(baseHealth(type) * multiplier, 1, 100_000));
    boolean hostile =
        org.plugin.theMob.compat.BukkitTypes.hostile(type.getEntityClass(), type.name());
    double damage = number(settings, "levels." + level + ".damage", 1 + .2 * (level - 1), .1, 10);
    if (hostile) cfg.set("stats.attack-damage", 3 * damage);
    cfg.set("projectile-damage-multiplier", damage);
    cfg.set("xp", (hostile ? 3 : 1) * level);
    cfg.set("drops", List.of());
    return cfg;
  }

  private static void phases(YamlConfiguration cfg, String behavior, boolean summons) {
    cfg.set("arena.radius-chunks", 3);
    int level = cfg.getInt("level", 1);
    String[] names = {"guard", "pressure", "rage"};
    String[] ranges = {"100-65", "65-30", "30-0"};
    for (int phase = 0; phase < names.length; phase++) {
      String path = "phases." + names[phase] + ".";
      cfg.set(path + "hp-range", ranges[phase]);
      cfg.set(path + "title", "&6" + title(names[phase]));
      cfg.set(
          path + "behavior.mode",
          phase == 2 && behavior.equals("bruiser") ? "berserker" : behavior);
      cfg.set(path + "behavior.range", 24);
      cfg.set(path + "behavior.leash-radius", 48);
      cfg.set(path + "behavior.attack-damage", 2 + level * .6);
      cfg.set(path + "behavior.cooldown-ticks", Math.max(60, 160 - level * 10 - phase * 15));
      cfg.set(path + "behavior.windup-ticks", 20);
      if (summons && phase > 0) {
        String summon = path + "actions.summon-minions.";
        cfg.set(summon + "enabled", true);
        cfg.set(summon + "type", "CAVE_SPIDER");
        cfg.set(summon + "amount", Math.min(3, level));
        cfg.set(summon + "max-alive", Math.min(6, level + 1));
        cfg.set(summon + "cooldown", 20);
        cfg.set(summon + "repeat", true);
        cfg.set(summon + "lifetime", 30);
        cfg.set(summon + "no-drops", true);
        cfg.set(summon + "radius", 3);
      }
    }
  }

  public static double baseHealth(EntityType type) {
    return switch (type.name()) {
      case "ENDER_DRAGON" -> 200;
      case "WITHER" -> 300;
      case "WARDEN" -> 500;
      case "IRON_GOLEM", "RAVAGER" -> 100;
      case "ELDER_GUARDIAN" -> 80;
      case "GHAST", "COW", "PIG", "SHEEP", "BEE", "CHICKEN" -> 10;
      case "BAT",
          "COD",
          "SALMON",
          "TROPICAL_FISH",
          "PUFFERFISH",
          "TADPOLE",
          "RABBIT",
          "SILVERFISH",
          "ENDERMITE" ->
          6;
      case "ENDERMAN", "HOGLIN", "ZOGLIN", "POLAR_BEAR" -> 40;
      default -> 20;
    };
  }

  public static double number(
      FileConfiguration config, String path, double fallback, double min, double max) {
    double value = config.getDouble(path, fallback);
    return Double.isFinite(value)
        ? org.plugin.theMob.compat.Numbers.clamp(value, min, max)
        : fallback;
  }

  public static String title(String text) {
    return Arrays.stream(text.toLowerCase(Locale.ROOT).split("_"))
        .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
        .reduce((a, b) -> a + " " + b)
        .orElse(text);
  }
}
