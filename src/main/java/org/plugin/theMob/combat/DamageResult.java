package org.plugin.theMob.combat;

import java.util.Collections;
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

    public DamageResult {
        // defensive copy + immutability
        debug = debug != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(debug))
                : Collections.emptyMap();
    }

    public static DamageResult empty(double vanillaBase) {
        double v = Math.max(0.0, vanillaBase);
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
                Collections.emptyMap()
        );
    }
}
