package org.plugin.theMob.player.stats.menu;

import java.util.List;

public final class TierResolver {
  private TierResolver() {}

  public static int tier(double value, List<Double> tiers) {
    int tier = 0;

    for (double v : tiers) {
      if (!(value >= v)) {
        break;
      }

      tier++;
    }

    return tier;
  }

  public static double prevValue(int tier, List<Double> tiers) {
    if (tier <= 0) {
      return 0.0;
    } else {
      return tier > tiers.size() ? tiers.get(tiers.size() - 1) : tiers.get(tier - 1);
    }
  }

  public static double nextValue(int tier, List<Double> tiers) {
    if (tier < 0) {
      return tiers.get(0);
    } else {
      return tier >= tiers.size() ? -1.0 : tiers.get(tier);
    }
  }

  public static double progress(double value, int tier, List<Double> tiers) {
    if (tiers.isEmpty()) {
      return 0.0;
    } else {
      double prev = prevValue(tier, tiers);
      double next = nextValue(tier, tiers);
      if (!(next <= prev) && !(next < 0.0)) {
        double raw = (value - prev) / (next - prev);
        return Math.min(1.0, Math.max(0.0, raw));
      } else {
        return 1.0;
      }
    }
  }
}
