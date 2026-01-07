package org.plugin.theMob.mob.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

/**
 * Prevents "AI section looks implemented" expectation mismatch.
 * Call once during mob config load.
 */
public final class MobConfigAiWarning {

    private MobConfigAiWarning() {}

    public static void warnIfAiConfigured(Plugin plugin, String mobId, ConfigurationSection cfg) {
        if (plugin == null || cfg == null) return;

        ConfigurationSection ai = cfg.getConfigurationSection("ai");
        if (ai == null) return;

        // If there's anything inside ai:, warn the operator
        if (!ai.getKeys(false).isEmpty()) {
            plugin.getLogger().warning(
                    "[TheMob] Mob '" + mobId + "' defines an 'ai:' section, " +
                            "but advanced AI/behavior scripting is not implemented yet. " +
                            "The mob will use vanilla AI + configured stats."
            );
        }
    }
}
