package org.plugin.theMob.boss;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Objects;

public final class BossPhase {

    private final String id;
    private final double minHpPercent;
    private final double maxHpPercent;
    private final String title;
    private final ConfigurationSection section;

    public BossPhase(
            String id,
            double minHpPercent,
            double maxHpPercent,
            String title,
            ConfigurationSection section
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.title = title != null ? title : "";
        this.section = section;

        // Normalize & clamp to [0–100]
        double min = clamp(minHpPercent);
        double max = clamp(maxHpPercent);

        if (min > max) {
            double tmp = min;
            min = max;
            max = tmp;
        }

        this.minHpPercent = min;
        this.maxHpPercent = max;
    }

    public String id() {
        return id;
    }

    /**
     * Inclusive min, exclusive max
     * Example:
     * 100–75 → hp >= 75 && hp < 100
     * 25–0   → hp >= 0  && hp < 25
     */
    public boolean matches(double hpPercent) {
        if (Double.isNaN(hpPercent)) return false;

        double hp = clamp(hpPercent);
        return hp >= minHpPercent && hp < maxHpPercent;
    }

    public String title() {
        return title;
    }

    public double min() {
        return minHpPercent;
    }

    public double max() {
        return maxHpPercent;
    }

    public ConfigurationSection cfg() {
        return section;
    }

    private double clamp(double v) {
        return Math.max(0.0, Math.min(100.0, v));
    }
}
