package org.plugin.theMob.boss;

import java.util.Objects;
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
      ConfigurationSection section) {
    this.id = Objects.requireNonNull(id, "id");
    this.title = title != null ? title : "";
    this.section = section;
    double min = this.clamp(minHpPercent);
    double max = this.clamp(maxHpPercent);
    if (min > max) {
      double tmp = min;
      min = max;
      max = tmp;
    }

    this.minHpPercent = min;
    this.maxHpPercent = max;
  }

  public String id() {
    return this.id;
  }

  public boolean matches(double hpPercent) {
    if (Double.isNaN(hpPercent)) {
      return false;
    } else {
      double hp = this.clamp(hpPercent);
      return hp >= this.minHpPercent && hp < this.maxHpPercent;
    }
  }

  public String title() {
    return this.title;
  }

  public double min() {
    return this.minHpPercent;
  }

  public double max() {
    return this.maxHpPercent;
  }

  public ConfigurationSection cfg() {
    return this.section;
  }

  private double clamp(double v) {
    return Math.max(0.0, Math.min(100.0, v));
  }
}
