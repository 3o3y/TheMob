package org.plugin.theMob.boss;

import org.bukkit.configuration.ConfigurationSection;

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
        this.id = id;
        this.minHpPercent = minHpPercent;
        this.maxHpPercent = maxHpPercent;
        this.title = title;
        this.section = section;
    }
    public String id() { return id; }
    public boolean matches(double hpPercent) {
        return hpPercent <= maxHpPercent && hpPercent > minHpPercent;
    }
    public String title() { return title; }
    public double min() { return minHpPercent; }
    public double max() { return maxHpPercent; }
    public ConfigurationSection cfg() { return section; }
}
