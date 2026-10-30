package org.plugin.theMob.control;

import java.util.function.LongSupplier;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class TpsTracker {
  private static final int WINDOW_1S = 20;
  private static final int WINDOW_1M = 1200;
  private final double[] msptRing = new double[1200];
  private int idx = 0;
  private int count = 0;
  private double sum1m = 0.0;
  private double lastMspt = 50.0;
  private double prevTps1s = 20.0;
  private long lastDropMillis = 0L;
  private boolean dropping = false;
  private BukkitRunnable task;
  private final LongSupplier nanoTime;
  private long lastTickNanos;
  private long currentTick;
  private boolean sampled;

  public TpsTracker() {
    this(System::nanoTime);
  }

  TpsTracker(LongSupplier nanoTime) {
    this.nanoTime = nanoTime;
    this.lastTickNanos = nanoTime.getAsLong();
  }

  /** Plugin-local server ticks; shared by spawning, HUD and minion guards. */
  public long currentTick() {
    return currentTick;
  }

  public void start(JavaPlugin plugin) {
    if (this.task == null) {
      this.lastTickNanos = nanoTime.getAsLong();
      this.sampled = false;
      this.task =
          new BukkitRunnable() {
            public void run() {
              TpsTracker.this.tick();
            }
          };
      this.task.runTaskTimer(plugin, 1L, 1L);
    }
  }

  public void stop() {
    if (this.task != null) {
      this.task.cancel();
      this.task = null;
    }

    this.idx = 0;
    this.count = 0;
    this.sum1m = 0.0;
    this.lastMspt = 50.0;
    this.prevTps1s = 20.0;
    this.lastDropMillis = 0L;
    this.dropping = false;
    this.currentTick = 0;
    this.sampled = false;
    this.lastTickNanos = nanoTime.getAsLong();

    for (int i = 0; i < this.msptRing.length; i++) {
      this.msptRing[i] = 0.0;
    }
  }

  void tick() {
    currentTick++;
    double mspt = this.readRealMspt();
    this.lastMspt = mspt;
    double old = this.msptRing[this.idx];
    this.msptRing[this.idx] = mspt;
    if (this.count < 1200) {
      this.count++;
    } else {
      this.sum1m -= old;
    }

    this.sum1m += mspt;
    this.idx++;
    if (this.idx >= 1200) {
      this.idx = 0;
    }

    double tps1sNow = this.tps1s();
    long nowMs = System.currentTimeMillis();
    boolean drop = this.prevTps1s - tps1sNow >= 2.0 || tps1sNow < 18.0;
    if (drop) {
      this.dropping = true;
      this.lastDropMillis = nowMs;
    } else if (this.dropping && nowMs - this.lastDropMillis > 5000L) {
      this.dropping = false;
    }

    this.prevTps1s = tps1sNow;
  }

  private double readRealMspt() {
    long now = nanoTime.getAsLong();
    double elapsed = Math.max(0L, now - lastTickNanos) / 1_000_000.0;
    lastTickNanos = now;
    if (!sampled) {
      sampled = true;
      return 50.0;
    }
    // Spigot exposes no MSPT sampler. Measure actual tick intervals, including long stalls.
    return Math.max(50.0, elapsed);
  }

  public double mspt() {
    return this.lastMspt;
  }

  public double mspt1s() {
    int n = Math.min(this.count, 20);
    if (n <= 0) {
      return this.lastMspt;
    } else {
      double sum = 0.0;

      for (int i = 1; i <= n; i++) {
        int p = this.idx - i;
        if (p < 0) {
          p += 1200;
        }

        sum += this.msptRing[p];
      }

      return sum / n;
    }
  }

  public double mspt1m() {
    int n = Math.min(this.count, 1200);
    return n <= 0 ? this.lastMspt : this.sum1m / n;
  }

  public double tps1s() {
    return this.msptToTps(this.mspt1s());
  }

  public double tps1m() {
    return this.msptToTps(this.mspt1m());
  }

  private double msptToTps(double mspt) {
    return mspt <= 1.0E-4 ? 20.0 : Math.min(20.0, 1000.0 / mspt);
  }

  public TpsTracker.Status statusEnum() {
    double m = this.mspt1s();
    if (m <= 50.0) {
      return TpsTracker.Status.OK;
    } else if (m <= 55.0) {
      return TpsTracker.Status.WARN;
    } else {
      return m <= 85.0 ? TpsTracker.Status.CRITICAL : TpsTracker.Status.DANGER;
    }
  }

  public boolean isDropping() {
    return this.dropping;
  }

  public String status() {
    return this.statusEnum().name();
  }

  public static enum Status {
    OK,
    WARN,
    CRITICAL,
    DANGER;
  }
}
