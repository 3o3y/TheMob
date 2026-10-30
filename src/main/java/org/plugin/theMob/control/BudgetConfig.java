package org.plugin.theMob.control;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public final class BudgetConfig {
  public boolean globalEnabled = true;
  public int globalTotal = 500;
  public int globalBosses = 10;
  public int globalMinions = 200;
  public boolean worldEnabled = true;
  public final Map<String, Integer> worldTotals = new HashMap<>();

  public void reload(FileConfiguration cfg) {
    this.globalEnabled = cfg.getBoolean("global-budgets.enabled", true);
    this.globalTotal = cfg.getInt("global-budgets.total-mobs", 500);
    this.globalBosses = cfg.getInt("global-budgets.bosses", 10);
    this.globalMinions = cfg.getInt("global-budgets.minions", 200);
    this.worldEnabled = cfg.getBoolean("world-budgets.enabled", true);
    this.worldTotals.clear();
    ConfigurationSection worlds = cfg.getConfigurationSection("world-budgets.worlds");
    if (worlds != null) {
      for (String w : worlds.getKeys(false)) {
        int total = worlds.getInt(w + ".total-mobs", -1);
        if (total >= 0) {
          this.worldTotals.put(w, total);
        }
      }
    }

    for (World wx : Bukkit.getWorlds()) {
      this.worldTotals.putIfAbsent(wx.getName(), this.worldTotals.getOrDefault(wx.getName(), -1));
    }
  }

  public int worldTotalBudget(String worldName) {
    Integer v = this.worldTotals.get(worldName);
    return v == null ? -1 : v;
  }

  public int worldCapSum() {
    int sum = 0;

    for (int v : this.worldTotals.values()) {
      if (v > 0) {
        sum += v;
      }
    }

    return sum;
  }

  public int worldCap(String worldName) {
    return this.worldTotalBudget(worldName);
  }
}
