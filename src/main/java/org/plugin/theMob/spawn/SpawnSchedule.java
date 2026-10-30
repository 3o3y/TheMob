package org.plugin.theMob.spawn;

public final class SpawnSchedule {
  private SpawnSchedule() {}

  public static long intervalTicks(int seconds, double multiplier) {
    if (!Double.isFinite(multiplier) || multiplier <= 0) return 20L * 60 * 60 * 24;
    return org.plugin.theMob.compat.Numbers.clamp(
        (long) Math.ceil(Math.max(1, seconds) * 20.0 * multiplier), 1, 20L * 60 * 60 * 24);
  }
}
