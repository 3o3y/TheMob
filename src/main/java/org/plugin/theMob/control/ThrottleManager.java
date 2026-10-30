package org.plugin.theMob.control;

import org.bukkit.configuration.file.FileConfiguration;

public final class ThrottleManager {
  private boolean enabled;
  private double tpsNormal = 19.5;
  private double tpsWarning = 18.0;
  private double tpsCritical = 16.0;
  private double warningMultiplier = 1.25;
  private double criticalMultiplier = 1.75;
  private boolean hardStopBelowCritical = true;

  public void reload(FileConfiguration cfg) {
    this.enabled = cfg.getBoolean("throttling.enabled", true);
    this.tpsNormal = cfg.getDouble("throttling.tps-thresholds.normal", 19.5);
    this.tpsWarning = cfg.getDouble("throttling.tps-thresholds.warning", 18.0);
    this.tpsCritical = cfg.getDouble("throttling.tps-thresholds.critical", 16.0);
    this.warningMultiplier = cfg.getDouble("throttling.behavior.warning-multiplier", 1.25);
    this.criticalMultiplier = cfg.getDouble("throttling.behavior.critical-multiplier", 1.75);
    this.hardStopBelowCritical =
        cfg.getBoolean("throttling.behavior.hard-stop-below-critical", true);
  }

  public boolean enabled() {
    return this.enabled;
  }

  public ThrottleManager.State state(double tps) {
    if (!this.enabled) {
      return ThrottleManager.State.NORMAL;
    } else if (this.hardStopBelowCritical && tps < this.tpsCritical) {
      return ThrottleManager.State.HARD_STOP;
    } else if (tps < this.tpsWarning) {
      return ThrottleManager.State.CRITICAL;
    } else {
      return tps < this.tpsNormal ? ThrottleManager.State.WARNING : ThrottleManager.State.NORMAL;
    }
  }

  public double intervalMultiplier(ThrottleManager.State state) {
    if (!this.enabled) {
      return 1.0;
    } else {
      return switch (state) {
        case NORMAL -> 1.0;
        case WARNING -> this.warningMultiplier;
        case CRITICAL, HARD_STOP -> this.criticalMultiplier;
      };
    }
  }

  public double tpsNormal() {
    return this.tpsNormal;
  }

  public double tpsWarning() {
    return this.tpsWarning;
  }

  public double tpsCritical() {
    return this.tpsCritical;
  }

  public double warningMultiplier() {
    return this.warningMultiplier;
  }

  public double criticalMultiplier() {
    return this.criticalMultiplier;
  }

  public boolean hardStopBelowCritical() {
    return this.hardStopBelowCritical;
  }

  public static enum State {
    NORMAL,
    WARNING,
    CRITICAL,
    HARD_STOP;
  }
}
