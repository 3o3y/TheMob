package org.plugin.theMob.mob.ai;

import org.bukkit.configuration.ConfigurationSection;
import org.plugin.theMob.mob.ai.target.SmartTargetingStrategy;
import org.plugin.theMob.mob.ai.target.TargetingStrategy;

public record MobAIProfile(
        TargetingStrategy targeting,
        int switchCooldown,
        double engageDistance,
        double disengageDistance,
        boolean fleeEnabled,
        double fleeThreshold,
        int regroupTicks,
        int tickRate
) {

    public static MobAIProfile fromConfig(ConfigurationSection cfg) {
        if (cfg == null) return presetAggressive();

        ConfigurationSection t = cfg.getConfigurationSection("targeting");

        double maxDistance = t != null ? t.getDouble("max-distance", 16) : 16;
        double engageDistance = cfg.getDouble("aggression.engage-distance", 14);

        TargetingStrategy strategy = new SmartTargetingStrategy(
                maxDistance,
                engageDistance
        );

        return new MobAIProfile(
                strategy,
                t != null ? t.getInt("switch-cooldown", 40) : 40,
                engageDistance,
                cfg.getDouble("aggression.disengage-distance", 22),
                cfg.getBoolean("flee.enabled", false),
                cfg.getDouble("flee.health-below", 0.25),
                cfg.getInt("flee.regroup-after", 80),
                cfg.getInt("throttle.tick-rate", 10)
        );
    }

    public static MobAIProfile presetAggressive() {
        return new MobAIProfile(
                new SmartTargetingStrategy(16, 14),
                40,
                14,
                22,
                false,
                0.25,
                80,
                10
        );
    }
}
