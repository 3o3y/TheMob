package org.plugin.theMob.player.stats.menu;

import java.util.List;

public final class TierResolver {

    private TierResolver() {}
    public static int tier(double value, List<Double> tiers) {
        int t = 0;
        for (double v : tiers) {
            if (value >= v) t++;
            else break;
        }
        return t;
    }
    public static double next(double value, List<Double> tiers) {
        for (double v : tiers) {
            if (value < v) return v;
        }
        return -1;
    }
    public static double progress(double value, double prev, double next) {
        if (next <= prev) return 1.0;
        return Math.min(1.0, (value - prev) / (next - prev));
    }
}
