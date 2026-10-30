package org.plugin.theMob.core;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class TickScheduler {
  private final Plugin plugin;
  private final List<BukkitTask> tasks = new ArrayList<>();
  private volatile boolean shutdown;

  public TickScheduler(Plugin plugin) {
    if (plugin == null) {
      throw new IllegalArgumentException("plugin is null");
    } else {
      this.plugin = plugin;
    }
  }

  public BukkitTask syncRepeating(Runnable r, long delay, long period) {
    if (this.shutdown) {
      throw new IllegalStateException("TickScheduler is shut down");
    } else if (r == null) {
      throw new IllegalArgumentException("r is null");
    } else {
      BukkitTask t = Bukkit.getScheduler().runTaskTimer(this.plugin, r, delay, period);
      this.tasks.add(t);
      return t;
    }
  }

  public BukkitTask syncLater(Runnable r, long delay) {
    if (this.shutdown) {
      throw new IllegalStateException("TickScheduler is shut down");
    } else if (r == null) {
      throw new IllegalArgumentException("r is null");
    } else {
      BukkitTask t = Bukkit.getScheduler().runTaskLater(this.plugin, r, delay);
      this.tasks.add(t);
      return t;
    }
  }

  public BukkitTask registerRepeatingTask(long periodTicks, Runnable r) {
    return this.syncRepeating(r, periodTicks, periodTicks);
  }

  public BukkitTask registerRepeatingTask(long delayTicks, long periodTicks, Runnable r) {
    return this.syncRepeating(r, delayTicks, periodTicks);
  }

  public void shutdown() {
    this.shutdown = true;

    for (BukkitTask t : this.tasks) {
      try {
        t.cancel();
      } catch (Throwable var4) {
      }
    }

    this.tasks.clear();
  }
}
