package org.plugin.theMob.player.stats.menu;

import java.util.List;

public final class TierResolver {

    private TierResolver() {}

    /** Tier ist 0-basiert → Anzeige bleibt 0,1,2,3 */
    public static int tier(double value, List<Double> tiers) {
        int tier = 0;
        for (double v : tiers) {
            if (value >= v) tier++;
            else break;
        }
        return tier;
    }

    public static double prevValue(int tier, List<Double> tiers) {
        if (tier <= 0) return 0.0;
        if (tier > tiers.size()) return tiers.get(tiers.size() - 1);
        return tiers.get(tier - 1);
    }

    public static double nextValue(int tier, List<Double> tiers) {
        if (tier < 0) return tiers.get(0);
        if (tier >= tiers.size()) return -1;
        return tiers.get(tier);
    }

    public static double progress(double value, int tier, List<Double> tiers) {
        if (tiers.isEmpty()) return 0.0;

        double prev = prevValue(tier, tiers);
        double next = nextValue(tier, tiers);

        if (next <= prev || next < 0) return 1.0;

        double raw = (value - prev) / (next - prev);
        return Math.min(1.0, Math.max(0.0, raw));
    }
}
