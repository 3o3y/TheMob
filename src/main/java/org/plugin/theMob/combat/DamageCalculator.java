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
            double vanillaBaseDamage,
            Map<String, Double> stats,
            ConfigurationSection combatCfg
    ) {
        if (attacker == null || target == null || stats == null) {
            return DamageResult.empty(vanillaBaseDamage);
        }

        Map<String, String> dbg = new LinkedHashMap<>();

        // ---- Base ----
        double base = vanillaBaseDamage;
        double addDamage =
                stats.getOrDefault("damage", 0.0) +
                        stats.getOrDefault("extra_damage", 0.0);

        boolean overrideVanilla =
                combatCfg != null && combatCfg.getBoolean("override_vanilla", false);
        if (overrideVanilla) base = 0.0;

        double computedBase = Math.max(0.0, base + addDamage);

        dbg.put("VanillaBase", trim(vanillaBaseDamage));
        dbg.put("AddDamage", trim(addDamage));
        dbg.put("ComputedBase", trim(computedBase));

        // ---- Crit Chance ----
        double critChanceRaw =
                stats.containsKey("crit_chance")
                        ? stats.get("crit_chance")
                        : stats.getOrDefault("crit", 0.0);

        double critChance = normalizePercent(critChanceRaw);

        double maxCritChance =
                combatCfg != null ? combatCfg.getDouble("crit.max_chance", 0.75) : 0.75;
        critChance = clamp(critChance, 0.0, maxCritChance);

        // ---- Crit Multiplier (FIXED + dual format support) ----
        double critMultRaw =
                stats.containsKey("crit_multiplier")
                        ? stats.get("crit_multiplier")
                        : stats.getOrDefault("crit_mult", 0.0);

        double critMultiplier = normalizeCritMultiplier(critMultRaw);

        double maxCritMult =
                combatCfg != null ? combatCfg.getDouble("crit.max_multiplier", 10.0) : 10.0;
        critMultiplier = clamp(critMultiplier, 1.0, maxCritMult);

        boolean crit = rnd.nextDouble() < critChance;
        double afterCrit = crit ? (computedBase * critMultiplier) : computedBase;

        dbg.put("CritChanceRaw", trim(critChanceRaw));
        dbg.put("CritChance", trim(critChance));
        dbg.put("CritMultiplierRaw", trim(critMultRaw));
        dbg.put("CritMultiplier", trim(critMultiplier));
        dbg.put("CritRolled", String.valueOf(crit));
        dbg.put("AfterCrit", trim(afterCrit));

        // ---- Mob Multiplier ----
        double mobMult = multipliers != null
                ? multipliers.multiplierFor(target, combatCfg)
                : 1.0;
        mobMult = clamp(mobMult, 0.05, 50.0);

        double afterMob = afterCrit * mobMult;

        dbg.put("MobMultiplier", trim(mobMult));
        dbg.put("AfterMob", trim(afterMob));

        // ---- Conditional ----
        double conditionalMult = 1.0;

        double hpPct = healthPct(target);
        double execThreshold =
                combatCfg != null ? combatCfg.getDouble("conditional.execute.threshold", 0.20) : 0.20;

        if (hpPct > 0 && hpPct <= execThreshold) {
            conditionalMult *=
                    combatCfg != null
                            ? combatCfg.getDouble("conditional.execute.multiplier", 1.25)
                            : 1.25;
        }

        if (target.getScoreboardTags().contains("themob_enraged")) {
            conditionalMult *=
                    combatCfg != null
                            ? combatCfg.getDouble("conditional.enrage.multiplier", 1.15)
                            : 1.15;
        }

        double afterConditional = afterMob * conditionalMult;

        dbg.put("ConditionalMultiplier", trim(conditionalMult));
        dbg.put("AfterConditional", trim(afterConditional));

        // ---- Defense ----
        double defense = stats.getOrDefault("defense", 0.0);
        double afterDefense = afterConditional;

        if (defense > 0) {
            double k =
                    combatCfg != null ? combatCfg.getDouble("defense.k", 200.0) : 200.0;
            double maxReduction =
                    combatCfg != null ? combatCfg.getDouble("defense.max_reduction", 0.60) : 0.60;

            double reduction = clamp(defense / (defense + k), 0.0, maxReduction);
            afterDefense *= (1.0 - reduction);

            dbg.put("Defense", trim(defense));
            dbg.put("DefenseReduction", trim(reduction));
        } else {
            dbg.put("Defense", trim(defense));
            dbg.put("DefenseReduction", "0");
        }

        dbg.put("AfterDefense", trim(afterDefense));

        // ---- Lifesteal ----
        double lifestealRaw = stats.getOrDefault("lifesteal", 0.0);
        double lifestealPct = normalizePercent(lifestealRaw);
        double lifestealMax =
                combatCfg != null ? combatCfg.getDouble("lifesteal.max", 0.35) : 0.35;
        lifestealPct = clamp(lifestealPct, 0.0, lifestealMax);

        double lifestealAmount = afterDefense * lifestealPct;

        dbg.put("LifestealRaw", trim(lifestealRaw));
        dbg.put("LifestealPct", trim(lifestealPct));
        dbg.put("LifestealAmount", trim(lifestealAmount));

        double finalDamage = Math.max(0.0, afterDefense);
        dbg.put("FinalDamage", trim(finalDamage));

        return new DamageResult(
                vanillaBaseDamage,
                computedBase,
                crit,
                critChance,
                critMultiplier,
                mobMult,
                conditionalMult,
                finalDamage,
                lifestealAmount,
                dbg
        );
    }

    // ================= HELPERS =================

    private double normalizePercent(double v) {
        if (v <= 0) return 0.0;
        if (v > 1.0) return v / 100.0;
        return v;
    }

    /**
     * Supports both formats:
     *  - 4   => 4.0x (direct multiplier)
     *  - 300 => 4.0x (percent bonus: 1 + 300/100)
     */
    private double normalizeCritMultiplier(double v) {
        if (v <= 0) return 1.5;

        // Direct multiplier style (e.g. 2, 3, 4, 5)
        if (v >= 1.0 && v <= 10.0) return v;

        // Percent bonus style (e.g. 50 -> 1.5x, 300 -> 4.0x)
        return 1.0 + (v / 100.0);
    }

    private double healthPct(LivingEntity e) {
        AttributeInstance max = e.getAttribute(Attribute.MAX_HEALTH);
        if (max == null || max.getValue() <= 0) return -1;
        return clamp(e.getHealth() / max.getValue(), 0.0, 1.0);
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
