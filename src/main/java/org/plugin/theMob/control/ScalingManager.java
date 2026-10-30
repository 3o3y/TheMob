package org.plugin.theMob.control;

import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public final class ScalingManager {
  private boolean enabled;
  private final NavigableMap<Integer, Double> upperBoundToMultiplier = new TreeMap<>();
  private double fallbackMultiplier = 1.0;

  public void reload(FileConfiguration cfg) {
    this.enabled = cfg.getBoolean("scaling.enabled", true);
    this.upperBoundToMultiplier.clear();
    this.fallbackMultiplier = 1.0;
    ConfigurationSection ranges = cfg.getConfigurationSection("scaling.player-ranges");
    if (ranges != null) {
      for (String key : ranges.getKeys(false)) {
        ConfigurationSection s = ranges.getConfigurationSection(key);
        if (s != null) {
          double mult = s.getDouble("spawn-multiplier", 1.0);
          String raw = key.toLowerCase().replace("players-", "");
          if (!raw.endsWith("-plus") && !raw.endsWith("+")) {
            String[] parts = raw.split("-");
            if (parts.length == 2) {
              Integer upper = tryParseInt(parts[1]);
              if (upper != null) {
                this.upperBoundToMultiplier.put(upper, mult);
              }
            }
          } else {
            this.fallbackMultiplier = mult;
          }
        }
      }
    }
  }

  public boolean enabled() {
    return this.enabled;
  }

  public double multiplierForPlayers(int onlinePlayers) {
    if (!this.enabled) {
      return 1.0;
    } else if (onlinePlayers <= 0) {
      return 0.0;
    } else {
      Entry<Integer, Double> entry = this.upperBoundToMultiplier.ceilingEntry(onlinePlayers);
      return entry != null ? entry.getValue() : this.fallbackMultiplier;
    }
  }

  private static Integer tryParseInt(String s) {
    try {
      return Integer.parseInt(s.trim());
    } catch (Exception var2) {
      return null;
    }
  }
}
