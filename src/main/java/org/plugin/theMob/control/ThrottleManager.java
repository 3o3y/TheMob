package org.plugin.theMob.control;

import org.bukkit.configuration.file.FileConfiguration;

public final class ThrottleManager {

    public enum State {
        NORMAL,
        WARNING,
        CRITICAL,
        HARD_STOP
    }

    private boolean enabled;

    private double tpsNormal = 19.5;
    private double tpsWarning = 18.0;
    private double tpsCritical = 16.0;

    private double warningMultiplier = 1.25;
    private double criticalMultiplier = 1.75;
    private boolean hardStopBelowCritical = true;

    public void reload(FileConfiguration cfg) {
        enabled = cfg.getBoolean("throttling.enabled", true);

        tpsNormal = cfg.getDouble("throttling.tps-thresholds.normal", 19.5);
        tpsWarning = cfg.getDouble("throttling.tps-thresholds.warning", 18.0);
        tpsCritical = cfg.getDouble("throttling.tps-thresholds.critical", 16.0);

        warningMultiplier = cfg.getDouble(
                "throttling.behavior.warning-multiplier", 1.25);

        criticalMultiplier = cfg.getDouble(
                "throttling.behavior.critical-multiplier", 1.75);

        hardStopBelowCritical = cfg.getBoolean(
                "throttling.behavior.hard-stop-below-critical", true);
    }


    public boolean enabled() {
        return enabled;
    }

    public State state(double tps) {
        if (!enabled) return State.NORMAL;

        if (hardStopBelowCritical && tps < tpsCritical) return State.HARD_STOP;
        if (tps < tpsWarning) return State.CRITICAL;
        if (tps < tpsNormal) return State.WARNING;
        return State.NORMAL;
    }

    public double intervalMultiplier(State state) {
        if (!enabled) return 1.0;
        return switch (state) {
            case NORMAL -> 1.0;
            case WARNING -> warningMultiplier;
            case CRITICAL, HARD_STOP -> criticalMultiplier;
        };
    }

    public double tpsNormal() { return tpsNormal; }
    public double tpsWarning() { return tpsWarning; }
    public double tpsCritical() { return tpsCritical; }
    public double warningMultiplier() { return warningMultiplier; }
    public double criticalMultiplier() { return criticalMultiplier; }
    public boolean hardStopBelowCritical() { return hardStopBelowCritical; }
}
