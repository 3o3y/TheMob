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

        // clamp + normalize
        double min = Math.max(0.0, Math.min(100.0, minHpPercent));
        double max = Math.max(0.0, Math.min(100.0, maxHpPercent));

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

    public boolean matches(double hpPercent) {
        return hpPercent > minHpPercent && hpPercent <= maxHpPercent;
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
}
