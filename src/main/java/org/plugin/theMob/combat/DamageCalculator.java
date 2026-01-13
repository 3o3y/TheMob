package org.plugin.theMob.combat;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

public final class DamageCalculator {

    private final Random rnd = new Random();
    private final MobMultiplierService multipliers;

    public DamageCalculator(MobMultiplierService multipliers) {
        this.multipliers = multipliers;
    }

    public DamageResult calculate(
            Player attacker,
            LivingEntity target,
            double vanillaDamage,
            Map<String, Double> stats,
            ConfigurationSection combatCfg
    ) {

        if (target == null || stats == null) {
            return DamageResult.empty(vanillaDamage);
        }

        Map<String, String> dbg = new LinkedHashMap<>();

        // =====================================================
        // BASE DAMAGE (RPG FIRST)
        // =====================================================
        double baseDamage = stats.getOrDefault("damage", 0.0);
        double extraDamage = stats.getOrDefault("extra_damage", 0.0);

        double base;
        if (baseDamage > 0) {
            base = baseDamage + extraDamage;
            dbg.put("BaseSource", "ITEM");
        } else {
            base = vanillaDamage;
            dbg.put("BaseSource", "VANILLA");
        }

        double computedBase = Math.max(0.0, base);
        dbg.put("ComputedBase", trim(computedBase));

        // =====================================================
        // CRIT
        // =====================================================
        double critChance =
                clamp(stats.getOrDefault("crit", 0.0) / 100.0, 0.0, 0.75);

        double critMultiplier = 1.0;
        double rawMult = stats.getOrDefault("crit_multiplier", 1.0);
        if (rawMult >= 1.0 && rawMult <= 3.0) {
            critMultiplier = rawMult;
        }

        boolean crit = rnd.nextDouble() < critChance;
        double afterCrit = crit
                ? computedBase * critMultiplier
                : computedBase;

        dbg.put("Crit", crit ? "YES" : "NO");

        // =====================================================
        // MOB MULTIPLIER
        // =====================================================
        double mobMult = multipliers != null
                ? multipliers.multiplierFor(target, combatCfg, true)
                : 1.0;

        double afterMob = afterCrit * mobMult;

        dbg.put("MobMultiplier", trim(mobMult));
        dbg.put("AfterMob", trim(afterMob));

        // =====================================================
        // DEFENSE
        // =====================================================
        double armor = targetDefense(target);
        double finalDamage = afterMob;

        if (armor > 0) {
            double k = combatCfg != null
                    ? combatCfg.getDouble("defense.k", 200.0)
                    : 200.0;

            double reduction = armor / (armor + k);
            finalDamage *= (1.0 - reduction);

            dbg.put("ArmorReduction", trim(reduction));
        }

        finalDamage = Math.max(0.0, finalDamage);

        // =====================================================
        // LIFESTEAL
        // =====================================================
        double lifestealPct =
                clamp(stats.getOrDefault("lifesteal", 0.0) / 100.0, 0.0, 0.5);

        double lifestealAmount = finalDamage * lifestealPct;

        dbg.put("FinalDamage", trim(finalDamage));
        dbg.put("Lifesteal", trim(lifestealAmount));

        return new DamageResult(
                vanillaDamage,
                computedBase,
                crit,
                critChance,
                critMultiplier,
                mobMult,
                1.0,
                finalDamage,
                lifestealAmount,
                dbg
        );
    }

    // =====================================================
    // HELPERS
    // =====================================================
    private double targetDefense(LivingEntity e) {
        AttributeInstance armor = e.getAttribute(Attribute.ARMOR);
        return armor != null ? armor.getValue() : 0.0;
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private String trim(double d) {
        return d % 1 == 0
                ? String.valueOf((int) d)
                : String.valueOf(Math.round(d * 100.0) / 100.0);
    }
}
