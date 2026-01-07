package org.plugin.theMob.control;

import org.bukkit.Bukkit;

public final class TpsUtil {

    private TpsUtil() {}

    public static double getPaperTps1mOr20() {
        try {
            double[] tps = Bukkit.getTPS(); // Paper only
            if (tps != null && tps.length > 0 && tps[0] > 0) {
                return tps[0];
            }
        } catch (Throwable ignored) {}
        return 20.0;
    }

    public static boolean isPaper() {
        try {
            Bukkit.getTPS();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
