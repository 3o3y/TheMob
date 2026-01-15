package org.plugin.theMob.combat;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
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

import java.util.*;

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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {

        Player attacker = resolveAttacker(e.getDamager());
        if (attacker == null) return;

        if (!(e.getEntity() instanceof LivingEntity target)) return;

        if (attacker.getGameMode() == GameMode.CREATIVE
                || attacker.getGameMode() == GameMode.SPECTATOR) return;

        // -----------------------------
        // STATS
        // -----------------------------
        Map<String, Double> mergedStats = merge(
                cache != null ? cache.get(attacker) : Map.of(),
                collectItemStats(attacker)
        );

        ConfigurationSection combatCfg =
                plugin.getConfig().getConfigurationSection("combat");

        double vanillaDamage = e.getDamage();

        DamageResult result = calc.calculate(
                attacker,
                target,
                vanillaDamage,
                mergedStats,
                combatCfg
        );

        double finalDamage = Math.max(0.0, result.finalDamage());
        double health = Math.max(0.0, target.getHealth());

        // -----------------------------
        // APPLY DAMAGE + HARD KILL
        // -----------------------------
        if (finalDamage >= health && health > 0.0) {
            // Apply lethal damage first (keeps vanilla death pipeline as much as possible)
            e.setDamage(health);

            // Hard-kill next tick (fixes "unkillable boss at 1 HP" edge cases)
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (target.isValid() && !target.isDead()) {
                    try {
                        target.setHealth(0.0);
                    } catch (Throwable ignored) {
                        // some entities/plugins may block setHealth
                    }
                }
            });
        } else {
            e.setDamage(finalDamage);
        }

        // -----------------------------
        // DAMAGE NUMBERS
        // -----------------------------
        if (shouldShowDamageNumbers(attacker, combatCfg) && !(target instanceof Player)) {
            DamageNumberService.spawn(
                    plugin,
                    target,
                    finalDamage,
                    result.crit()
            );
        }

        // -----------------------------
        // LIFESTEAL (POST)
        // -----------------------------
        if (result.lifestealAmount() > 0) {
            double heal = result.lifestealAmount();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!attacker.isOnline()) return;
                double maxHp = attacker.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null
                        ? attacker.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()
                        : attacker.getMaxHealth();

                attacker.setHealth(Math.min(maxHp, attacker.getHealth() + heal));

                if (shouldShowDamageNumbers(attacker, combatCfg)) {
                    DamageNumberService.spawnHeal(plugin, attacker, heal);
                }
            });
        }

        // -----------------------------
        // ITEM ENCHANTS
        // -----------------------------
        if (enchants != null) {
            enchants.trigger(attacker, target, mergedStats, finalDamage);
        }

        // -----------------------------
        // DEBUG
        // -----------------------------
        if (debug != null) {
            debug.send(attacker, result);
        }
    }

    // =====================================================
    // DAMAGE INDICATOR CONFIG CHECK
    // =====================================================
    private boolean shouldShowDamageNumbers(Player p, ConfigurationSection combatCfg) {
        if (combatCfg == null) return false;

        ConfigurationSection sec = combatCfg.getConfigurationSection("damage-indicator");
        if (sec == null) return false;

        if (!sec.getBoolean("enabled", false)) return false;

        List<String> showTo = sec.getStringList("show-to");
        if (showTo == null || showTo.isEmpty()) return false;

        if (p.isOp() && showTo.contains("op")) return true;
        return showTo.contains("admin") && p.hasPermission("themob.admin");
    }

    // =====================================================
    // HELPERS
    // =====================================================
    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player p) return p;

        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;

        if (damager instanceof Tameable tame && tame.getOwner() instanceof Player p) return p;

        return null;
    }

    private Map<String, Double> collectItemStats(Player p) {
        if (enchants == null) return Map.of();

        ItemStack item = p.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) return Map.of();

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return Map.of();

        return enchants.collect(meta);
    }

    private Map<String, Double> merge(Map<String, Double> base, Map<String, Double> add) {
        Map<String, Double> out = new HashMap<>();
        if (base != null) out.putAll(base);

        if (add != null) {
            add.forEach((k, v) -> out.merge(k, v, Double::sum));
        }
        return out;
    }
}
