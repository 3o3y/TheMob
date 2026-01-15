package org.plugin.theMob.mob.stats;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.plugin.theMob.core.KeyRegistry;

public final class BaseMobStatApplier {

    private static final double MIN_MOVE_SPEED = 0.05;
    private static final double MAX_MOVE_SPEED = 0.45;

    private final KeyRegistry keys;

    public BaseMobStatApplier(KeyRegistry keys) {
        this.keys = keys;
    }

    public void apply(LivingEntity mob, ConfigurationSection stats) {
        if (mob == null || stats == null) return;

        // =========================
        // HEALTH (override)
        // =========================
        if (stats.contains("health.max")) {
            double max = stats.getDouble("health.max");
            AttributeInstance hp = mob.getAttribute(Attribute.MAX_HEALTH);
            if (hp != null) {
                hp.setBaseValue(Math.max(1.0, max));

                double current = stats.contains("health.current")
                        ? stats.getDouble("health.current")
                        : max;

                mob.setHealth(Math.min(max, Math.max(0.0, current)));
            }
        }

        // =========================
        // ATTACK (override if present)
        // =========================
        // Supports BOTH styles:
        // - attack-damage: 3
        // - attack:
        //     damage: 16
        if (stats.contains("attack-damage")) {
            setAttrOverride(mob, Attribute.ATTACK_DAMAGE, stats.getDouble("attack-damage"));
        }
        if (stats.contains("attack.damage")) {
            setAttrOverride(mob, Attribute.ATTACK_DAMAGE, stats.getDouble("attack.damage"));
        }
        if (stats.contains("attack.speed")) {
            setAttrOverride(mob, Attribute.ATTACK_SPEED, stats.getDouble("attack.speed"));
        }

        // =========================
        // DEFENSE (override if present)
        // =========================
        if (stats.contains("defense.armor")) {
            setAttrOverride(mob, Attribute.ARMOR, stats.getDouble("defense.armor"));
        }
        if (stats.contains("defense.armor-toughness")) {
            setAttrOverride(mob, Attribute.ARMOR_TOUGHNESS, stats.getDouble("defense.armor-toughness"));
        }
        if (stats.contains("defense.knockback-resistance")) {
            double v = stats.getDouble("defense.knockback-resistance");
            v = Math.max(0.0, Math.min(1.0, v));
            setAttrOverride(mob, Attribute.KNOCKBACK_RESISTANCE, v);
        }

        // =========================
        // MOVEMENT (OVERRIDE, NOT ADD)
        // =========================
        if (stats.contains("movement-speed")) {
            AttributeInstance ms = mob.getAttribute(Attribute.MOVEMENT_SPEED);
            if (ms != null) {
                double value = stats.getDouble("movement-speed");
                value = Math.max(MIN_MOVE_SPEED, Math.min(MAX_MOVE_SPEED, value));
                ms.setBaseValue(value);
            }
        }

        // =========================
        // FOLLOW RANGE (override if present)
        // =========================
        if (stats.contains("follow-range")) {
            setAttrOverride(mob, Attribute.FOLLOW_RANGE, stats.getDouble("follow-range"));
        }

        // =========================
        // SCALE (override if present)
        // =========================
        if (stats.contains("scale")) {
            AttributeInstance scale = mob.getAttribute(Attribute.SCALE);
            if (scale != null) {
                double v = stats.getDouble("scale");
                v = Math.max(0.25, Math.min(5.0, v));
                scale.setBaseValue(v);
            }
        }

        // =========================
        // PDC MIRROR (for Combat & Phases)
        // only numeric leaf-values
        // =========================
        stats.getValues(true).forEach((k, v) -> {
            if (!(v instanceof Number n)) return;

            var key = keys.mobStat(k);
            if (key != null) {
                mob.getPersistentDataContainer().set(
                        key,
                        PersistentDataType.DOUBLE,
                        n.doubleValue()
                );
            }
        });
    }

    private void setAttrOverride(LivingEntity mob, Attribute attr, double value) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst == null) return;
        inst.setBaseValue(value);
    }
}
