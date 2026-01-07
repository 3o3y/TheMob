package org.plugin.theMob.control;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

public final class BudgetConfig {

    public boolean globalEnabled = true;
    public int globalTotal = 500;
    public int globalBosses = 10;
    public int globalMinions = 200;

    public boolean worldEnabled = true;
    public final Map<String, Integer> worldTotals = new HashMap<>();

    public void reload(FileConfiguration cfg) {
        globalEnabled = cfg.getBoolean("global-budgets.enabled", true);
        globalTotal = cfg.getInt("global-budgets.total-mobs", 500);
        globalBosses = cfg.getInt("global-budgets.bosses", 10);
        globalMinions = cfg.getInt("global-budgets.minions", 200);

        worldEnabled = cfg.getBoolean("world-budgets.enabled", true);
        worldTotals.clear();

        ConfigurationSection worlds = cfg.getConfigurationSection("world-budgets.worlds");
        if (worlds != null) {
            for (String w : worlds.getKeys(false)) {
                int total = worlds.getInt(w + ".total-mobs", -1);
                if (total > 0) worldTotals.put(w, total);
            }
        }

        // ensure configured worlds exist (optional safety)
        for (World w : Bukkit.getWorlds()) {
            worldTotals.putIfAbsent(w.getName(), worldTotals.getOrDefault(w.getName(), -1));
        }
    }

    public int worldTotalBudget(String worldName) {
        Integer v = worldTotals.get(worldName);
        return v == null ? -1 : v;
    }
    // =========================
// WORLD CAP AGGREGATION
// =========================

    /** Sum of all configured world caps (>0). */
    public int worldCapSum() {
        int sum = 0;
        for (int v : worldTotals.values()) {
            if (v > 0) sum += v;
        }
        return sum;
    }

    /** Effective cap for a given world (global + world logic is handled elsewhere). */
    public int worldCap(String worldName) {
        return worldTotalBudget(worldName);
    }

}
