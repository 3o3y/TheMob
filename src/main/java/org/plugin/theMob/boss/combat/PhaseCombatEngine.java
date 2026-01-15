package org.plugin.theMob.boss.combat;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.plugin.theMob.core.KeyRegistry;

import java.util.Locale;

public final class PhaseCombatEngine {

    private final KeyRegistry keys;

    public PhaseCombatEngine(KeyRegistry keys) {
        this.keys = keys;
    }

    // =====================================================
    // APPLY COMBAT MODIFIERS (PHASE ENTER)
    // =====================================================
    public void apply(LivingEntity mob, ConfigurationSection phaseCfg) {
        if (mob == null || phaseCfg == null) return;

        ConfigurationSection combat = phaseCfg.getConfigurationSection("combat");
        if (combat == null) return;

        combat.getKeys(false).forEach(rawKey -> {

            Object rawValue = combat.get(rawKey);
            double value = parse(rawValue);
            if (value == 0.0) return;

            String norm = normalize(rawKey);

            var namespacedKey = keys.mobStat(norm);

            // 🔒 ABSOLUT KRITISCHER GUARD
            if (namespacedKey == null) {
                // Optional Debug:
                // Bukkit.getLogger().warning("[TheMob] Unknown combat stat ignored: " + norm);
                return;
            }

            mob.getPersistentDataContainer().set(
                    namespacedKey,
                    PersistentDataType.DOUBLE,
                    value
            );
        });
    }

    // =====================================================
    // ROLLBACK (PHASE LEAVE)
    // =====================================================
    public void rollback(LivingEntity mob) {
        if (mob == null) return;

        for (var e : keys.ALL_STATS.entrySet()) {
            String stat = e.getKey();

            if (stat.startsWith("crit")
                    || stat.startsWith("lifesteal")
                    || stat.startsWith("deal_knockback")
                    || stat.startsWith("receive_damage_multiplier")) {

                mob.getPersistentDataContainer().remove(e.getValue());
            }
        }
    }

    // =====================================================
    // HELPERS
    // =====================================================
    private double parse(Object o) {
        if (o == null) return 0.0;
        try {
            return Double.parseDouble(o.toString().replace("+", ""));
        } catch (Exception e) {
            return 0.0;
        }
    }

    private String normalize(String s) {
        return s == null
                ? ""
                : s.toLowerCase(Locale.ROOT).replace("-", "_");
    }
}
