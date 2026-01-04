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
            return DamageResult.empty(Math.max(1.0, vanillaBaseDamage));
        }

        Map<String, String> dbg = new LinkedHashMap<>();

        // =====================================================
        // BASE (V1.5.1)
        // - vanillaBaseDamage kommt aus CombatListener (Faust/Waffe)
        // - plus custom stats vom Item-System
        // - niemals unter 1.0 (damit immer Damage + DamageNumbers)
        // =====================================================
        double vanilla = Math.max(1.0, vanillaBaseDamage);

        double dmgStat = stats.getOrDefault("damage", 0.0);
        double extra = stats.getOrDefault("extra_damage", 0.0);

        double computedBase = Math.max(1.0, vanilla + dmgStat + extra);

        dbg.put("VanillaWeapon", trim(vanilla));
        dbg.put("DamageStat", trim(dmgStat));
        dbg.put("ExtraDamage", trim(extra));
        dbg.put("ComputedBase", trim(computedBase));

        // =====================================================
        // CRIT
        // =====================================================
        double critChanceRaw = stats.getOrDefault("crit_chance", 0.0);
        double critChance = normalizePercent(critChanceRaw);

        double maxCritChance = combatCfg != null ? combatCfg.getDouble("crit.max_chance", 1.0) : 1.0;
        critChance = clamp(critChance, 0.0, maxCritChance);

        double critMultRaw = stats.getOrDefault("crit_multiplier", 1.0);
        double critMultiplier = normalizeCritMultiplier(critMultRaw);

        double maxCritMult = combatCfg != null ? combatCfg.getDouble("crit.max_multiplier", 999.0) : 999.0;
        critMultiplier = clamp(critMultiplier, 1.0, maxCritMult);

        boolean crit = rnd.nextDouble() < critChance;
        double afterCrit = crit ? (computedBase * critMultiplier) : computedBase;

        dbg.put("CritChanceRaw", trim(critChanceRaw));
        dbg.put("CritChanceUsed", trim(critChance));
        dbg.put("CritMultiplierRaw", trim(critMultRaw));
        dbg.put("CritMultiplierUsed", trim(critMultiplier));
        dbg.put("CritRolled", String.valueOf(crit));
        dbg.put("AfterCrit", trim(afterCrit));

        // =====================================================
        // MOB MULTIPLIER
        // =====================================================
        double mobMult = multipliers != null ? multipliers.multiplierFor(target, combatCfg) : 1.0;
        mobMult = clamp(mobMult, 0.05, 50.0);

        double afterMob = afterCrit * mobMult;

        String mobId = multipliers != null ? multipliers.resolveMobId(target) : null;
        dbg.put("MobId", mobId == null ? "-" : mobId);
        dbg.put("MobMultiplier", trim(mobMult));
        dbg.put("AfterMob", trim(afterMob));

        // =====================================================
        // CONDITIONALS (execute/enrage)
        // =====================================================
        double conditionalMult = 1.0;

        double execThreshold = combatCfg != null ? combatCfg.getDouble("conditional.execute.threshold", 0.20) : 0.20;
        double execMult = combatCfg != null ? combatCfg.getDouble("conditional.execute.multiplier", 1.25) : 1.25;

        double hpPct = healthPct(target);
        boolean execute = hpPct > 0 && hpPct <= execThreshold;
        if (execute) conditionalMult *= execMult;

        boolean enraged = target.getScoreboardTags().contains("themob_enraged");
        double enrageMult = combatCfg != null ? combatCfg.getDouble("conditional.enrage.multiplier", 1.15) : 1.15;
        if (enraged) conditionalMult *= enrageMult;

        double afterConditional = afterMob * conditionalMult;

        dbg.put("TargetHpPct", trim(hpPct));
        dbg.put("ExecuteActive", String.valueOf(execute));
        dbg.put("EnrageActive", String.valueOf(enraged));
        dbg.put("ConditionalMultiplier", trim(conditionalMult));
        dbg.put("AfterConditional", trim(afterConditional));

        // =====================================================
        // DEFENSE (optional)
        // =====================================================
        boolean defenseEnabled = combatCfg == null || combatCfg.getBoolean("defense.enabled", true);
        double defense = stats.getOrDefault("defense", 0.0);

        double afterDefense = afterConditional;
        if (defenseEnabled && defense > 0) {
            double maxReduction = combatCfg != null ? combatCfg.getDouble("defense.max_reduction", 0.60) : 0.60;
            double k = combatCfg != null ? combatCfg.getDouble("defense.k", 200.0) : 200.0;
            double reduction = clamp(defense / (defense + k), 0.0, maxReduction);
            afterDefense = afterConditional * (1.0 - reduction);

            dbg.put("Defense", trim(defense));
            dbg.put("DefenseReduction", trim(reduction));
        } else {
            dbg.put("Defense", trim(defense));
            dbg.put("DefenseReduction", "0");
        }

        // =====================================================
        // FINAL DAMAGE (V1.5.1 FIX)
        // - nie 0 (sonst keine DamageNumbers / kein sichtbarer Hit)
        // =====================================================
        double finalDamage = Math.max(1.0, afterDefense);
        dbg.put("FinalDamage", trim(finalDamage));

        // =====================================================
        // LIFESTEAL
        // - basierend auf FINAL DAMAGE (stabil, konsistent)
        // =====================================================
        double lifestealRaw = stats.getOrDefault("lifesteal", 0.0);
        double lifestealPct = normalizePercent(lifestealRaw);

        double lifestealMax = combatCfg != null ? combatCfg.getDouble("lifesteal.max", 0.35) : 0.35;
        lifestealPct = clamp(lifestealPct, 0.0, lifestealMax);

        double lifestealAmount = finalDamage * lifestealPct;

        dbg.put("LifestealRaw", trim(lifestealRaw));
        dbg.put("LifestealPctUsed", trim(lifestealPct));
        dbg.put("LifestealAmount", trim(lifestealAmount));

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

    private double normalizePercent(double v) {
        if (v <= 0) return 0.0;
        if (v > 1.0) return v / 100.0; // 90 -> 0.90
        return v;
    }

    private double normalizeCritMultiplier(double v) {
        if (v <= 0) return 1.0;

        // multiplier already (4.3)
        if (v >= 1.0 && v <= 50.0) return v;

        // percent total (330 -> 3.3x)
        if (v > 50.0) return v / 100.0;

        // delta (0.5 -> +50% => 1.5x)
        if (v > 0.0 && v < 1.0) return 1.0 + v;

        return 1.0;
    }

    private double healthPct(LivingEntity e) {
        AttributeInstance max = e.getAttribute(Attribute.MAX_HEALTH);
        double mh = (max != null ? max.getValue() : 0.0);
        if (mh <= 0) return -1;
        return clamp(e.getHealth() / mh, 0.0, 1.0);
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private String trim(double d) {
        return (d % 1 == 0) ? String.valueOf((int) d) : String.valueOf(Math.round(d * 100.0) / 100.0);
    }
}
