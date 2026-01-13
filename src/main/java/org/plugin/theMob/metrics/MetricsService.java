package org.plugin.theMob.metrics;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;

import org.bukkit.plugin.java.JavaPlugin;

public final class MetricsService {

    private static final int PLUGIN_ID = 28782;
    private static Metrics metrics;
    private static boolean chartsRegistered = false;

    private MetricsService() {}

    public static void init(JavaPlugin plugin) {
        if (metrics != null) return;
        metrics = new Metrics(plugin, PLUGIN_ID);
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
