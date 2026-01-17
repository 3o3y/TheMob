package org.plugin.theMob.control;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class TpsTracker {

    // =================================================
    // STATUS
    // =================================================
    public enum Status {
        OK, WARN, CRITICAL, DANGER
    }

    // =================================================
    // WINDOWS
    // =================================================
    private static final int WINDOW_1S = 20;     // ~1s
    private static final int WINDOW_1M = 1200;   // ~60s

    // =================================================
    // RING BUFFER (REAL MSPT)
    // =================================================
    private final double[] msptRing = new double[WINDOW_1M];
    private int idx = 0;
    private int count = 0;
    private double sum1m = 0.0;

    private double lastMspt = 50.0;

    // =================================================
    // DROP DETECTION
    // =================================================
    private double prevTps1s = 20.0;
    private long lastDropMillis = 0L;
    private boolean dropping = false;

    private BukkitRunnable task;

    // =================================================
    // LIFECYCLE
    // =================================================
    public void start(JavaPlugin plugin) {
        if (task != null) return;

        task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        };
        task.runTaskTimer(plugin, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        idx = 0;
        count = 0;
        sum1m = 0.0;
        lastMspt = 50.0;
        prevTps1s = 20.0;
        lastDropMillis = 0L;
        dropping = false;

        for (int i = 0; i < msptRing.length; i++) {
            msptRing[i] = 0.0;
        }
    }

    // =================================================
    // TICK (REAL MSPT SOURCE)
    // =================================================
    private void tick() {

        double mspt = readRealMspt();
        lastMspt = mspt;

        double old = msptRing[idx];
        msptRing[idx] = mspt;

        if (count < WINDOW_1M) {
            count++;
        } else {
            sum1m -= old;
        }
        sum1m += mspt;

        idx++;
        if (idx >= WINDOW_1M) idx = 0;

        // TPS drop detection
        double tps1sNow = tps1s();
        long nowMs = System.currentTimeMillis();

        boolean drop =
                (prevTps1s - tps1sNow) >= 2.0
                        || tps1sNow < 18.0;

        if (drop) {
            dropping = true;
            lastDropMillis = nowMs;
        } else if (dropping && (nowMs - lastDropMillis) > 5000) {
            dropping = false;
        }

        prevTps1s = tps1sNow;
    }

    // =================================================
    // MSPT SOURCE
    // =================================================
    private double readRealMspt() {
        try {
            // Paper only – REAL tick cost (no sleep)
            double mspt = Bukkit.getServer().getAverageTickTime();
            if (mspt > 0 && mspt < 1000) {
                return mspt;
            }
        } catch (Throwable ignored) {}

        // fallback only if Paper not available
        return lastMspt;
    }

    // =================================================
    // MSPT API
    // =================================================
    public double mspt() {
        return lastMspt;
    }

    public double mspt1s() {
        int n = Math.min(count, WINDOW_1S);
        if (n <= 0) return lastMspt;

        double sum = 0.0;
        for (int i = 1; i <= n; i++) {
            int p = idx - i;
            if (p < 0) p += WINDOW_1M;
            sum += msptRing[p];
        }
        return sum / n;
    }

    public double mspt1m() {
        int n = Math.min(count, WINDOW_1M);
        return n <= 0 ? lastMspt : (sum1m / n);
    }

    // =================================================
    // TPS (DERIVED)
    // =================================================
    public double tps1s() {
        return msptToTps(mspt1s());
    }

    public double tps1m() {
        return msptToTps(mspt1m());
    }

    private double msptToTps(double mspt) {
        if (mspt <= 0.0001) return 20.0;
        return Math.min(20.0, 1000.0 / mspt);
    }

    // =================================================
    // STATUS
    // =================================================
    public Status statusEnum() {
        double m = mspt1s();

        if (m <= 45.0) return Status.OK;
        if (m <= 55.0) return Status.WARN;
        if (m <= 85.0) return Status.CRITICAL;
        return Status.DANGER;
    }

    public boolean isDropping() {
        return dropping;
    }
    public String status() {
        return statusEnum().name();
    }

}
