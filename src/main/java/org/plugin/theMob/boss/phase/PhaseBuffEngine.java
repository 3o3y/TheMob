package org.plugin.theMob.boss.phase;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.plugin.theMob.core.KeyRegistry;

public final class PhaseBuffEngine {

    private final KeyRegistry keys;
    private final NamespacedKey SNAPSHOT_KEY;

    public PhaseBuffEngine(KeyRegistry keys) {
        this.keys = keys;
        this.SNAPSHOT_KEY = new NamespacedKey(
                keys.DAMAGE.getNamespace(),
                "attribute_snapshot"
        );
    }

    // =====================================================
    // APPLY PHASE BUFFS (SAFE + CLAMPED)
    // =====================================================
    public void applyPhase(LivingEntity mob, ConfigurationSection phaseCfg) {
        if (mob == null || phaseCfg == null) return;

        ConfigurationSection buffs = phaseCfg.getConfigurationSection("buffs");
        if (buffs == null || buffs.getKeys(false).isEmpty()) return;

        PersistentDataContainer pdc = mob.getPersistentDataContainer();

        // SNAPSHOT ONCE
        if (!pdc.has(SNAPSHOT_KEY, PersistentDataType.TAG_CONTAINER)) {
            AttributeSnapshot snapshot = AttributeSnapshot.capture(mob);
            PersistentDataContainer tag =
                    pdc.getAdapterContext().newPersistentDataContainer();
            snapshot.writeTo(tag);
            pdc.set(SNAPSHOT_KEY, PersistentDataType.TAG_CONTAINER, tag);
        }

        for (String key : buffs.getKeys(false)) {
            double delta = parse(buffs.get(key));
            if (delta == 0.0) continue;

            Attribute attr = mapAttribute(key);

            if (attr != null) {
                AttributeInstance inst = mob.getAttribute(attr);
                if (inst == null) continue;

                double target = AttributeSafety.clamp(
                        attr,
                        inst.getBaseValue() + delta
                );
                inst.setBaseValue(target);
                continue;
            }

            // CUSTOM STAT
            var statKey = keys.mobStat(key);
            if (statKey != null) {
                double current = pdc.getOrDefault(
                        statKey,
                        PersistentDataType.DOUBLE,
                        0.0
                );
                pdc.set(statKey, PersistentDataType.DOUBLE, current + delta);
            }
        }
    }

    // =====================================================
    // FULL RESTORE
    // =====================================================
    public void rollbackPhase(LivingEntity mob) {
        if (mob == null) return;

        PersistentDataContainer pdc = mob.getPersistentDataContainer();
        if (!pdc.has(SNAPSHOT_KEY, PersistentDataType.TAG_CONTAINER)) return;

        AttributeSnapshot snapshot =
                AttributeSnapshot.readFrom(
                        pdc.get(SNAPSHOT_KEY, PersistentDataType.TAG_CONTAINER)
                );

        snapshot.restore(mob);
        pdc.remove(SNAPSHOT_KEY);
    }

    // =====================================================
    // HELPERS
    // =====================================================
    private double parse(Object o) {
        try {
            return Double.parseDouble(o.toString().replace("+", "").trim());
        } catch (Exception e) {
            return 0.0;
        }
    }

    private Attribute mapAttribute(String key) {
        return switch (key.toLowerCase()) {
            case "movement-speed" -> Attribute.MOVEMENT_SPEED;
            case "armor" -> Attribute.ARMOR;
            case "armor-toughness" -> Attribute.ARMOR_TOUGHNESS;
            case "max-health" -> Attribute.MAX_HEALTH;
            case "knockback-resistance" -> Attribute.KNOCKBACK_RESISTANCE;
            case "follow-range" -> Attribute.FOLLOW_RANGE;
            default -> null;
        };
    }
}
