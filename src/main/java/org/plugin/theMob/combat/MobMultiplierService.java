package org.plugin.theMob.combat;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Locale;

public final class MobMultiplierService {

    private final Plugin plugin;

    // Tries a few common PDC keys for mob-id
    private final NamespacedKey keyA;
    private final NamespacedKey keyB;
    private final NamespacedKey keyC;

    public MobMultiplierService(Plugin plugin) {
        this.plugin = plugin;
        this.keyA = new NamespacedKey(plugin, "mob_id");
        this.keyB = new NamespacedKey(plugin, "themob_mob_id");
        this.keyC = new NamespacedKey("themob", "mob_id");
    }

    public String resolveMobId(LivingEntity e) {
        if (e == null) return null;
        PersistentDataContainer pdc = e.getPersistentDataContainer();

        String id = getString(pdc, keyA);
        if (id == null) id = getString(pdc, keyB);
        if (id == null) id = getString(pdc, keyC);

        return (id == null || id.isBlank()) ? null : id.trim();
    }

    public double multiplierFor(LivingEntity target, ConfigurationSection combatCfg) {
        if (target == null) return 1.0;

        // 1) Mob-ID
        String mobId = resolveMobId(target);
        if (mobId != null && combatCfg != null) {
            ConfigurationSection mob = combatCfg.getConfigurationSection("mob_multipliers");
            if (mob != null) {
                double v = mob.getDouble(mobId, Double.NaN);
                if (!Double.isNaN(v)) return v;
                // also try lowercase
                v = mob.getDouble(mobId.toLowerCase(Locale.ROOT), Double.NaN);
                if (!Double.isNaN(v)) return v;
            }
        }

        // 2) EntityType fallback
        if (combatCfg != null) {
            ConfigurationSection types = combatCfg.getConfigurationSection("entitytype_multipliers");
            if (types != null) {
                String type = target.getType().name().toLowerCase(Locale.ROOT);
                double v = types.getDouble(type, Double.NaN);
                if (!Double.isNaN(v)) return v;
            }
        }

        return 1.0;
    }

    private String getString(PersistentDataContainer pdc, NamespacedKey key) {
        if (pdc.has(key, PersistentDataType.STRING)) {
            return pdc.get(key, PersistentDataType.STRING);
        }
        return null;
    }
}
