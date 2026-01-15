package org.plugin.theMob.boss.phase;

import org.bukkit.attribute.Attribute;

import java.util.HashMap;
import java.util.Map;

public final class AttributeSafety {

    private static final Map<Attribute, Limits> LIMITS = new HashMap<>();

    static {
        // ===== MOVEMENT =====
        LIMITS.put(Attribute.MOVEMENT_SPEED, new Limits(0.05, 0.45));
        LIMITS.put(Attribute.FLYING_SPEED, new Limits(0.05, 0.80));

        // ===== COMBAT =====
        LIMITS.put(Attribute.ATTACK_DAMAGE, new Limits(0.0, 2048.0));
        LIMITS.put(Attribute.ATTACK_SPEED, new Limits(0.1, 1024.0));

        // ===== DEFENSE =====
        LIMITS.put(Attribute.ARMOR, new Limits(0.0, 100.0));
        LIMITS.put(Attribute.ARMOR_TOUGHNESS, new Limits(0.0, 50.0));
        LIMITS.put(Attribute.KNOCKBACK_RESISTANCE, new Limits(0.0, 1.0));

        // ===== HEALTH =====
        LIMITS.put(Attribute.MAX_HEALTH, new Limits(1.0, 100000.0));

        // ===== AI =====
        LIMITS.put(Attribute.FOLLOW_RANGE, new Limits(1.0, 128.0));
    }

    private AttributeSafety() {}

    public static double clamp(Attribute attr, double value) {
        if (attr == null) return value;
        Limits l = LIMITS.get(attr);
        if (l == null) return value;
        return Math.max(l.min, Math.min(l.max, value));
    }

    private record Limits(double min, double max) {}
}
