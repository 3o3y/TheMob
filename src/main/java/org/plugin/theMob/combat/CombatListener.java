package org.plugin.theMob.combat;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.combat.visual.DamageNumberService;
import org.plugin.theMob.item.CustomEnchantSystem;
import org.plugin.theMob.player.stats.PlayerStatCache;
import org.plugin.theMob.progression.PlayerProgressionManager;
import org.plugin.theMob.progression.PlayerProgressionState;
import org.plugin.theMob.progression.ProgressionCombatApplier;

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

        if (attacker.getGameMode() == org.bukkit.GameMode.CREATIVE
                || attacker.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;

        Map<String, Double> stats = cache.get(attacker);
        ConfigurationSection combatCfg =
                plugin.getConfig().getConfigurationSection("combat");

        // ✅ USE REAL VANILLA DAMAGE
        double vanillaDamage = e.getDamage();

        DamageResult r = calc.calculate(
                attacker,
                target,
                vanillaDamage,
                stats,
                combatCfg
        );

        double finalDamage = r.finalDamage();

        // ---------- v1.9 Progression ----------
        if (progression != null && progressionCombat != null) {
            PlayerProgressionState state = progression.get(attacker.getUniqueId());
            if (state != null) {
                finalDamage = progressionCombat.applyDamage(state, finalDamage);
            }
        }

        e.setDamage(finalDamage);

        boolean showNumbers =
                combatCfg == null || combatCfg.getBoolean("damage-indicator", true);

        if (showNumbers && !(target instanceof Player)) {
            DamageNumberService.spawn(
                    plugin,
                    target,
                    finalDamage,
                    r.crit()
            );
        }

        if (r.lifestealAmount() > 0) {
            Bukkit.getScheduler().runTask(
                    plugin,
                    () -> heal(attacker, r.lifestealAmount())
            );
        }

        if (enchants != null) {
            enchants.trigger(attacker, target, stats, finalDamage);
        }

        if (debug != null) {
            debug.send(attacker, r);
        }
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private void heal(Player p, double amount) {
        if (p == null || amount <= 0) return;
        p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + amount));
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player p) return p;

        if (damager instanceof Projectile proj &&
                proj.getShooter() instanceof Player p) return p;

        if (damager instanceof Tameable tame &&
                tame.getOwner() instanceof Player p) return p;

        return null;
    }
}
