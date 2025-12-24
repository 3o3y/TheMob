package org.plugin.theMob.core;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class MainThread {

    private MainThread() {}

    public static void run(Plugin plugin, Runnable r) {
        if (Bukkit.isPrimaryThread()) {
            r.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, r);
        }
    }

    public static void runLater(Plugin plugin, Runnable r, long delayTicks) {
        Bukkit.getScheduler().runTaskLater(plugin, r, delayTicks);
    }
}
