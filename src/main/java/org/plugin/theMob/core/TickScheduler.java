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
        if (plugin == null) throw new IllegalArgumentException("plugin is null");
        this.plugin = plugin;
    }

    // =====================================================
    // BASIC API
    // =====================================================

    public BukkitTask syncRepeating(Runnable r, long delay, long period) {
        if (shutdown) throw new IllegalStateException("TickScheduler is shut down");
        if (r == null) throw new IllegalArgumentException("r is null");

        BukkitTask t = Bukkit.getScheduler().runTaskTimer(plugin, r, delay, period);
        tasks.add(t);
        return t;
    }

    public BukkitTask syncLater(Runnable r, long delay) {
        if (shutdown) throw new IllegalStateException("TickScheduler is shut down");
        if (r == null) throw new IllegalArgumentException("r is null");

        BukkitTask t = Bukkit.getScheduler().runTaskLater(plugin, r, delay);
        tasks.add(t);
        return t;
    }

    // =====================================================
    // COMPAT WRAPPERS (DEIN ALTER CALL)
    // =====================================================

    /**
     * Kompatibel zu deinem Call: registerRepeatingTask(20, runnable)
     * -> läuft alle 20 Ticks, Start nach 20 Ticks.
     */
    public BukkitTask registerRepeatingTask(long periodTicks, Runnable r) {
        return syncRepeating(r, periodTicks, periodTicks);
    }

    /**
     * Optional: wenn du Delay und Period getrennt willst.
     */
    public BukkitTask registerRepeatingTask(long delayTicks, long periodTicks, Runnable r) {
        return syncRepeating(r, delayTicks, periodTicks);
    }

    // =====================================================
    // SHUTDOWN
    // =====================================================

    public void shutdown() {
        shutdown = true;
        for (BukkitTask t : tasks) {
            try { t.cancel(); } catch (Throwable ignored) {}
        }
        tasks.clear();
    }
}
