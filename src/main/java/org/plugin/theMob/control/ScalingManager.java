package org.plugin.theMob.control;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.NavigableMap;
import java.util.TreeMap;

public final class ScalingManager {

    private boolean enabled;
    private final NavigableMap<Integer, Double> upperBoundToMultiplier = new TreeMap<>();
    private double fallbackMultiplier = 1.0;

    public void reload(FileConfiguration cfg) {
        enabled = cfg.getBoolean("scaling.enabled", true);

        upperBoundToMultiplier.clear();
        fallbackMultiplier = 1.0;

        ConfigurationSection ranges = cfg.getConfigurationSection("scaling.player-ranges");
        if (ranges == null) return;

        for (String key : ranges.getKeys(false)) {
            ConfigurationSection s = ranges.getConfigurationSection(key);
            if (s == null) continue;

            double mult = s.getDouble("spawn-multiplier", 1.0);

            // Expected keys: players-1-10, players-11-50, players-51-150, players-151-plus
            // We parse the upper bound; "plus" means fallback.
            String raw = key.toLowerCase().replace("players-", "");
            if (raw.endsWith("-plus") || raw.endsWith("+")) {
                fallbackMultiplier = mult;
                continue;
            }

            String[] parts = raw.split("-");
            if (parts.length != 2) continue;

            Integer upper = tryParseInt(parts[1]);
            if (upper == null) continue;

            upperBoundToMultiplier.put(upper, mult);
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public double multiplierForPlayers(int onlinePlayers) {
        if (!enabled) return 1.0;
        if (onlinePlayers <= 0) return 0.0;

        var entry = upperBoundToMultiplier.ceilingEntry(onlinePlayers);
        if (entry != null) return entry.getValue();

        return fallbackMultiplier;
    }

    private static Integer tryParseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception ignored) {
            return null;
        }
    }
}
