package org.plugin.theMob.metrics;

import org.bukkit.plugin.java.JavaPlugin;
import org.plugin.theMob.libs.bstats.bukkit.Metrics;

public final class MetricsService {
  private static final int PLUGIN_ID = 28782;
  private static Metrics metrics;
  private static boolean chartsRegistered = false;

  private MetricsService() {}

  public static void init(JavaPlugin plugin) {
    if (metrics == null) {
      metrics = new Metrics(plugin, 28782);
    }
  }

  public static Metrics getMetrics() {
    return metrics;
  }

  public static boolean chartsRegistered() {
    return chartsRegistered;
  }

  public static void markChartsRegistered() {
    chartsRegistered = true;
  }
}
