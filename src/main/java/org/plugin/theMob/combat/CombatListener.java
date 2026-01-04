package org.plugin.theMob.combat;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.combat.visual.DamageNumberService;

import java.util.Map;

public final class CombatListener implements Listener {

    private final TheMob plugin;
    private final DamageCalculator calc;
    private final CombatDebugService debug;
    private final org.plugin.theMob.player.stats.PlayerStatCache cache;
    private final org.plugin.theMob.item.CustomEnchantSystem enchants;

    public CombatListener(
            TheMob plugin,
            org.plugin.theMob.player.stats.PlayerStatCache cache,
            DamageCalculator calc,
            CombatDebugService debug,
            org.plugin.theMob.item.CustomEnchantSystem enchants
    ) {
        this.plugin = plugin;
        this.cache = cache;
        this.calc = calc;
        this.debug = debug;
        this.enchants = enchants;
    }

    // =====================================================
    // DAMAGE EVENT
    // =====================================================
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {

        Player attacker = resolveAttacker(e.getDamager());
        if (attacker == null) return;

        if (!(e.getEntity() instanceof LivingEntity target)) return;

        String gm = attacker.getGameMode().name();
        if (gm.contains("CREATIVE") || gm.contains("SPECTATOR")) return;

        Map<String, Double> stats = cache.get(attacker);
        ConfigurationSection combatCfg =
                plugin.getConfig().getConfigurationSection("combat");

        // ✅ VANILLA BASE DAMAGE (Faust + Waffen)
        double vanillaBase = vanillaWeaponBase(attacker);

        DamageResult r = calc.calculate(
                attacker,
                target,
                vanillaBase,
                stats,
                combatCfg
        );

        // ✅ Vanilla-Pipeline behalten
        e.setDamage(r.finalDamage());

        // ✅ DAMAGE NUMBERS
        boolean showNumbers =
                combatCfg == null || combatCfg.getBoolean("damage-indicator", true);

        if (showNumbers && !(target instanceof Player)) {
            DamageNumberService.spawn(
                    plugin,
                    target,
                    r.finalDamage(),
                    r.crit()
            );
        }

        // ✅ LIFESTEAL
        if (r.lifestealAmount() > 0) {
            Bukkit.getScheduler().runTask(plugin,
                    () -> heal(attacker, r.lifestealAmount()));
        }

        if (enchants != null) {
            enchants.trigger(attacker, target, stats, r.finalDamage());
        }

        if (debug != null && debug.isEnabled(attacker)) {
            debug.send(attacker, r);
        }
    }

    // =====================================================
    // VANILLA WEAPON BASE (V1.5.1)
    // =====================================================
    private double vanillaWeaponBase(Player p) {
        ItemStack it = p.getInventory().getItemInMainHand();

        // 👊 Faust = nie 0 Schaden
        if (it == null || it.getType().isAir()) {
            return 1.0;
        }

        return switch (it.getType()) {

            // SWORDS
            case WOODEN_SWORD -> 4.0;
            case STONE_SWORD -> 5.0;
            case IRON_SWORD -> 6.0;
            case GOLDEN_SWORD -> 4.0;
            case DIAMOND_SWORD -> 7.0;
            case NETHERITE_SWORD -> 8.0;

            // AXES
            case WOODEN_AXE -> 7.0;
            case STONE_AXE -> 9.0;
            case IRON_AXE -> 9.0;
            case GOLDEN_AXE -> 7.0;
            case DIAMOND_AXE -> 9.0;
            case NETHERITE_AXE -> 10.0;

            // PICKAXES
            case WOODEN_PICKAXE -> 2.0;
            case STONE_PICKAXE -> 3.0;
            case IRON_PICKAXE -> 4.0;
            case GOLDEN_PICKAXE -> 2.0;
            case DIAMOND_PICKAXE -> 5.0;
            case NETHERITE_PICKAXE -> 6.0;

            // SHOVELS
            case WOODEN_SHOVEL -> 2.5;
            case STONE_SHOVEL -> 3.5;
            case IRON_SHOVEL -> 4.5;
            case GOLDEN_SHOVEL -> 2.5;
            case DIAMOND_SHOVEL -> 5.5;
            case NETHERITE_SHOVEL -> 6.5;

            // HOES
            case WOODEN_HOE -> 1.5;
            case STONE_HOE -> 2.0;
            case IRON_HOE -> 2.5;
            case GOLDEN_HOE -> 1.5;
            case DIAMOND_HOE -> 3.0;
            case NETHERITE_HOE -> 4.0;

            // SPECIAL
            case TRIDENT -> 8.0;
            case MACE -> 7.0;

            default -> 1.0;
        };
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
