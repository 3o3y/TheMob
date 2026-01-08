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

        String id = read(pdc, keyA);
        if (id == null) id = read(pdc, keyB);
        if (id == null) id = read(pdc, keyC);

        if (id == null) return null;

        id = id.trim();
        return id.isEmpty() ? null : id.toLowerCase(Locale.ROOT);
    }

    public double multiplierFor(LivingEntity target, ConfigurationSection combatCfg) {
        if (target == null || combatCfg == null) return 1.0;

        ConfigurationSection mobSection = combatCfg.getConfigurationSection("mob_multipliers");
        if (mobSection != null) {
            String mobId = resolveMobId(target);
            if (mobId != null) {
                double v = mobSection.getDouble(mobId, Double.NaN);
                if (!Double.isNaN(v)) return clamp(v);
            }
        }

        ConfigurationSection typeSection = combatCfg.getConfigurationSection("entitytype_multipliers");
        if (typeSection != null) {
            String type = target.getType().name().toLowerCase(Locale.ROOT);
            double v = typeSection.getDouble(type, Double.NaN);
            if (!Double.isNaN(v)) return clamp(v);
        }

        return 1.0;
    }

    private String read(PersistentDataContainer pdc, NamespacedKey key) {
        return pdc.has(key, PersistentDataType.STRING)
                ? pdc.get(key, PersistentDataType.STRING)
                : null;
    }

    private double clamp(double v) {
        if (v < 0.05) return 0.05;
        if (v > 50.0) return 50.0;
        return v;
    }
}
