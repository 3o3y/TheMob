package org.plugin.theMob.core;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;

public final class TickScheduler {

    private final JavaPlugin plugin;
    private final Set<BukkitTask> tasks = new HashSet<>();
    public TickScheduler(JavaPlugin plugin) {
        this.plugin = plugin;
    }
// SCHEDULE
    public BukkitTask runTimer(Runnable r, long delay, long period) {
        BukkitTask task = Bukkit.getScheduler()
                .runTaskTimer(plugin, r, delay, period);
        tasks.add(task);
        return task;
    }
    public BukkitTask runLater(Runnable r, long delay) {
        BukkitTask task = Bukkit.getScheduler()
                .runTaskLater(plugin, r, delay);
        tasks.add(task);
        return task;
    }
// SHUTDOWN
    public void shutdown() {
        for (BukkitTask t : tasks) {
            if (t != null && !t.isCancelled()) {
                t.cancel();
            }
        }
        tasks.clear();
    }
}
