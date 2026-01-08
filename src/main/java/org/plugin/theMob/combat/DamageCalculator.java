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
        if (attacker == null || target == null || stats == null) {
            return DamageResult.empty(vanillaDamage);
        }

        Map<String, String> dbg = new LinkedHashMap<>();

        // =====================================================
        // BASE (VANILLA FIRST)
        // =====================================================
        double base = vanillaDamage;
        double add =
                stats.getOrDefault("damage", 0.0) +
                        stats.getOrDefault("extra_damage", 0.0);

        boolean overrideVanilla =
                combatCfg != null && combatCfg.getBoolean("override_vanilla", false);

        if (overrideVanilla) base = 0.0;

        double computedBase = Math.max(0.0, base + add);

        dbg.put("VanillaDamage", trim(vanillaDamage));
        dbg.put("AddDamage", trim(add));
        dbg.put("ComputedBase", trim(computedBase));

        // =====================================================
        // CRIT
        // =====================================================
        double critChanceRaw = stats.getOrDefault("crit", 0.0);
        double critChance = clamp(normalizePercent(critChanceRaw), 0.0,
                combatCfg != null ? combatCfg.getDouble("crit.max_chance", 0.75) : 0.75);

        double critMultRaw = stats.getOrDefault("crit_multiplier", 0.0);
        double critMultiplier = critMultRaw > 0
                ? normalizeCritMultiplier(critMultRaw)
                : 1.0; // IMPORTANT: no override if not defined

        boolean crit = rnd.nextDouble() < critChance;
        double afterCrit = crit ? computedBase * critMultiplier : computedBase;

        dbg.put("CritChance", trim(critChance));
        dbg.put("CritMultiplier", trim(critMultiplier));
        dbg.put("CritRolled", String.valueOf(crit));
        dbg.put("AfterCrit", trim(afterCrit));

        // =====================================================
        // MOB MULTIPLIER
        // =====================================================
        double mobMult = multipliers != null
                ? multipliers.multiplierFor(target, combatCfg)
                : 1.0;

        mobMult = clamp(mobMult, 0.05, 50.0);
        double afterMob = afterCrit * mobMult;

        dbg.put("MobMultiplier", trim(mobMult));
        dbg.put("AfterMob", trim(afterMob));

        // =====================================================
        // CONDITIONAL
        // =====================================================
        double conditional = 1.0;

        double hpPct = healthPct(target);
        if (hpPct >= 0 && hpPct <= combatCfg.getDouble("conditional.execute.threshold", 0.20)) {
            conditional *= combatCfg.getDouble("conditional.execute.multiplier", 1.25);
        }

        double afterConditional = afterMob * conditional;
        dbg.put("ConditionalMultiplier", trim(conditional));
        dbg.put("AfterConditional", trim(afterConditional));

        // =====================================================
        // DEFENSE (TARGET BASED!)
        // =====================================================
        double defense = targetDefense(target);
        double afterDefense = afterConditional;

        if (defense > 0) {
            double k = combatCfg != null ? combatCfg.getDouble("defense.k", 200.0) : 200.0;
            double maxRed = combatCfg != null ? combatCfg.getDouble("defense.max_reduction", 0.60) : 0.60;
            double reduction = clamp(defense / (defense + k), 0.0, maxRed);
            afterDefense *= (1.0 - reduction);
            dbg.put("DefenseReduction", trim(reduction));
        }

        dbg.put("AfterDefense", trim(afterDefense));

        // =====================================================
        // LIFESTEAL (FINAL DAMAGE BASED)
        // =====================================================
        double lifestealPct = clamp(
                normalizePercent(stats.getOrDefault("lifesteal", 0.0)),
                0.0,
                combatCfg != null ? combatCfg.getDouble("lifesteal.max", 0.35) : 0.35
        );

        double finalDamage = Math.max(0.0, afterDefense);
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
                conditional,
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

    private double normalizePercent(double v) {
        return v > 1.0 ? v / 100.0 : Math.max(0.0, v);
    }

    private double normalizeCritMultiplier(double v) {
        return v >= 1.0 && v <= 10.0 ? v : 1.0 + (v / 100.0);
    }

    private double healthPct(LivingEntity e) {
        AttributeInstance max = e.getAttribute(Attribute.MAX_HEALTH);
        if (max == null || max.getValue() <= 0) return -1;
        return e.getHealth() / max.getValue();
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private String trim(double d) {
        return d % 1 == 0 ? String.valueOf((int) d) : String.valueOf(Math.round(d * 100.0) / 100.0);
    }
}
