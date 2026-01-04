package org.plugin.theMob.combat;

import java.util.LinkedHashMap;
import java.util.Map;

public record DamageResult(
        double vanillaBase,
        double computedBase,
        boolean crit,
        double critChance,
        double critMultiplier,
        double mobMultiplier,
        double conditionalMultiplier,
        double finalDamage,
        double lifestealAmount,
        Map<String, String> debug
) {
    public static DamageResult empty(double vanillaBase) {
        double v = Math.max(1.0, vanillaBase);
        return new DamageResult(
                v,
                v,
                false,
                0.0,
                1.0,
                1.0,
                1.0,
                v,
                0.0,
                new LinkedHashMap<>()
        );
    }
}
