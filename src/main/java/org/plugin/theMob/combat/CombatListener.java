package org.plugin.theMob.combat;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.combat.visual.DamageNumberService;
import org.plugin.theMob.item.CustomEnchantSystem;
import org.plugin.theMob.player.stats.PlayerStatCache;
import org.plugin.theMob.progression.PlayerProgressionManager;
import org.plugin.theMob.progression.PlayerProgressionState;
import org.plugin.theMob.progression.ProgressionCombatApplier;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Collections;


import java.util.HashMap;
import java.util.Map;

public final class CombatListener implements Listener {

    private final TheMob plugin;
    private final DamageCalculator calc;
    private final CombatDebugService debug;
    private final PlayerStatCache cache;
    private final CustomEnchantSystem enchants;

    private final PlayerProgressionManager progression;
    private final ProgressionCombatApplier progressionCombat;

    public CombatListener(
            TheMob plugin,
            PlayerStatCache cache,
            DamageCalculator calc,
            CombatDebugService debug,
            CustomEnchantSystem enchants,
            PlayerProgressionManager progression,
            ProgressionCombatApplier progressionCombat
    ) {
        this.plugin = plugin;
        this.cache = cache;
        this.calc = calc;
        this.debug = debug;
        this.enchants = enchants;
        this.progression = progression;
        this.progressionCombat = progressionCombat;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {

        Player attacker = resolveAttacker(e.getDamager());
        if (attacker == null) return;

        if (!(e.getEntity() instanceof LivingEntity target)) return;

        if (attacker.getGameMode() == GameMode.CREATIVE
                || attacker.getGameMode() == GameMode.SPECTATOR) return;

        // =====================================
        // STAT COLLECTION
        // =====================================

        Map<String, Double> playerStats = cache.get(attacker);
        Map<String, Double> itemStats = collectItemStats(attacker);

        Map<String, Double> mergedStats = merge(playerStats, itemStats);

        ConfigurationSection combatCfg =
                plugin.getConfig().getConfigurationSection("combat");

        // =====================================
        // VANILLA BASE DAMAGE
        // =====================================
        double vanillaDamage = e.getDamage();

        DamageResult result = calc.calculate(
                attacker,
                target,
                vanillaDamage,
                mergedStats,
                combatCfg
        );

        double finalDamage = result.finalDamage();

        double health = target.getHealth();

        if (finalDamage >= health) {
            // Erzwinge Vanilla-Kill
            e.setDamage(health + 0.01);
        } else {
            e.setDamage(finalDamage);
        }


        // =====================================
        // PROGRESSION SCALING (POST)
        // =====================================
        if (progression != null && progressionCombat != null) {
            PlayerProgressionState state =
                    progression.get(attacker.getUniqueId());
            if (state != null) {
                finalDamage =
                        progressionCombat.applyDamage(state, finalDamage);
            }
        }

        e.setDamage(finalDamage);

        // =====================================
        // DAMAGE NUMBERS
        // =====================================
        boolean showNumbers =
                combatCfg == null || combatCfg.getBoolean(
                        "damage-indicator", true
                );

        if (showNumbers && !(target instanceof Player)) {
            DamageNumberService.spawn(
                    plugin,
                    target,
                    finalDamage,
                    result.crit()
            );
        }
        if (result.lifestealAmount() > 0) {
            double heal = result.lifestealAmount();
            Bukkit.getScheduler().runTask(plugin, () -> {
                attacker.setHealth(
                        Math.min(attacker.getMaxHealth(),
                                attacker.getHealth() + heal)
                );
                DamageNumberService.spawnHeal(plugin, attacker, heal);
            });
        }

        // =====================================
        // ON-HIT EFFECTS (ITEM ONLY)
        // =====================================
        if (enchants != null && itemStats != null && !itemStats.isEmpty()) {
            enchants.trigger(attacker, target, itemStats, finalDamage);
        }

        // =====================================
        // DEBUG
        // =====================================
        if (debug != null) {
            debug.send(attacker, result);
        }
    }

    // =====================================================
// STAT MERGE
// =====================================================
    private static final Set<String> NO_SUM_KEYS =
            Collections.singleton("crit_multiplier");

    private Map<String, Double> merge(
            Map<String, Double> base,
            Map<String, Double> add
    ) {
        Map<String, Double> out = new HashMap<>();
        if (base != null) out.putAll(base);

        if (add != null) {
            add.forEach((k, v) -> {
                if (NO_SUM_KEYS.contains(k)) {
                    out.put(k, v);
                } else {
                    out.merge(k, v, Double::sum);
                }
            });
        }
        return out;
    }



    // =====================================================
    // ITEM STATS
    // =====================================================
    private Map<String, Double> collectItemStats(Player p) {
        if (enchants == null) return Map.of();

        ItemStack item = p.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) return Map.of();

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return Map.of();

        return enchants.collect(meta);
    }

    // =====================================================
    // HELPERS
    // =====================================================
    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player p) return p;

        if (damager instanceof Projectile proj &&
                proj.getShooter() instanceof Player p) return p;

        if (damager instanceof Tameable tame &&
                tame.getOwner() instanceof Player p) return p;

        return null;
    }
}
