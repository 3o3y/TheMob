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
        return new DamageResult(
                vanillaBase,
                vanillaBase,
                false,
                0.0,
                1.0,
                1.0,
                1.0,
                vanillaBase,
                0.0,
                new LinkedHashMap<>()
        );
    }
}
