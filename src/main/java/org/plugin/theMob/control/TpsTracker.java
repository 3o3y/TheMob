package org.plugin.theMob.control;

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
    private static final int WINDOW_1S = 20;     // 20 ticks
    private static final int WINDOW_1M = 1200;   // 1200 ticks (~60s)

    // =================================================
    // RING BUFFER (MSPT)
    // =================================================
    private final double[] msptRing = new double[WINDOW_1M];
    private int idx = 0;
    private int count = 0;
    private double sum1m = 0.0;

    private long lastTickNanos = -1L;
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
        task.runTaskTimer(plugin, 1L, 1L); // every tick
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        lastTickNanos = -1L;
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
    // TICK
    // =================================================
    private void tick() {
        long now = System.nanoTime();

        if (lastTickNanos < 0) {
            lastTickNanos = now;
            return;
        }

        long diff = now - lastTickNanos;
        lastTickNanos = now;

        double mspt = diff / 1_000_000.0;
        if (mspt < 0) mspt = 0;
        if (mspt > 10_000) mspt = 10_000;

        lastMspt = mspt;

        // ring buffer
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

        // TPS drop detection (realistic)
        double tps1sNow = tps1s();
        long nowMs = System.currentTimeMillis();

        boolean drop =
                (prevTps1s - tps1sNow) >= 2.0   // sharp drop
                        || tps1sNow < 18.0;          // sustained lag

        if (drop) {
            dropping = true;
            lastDropMillis = nowMs;
        } else if (dropping && (nowMs - lastDropMillis) > 5000) {
            dropping = false;
        }

        prevTps1s = tps1sNow;
    }

    // =================================================
    // MSPT
    // =================================================
    public double mspt() {
        return lastMspt;
    }

    public double mspt1s() {
        int n = Math.min(count, WINDOW_1S);
        if (n <= 0) return 50.0;

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
        return n <= 0 ? 50.0 : (sum1m / n);
    }

    // =================================================
    // TPS (derived)
    // =================================================
    public double tps1s() {
        return msptToTps(mspt1s());
    }

    public double tps1m() {
        return msptToTps(mspt1m());
    }

    private double msptToTps(double mspt) {
        if (mspt <= 0.0001) return 20.0;
        double tps = 1000.0 / mspt;
        if (tps > 20.0) tps = 20.0;
        if (tps < 0.0) tps = 0.0;
        return tps;
    }

    // =================================================
    // STATUS
    // =================================================
    public Status statusEnum() {
        double m = mspt1s();

        if (m <= 50.0) return Status.OK;
        if (m <= 65.0) return Status.WARN;
        if (m <= 85.0) return Status.CRITICAL;
        return Status.DANGER;
    }

    public String status() {
        return statusEnum().name();
    }

    public boolean isDropping() {
        return dropping;
    }
}
