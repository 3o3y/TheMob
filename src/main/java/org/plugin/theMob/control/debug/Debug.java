package org.plugin.theMob.control.debug;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

public final class Debug {

    private static boolean enabled = false;

    private Debug() {}

    public static void reload(FileConfiguration cfg) {
        enabled = cfg.getBoolean("plugin.debug", false);
    }

    public static boolean enabled() {
        return enabled;
    }

    public static void log(String msg) {
        if (!enabled) return;
        Bukkit.getLogger().info("[TheMob][DEBUG] " + msg);
    }
}
