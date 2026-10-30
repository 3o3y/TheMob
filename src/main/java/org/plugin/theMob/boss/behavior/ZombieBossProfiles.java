package org.plugin.theMob.boss.behavior;

import java.io.File;
import java.util.Map;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Runtime overlays also update existing installations without rewriting their mob/loot files. */
public final class ZombieBossProfiles {
  private ZombieBossProfiles() {}

  public static void apply(JavaPlugin plugin, Map<String, FileConfiguration> mobs) {
    File file = new File(plugin.getDataFolder(), "zombie-boss.yml");
    if (!file.exists()) plugin.saveResource("zombie-boss.yml", false);
    var settings = YamlConfiguration.loadConfiguration(file);
    if (!settings.getBoolean("enabled", true)) return;
    apply(
        mobs.get("zombie_boss_template"),
        settings,
        Map.of("phase1", "royal", "phase2", "hunt", "phase3", "frenzy", "phase4", "desperate"));
    if (settings.getBoolean("include-warlords", true))
      for (int level = 1; level <= 5; level++)
        apply(
            mobs.get("tm_boss_warlord_lvl_" + level),
            settings,
            Map.of("guard", "royal", "pressure", "hunt", "rage", "frenzy"));
  }

  private static void apply(
      FileConfiguration mob, FileConfiguration settings, Map<String, String> stages) {
    if (mob == null
        || !"ZOMBIE".equalsIgnoreCase(mob.getString("base-type", ""))
        || mob.getBoolean("zombie-behavior-disabled", false)) return;
    stages.forEach(
        (phase, stage) -> {
          var cfg = mob.getConfigurationSection("phases." + phase);
          var defaults = settings.getConfigurationSection("stages." + stage);
          if (cfg == null || defaults == null) return;
          // Explicit per-mob zombie settings take precedence over the shared profile.
          for (String key : defaults.getKeys(false))
            if (!cfg.isSet("behavior." + key)) cfg.set("behavior." + key, defaults.get(key));
          cfg.set("behavior.mode", "zombie_king");
          cfg.set("behavior.zombie-stage", stage);
          // This boss walks and fights on the ground; the original phase/loot/stats stay intact.
          cfg.set("physics.gravity", true);
          cfg.set("physics.levitation", 0);
          cfg.set("physics.slow-falling", false);
        });
  }
}
