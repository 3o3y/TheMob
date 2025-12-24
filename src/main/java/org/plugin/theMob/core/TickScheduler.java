// src/main/java/org/plugin/theMob/core/TickScheduler.java
package org.plugin.theMob.core;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

public final class TickScheduler {

    private final Plugin plugin;
    private final List<BukkitTask> tasks = new ArrayList<>();
    private volatile boolean shutdown;

    public TickScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    public BukkitTask syncRepeating(Runnable r, long delay, long period) {
        if (shutdown) throw new IllegalStateException("TickScheduler is shut down");
        BukkitTask t = Bukkit.getScheduler().runTaskTimer(plugin, r, delay, period);
        tasks.add(t);
        return t;
    }

    public BukkitTask syncLater(Runnable r, long delay) {
        if (shutdown) throw new IllegalStateException("TickScheduler is shut down");
        BukkitTask t = Bukkit.getScheduler().runTaskLater(plugin, r, delay);
        tasks.add(t);
        return t;
    }

    public void shutdown() {
        shutdown = true;
        for (BukkitTask t : tasks) {
            try { t.cancel(); } catch (Throwable ignored) {}
        }
        tasks.clear();
    }
}
