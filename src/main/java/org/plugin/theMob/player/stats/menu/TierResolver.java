package org.plugin.theMob.player.stats.menu;

import java.util.List;

public final class TierResolver {

    private TierResolver() {}

    // =====================================================
    // NEW API (v1.5+ preferred)
    // =====================================================

    /**
     * Returns the current tier (1-based).
     */
    public static int tier(double value, List<Double> tiers) {
        int tier = 0;
        for (double v : tiers) {
            if (value >= v) tier++;
            else break;
        }
        return tier;
    }

    /**
     * Returns the previous tier value (or 0 if none).
     */
    public static double prevValue(int tier, List<Double> tiers) {
        if (tier <= 0) return 0.0;
        if (tier > tiers.size()) return tiers.get(tiers.size() - 1);
        return tiers.get(tier - 1);
    }

    /**
     * Returns the next tier target value, or -1 if max tier reached.
     */
    public static double nextValue(int tier, List<Double> tiers) {
        if (tier < 0) tier = 0;
        if (tier >= tiers.size()) return -1;
        return tiers.get(tier);
    }

    /**
     * Progress between previous and next tier (0.0 – 1.0).
     */
    public static double progress(double value, int tier, List<Double> tiers) {
        double prev = tier <= 0 ? 0.0 : tiers.get(tier - 1);
        double next = tier >= tiers.size() ? prev : tiers.get(tier);

        if (next <= prev) return 1.0;
        return Math.min(1.0, Math.max(0.0, (value - prev) / (next - prev)));
    }

    // =====================================================
    // LEGACY API (kept for compatibility)
    // =====================================================

    /**
     * Legacy: returns next tier value based on current value.
     */
    public static double next(double value, List<Double> tiers) {
        for (double v : tiers) {
            if (value < v) return v;
        }
        return -1;
    }

    /**
     * Legacy: progress between explicit prev / next values.
     */
    public static double progress(double value, double prev, double next) {
        if (next <= prev) return 1.0;
        return Math.min(1.0, Math.max(0.0, (value - prev) / (next - prev)));
    }
}
