package org.plugin.theMob.combat;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Locale;

public final class MobMultiplierService {

    private final NamespacedKey mobIdKeyPlugin;      // <plugin>:mob_id
    private final NamespacedKey mobIdKeyLegacy;      // <plugin>:themob_mob_id (legacy)
    private final NamespacedKey mobIdKeyTheMobNs;    // themob:mob_id

    public MobMultiplierService(Plugin plugin) {
        this.mobIdKeyPlugin = new NamespacedKey(plugin, "mob_id");
        this.mobIdKeyLegacy = new NamespacedKey(plugin, "themob_mob_id");
        this.mobIdKeyTheMobNs = new NamespacedKey("themob", "mob_id");
    }

    /**
     * Resolve TheMob mob-id stored in PDC.
     * Returns lowercase id or null.
     */
    public String resolveMobId(LivingEntity e) {
        if (e == null) return null;

        PersistentDataContainer pdc = e.getPersistentDataContainer();

        String id = readString(pdc, mobIdKeyPlugin);
        if (id == null) id = readString(pdc, mobIdKeyLegacy);
        if (id == null) id = readString(pdc, mobIdKeyTheMobNs);

        if (id == null) return null;

        id = id.trim();
        return id.isEmpty() ? null : id.toLowerCase(Locale.ROOT);
    }

    /**
     * If you ONLY want multipliers for TheMob mobs (recommended),
     * set requireTheMobId = true.
     */
    public double multiplierFor(LivingEntity target, ConfigurationSection combatCfg, boolean requireTheMobId) {
        if (target == null || combatCfg == null) return 1.0;

        String mobId = resolveMobId(target);

        // ✅ recommended safety:
        // only apply multipliers if target is actually a TheMob mob (has mobId)
        if (requireTheMobId && mobId == null) {
            return 1.0;
        }

        // 1) mob-id based multipliers
        ConfigurationSection mobSection = combatCfg.getConfigurationSection("mob_multipliers");
        if (mobSection != null && mobId != null) {
            double v = mobSection.getDouble(mobId, Double.NaN);
            if (!Double.isNaN(v)) return clamp(v);
        }

        // 2) entity type based multipliers (careful: affects ALL vanilla mobs of that type)
        ConfigurationSection typeSection = combatCfg.getConfigurationSection("entitytype_multipliers");
        if (typeSection != null) {
            String type = target.getType().name().toLowerCase(Locale.ROOT);
            double v = typeSection.getDouble(type, Double.NaN);
            if (!Double.isNaN(v)) return clamp(v);
        }

        return 1.0;
    }

    /**
     * Backwards compatible signature: old behavior (may affect vanilla mobs too).
     */
    public double multiplierFor(LivingEntity target, ConfigurationSection combatCfg) {
        return multiplierFor(target, combatCfg, false);
    }

    private String readString(PersistentDataContainer pdc, NamespacedKey key) {
        if (pdc == null || key == null) return null;
        if (!pdc.has(key, PersistentDataType.STRING)) return null;
        return pdc.get(key, PersistentDataType.STRING);
    }

    private double clamp(double v) {
        if (v < 0.05) return 0.05;
        if (v > 50.0) return 50.0;
        return v;
    }
}
