package org.plugin.theMob.progression;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

public final class ProgressionConfig {

    private final boolean enabled;

    public ProgressionConfig(Plugin plugin) {
        FileConfiguration cfg = plugin.getConfig();
        this.enabled = cfg.getBoolean("progression.enabled", false);
    }

    public boolean isEnabled() {
        return enabled;
    }
}
