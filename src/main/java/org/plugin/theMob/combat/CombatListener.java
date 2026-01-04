package org.plugin.theMob.combat;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.Plugin;
import org.plugin.theMob.TheMob;

import java.util.Map;

public final class CombatListener implements Listener {

    private final TheMob plugin;
    private final DamageCalculator calc;
    private final CombatDebugService debug;
    private final org.plugin.theMob.player.stats.PlayerStatCache cache;
    private final org.plugin.theMob.item.CustomEnchantSystem enchants;

    public CombatListener(TheMob plugin,
                          org.plugin.theMob.player.stats.PlayerStatCache cache,
                          DamageCalculator calc,
                          CombatDebugService debug,
                          org.plugin.theMob.item.CustomEnchantSystem enchants) {
        this.plugin = plugin;
        this.cache = cache;
        this.calc = calc;
        this.debug = debug;
        this.enchants = enchants;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Player attacker = resolveAttacker(e.getDamager());
        if (attacker == null) return;

        if (!(e.getEntity() instanceof LivingEntity target)) return;

        // Optional: ignore creative/spectator
        if (attacker.getGameMode().name().contains("CREATIVE")
                || attacker.getGameMode().name().contains("SPECTATOR")) {
            return;
        }

        // Load stats
        Map<String, Double> stats = cache.get(attacker);

        // Read combat config section (best-effort)
        ConfigurationSection combatCfg = null;
        try {
            combatCfg = plugin.getConfig().getConfigurationSection("combat");
        } catch (Exception ignored) {}

        DamageResult r = calc.calculate(attacker, target, e.getDamage(), stats, combatCfg);

        // Apply final damage
        e.setDamage(r.finalDamage());
// ✅ Damage numbers nur über Mobs anzeigen
        if (target instanceof LivingEntity && !(target instanceof Player)) {
            org.plugin.theMob.combat.visual.DamageNumberService.spawn(
                    plugin,
                    target,
                    r.finalDamage(),
                    r.crit()
            );

        }

        // Lifesteal (next tick to avoid fighting with vanilla health changes)
        if (r.lifestealAmount() > 0) {
            double heal = r.lifestealAmount();
            Bukkit.getScheduler().runTask(plugin, () -> heal(attacker, heal));
        }

        // Custom enchant triggers (after final damage decided)
        if (enchants != null) {
            enchants.trigger(attacker, target, stats, r.finalDamage());
        }

        // Debug output
        if (debug != null && debug.isEnabled(attacker)) {
            debug.send(attacker, r);
        }
    }

    private void heal(Player p, double amount) {
        if (p == null || amount <= 0) return;
        double max = p.getMaxHealth();
        double now = p.getHealth();
        p.setHealth(Math.min(max, now + amount));
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player p) return p;

        if (damager instanceof Projectile proj) {
            Object shooter = proj.getShooter();
            if (shooter instanceof Player p) return p;
        }

        if (damager instanceof Tameable tame && tame.getOwner() instanceof Player p) {
            return p;
        }

        return null;
    }
}
