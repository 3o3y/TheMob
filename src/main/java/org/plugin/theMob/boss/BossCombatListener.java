package org.plugin.theMob.boss;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.Plugin;
import org.plugin.theMob.boss.phase.BossPhaseController;
import org.plugin.theMob.combat.visual.DamageNumberService;
import org.plugin.theMob.mob.MobManager;

import java.util.Random;

public final class BossCombatListener implements Listener {

    private final Plugin plugin;
    private final MobManager mobs;
    private final BossPhaseController phases;
    private final Random rnd = new Random();

    public BossCombatListener(Plugin plugin, MobManager mobs, BossPhaseController phases) {
        this.plugin = plugin;
        this.mobs = mobs;
        this.phases = phases;
    }

    @EventHandler(ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent event) {

        if (!(event.getDamager() instanceof LivingEntity boss)) return;
        if (!mobs.isBoss(boss)) return;
        if (!boss.isValid() || boss.isDead()) return;

        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (target instanceof Player) return;

        BossPhase phase = phases.currentPhase(boss);
        if (phase == null) return;

        var combat = phase.cfg().getConfigurationSection("combat");
        if (combat == null) return;

        double damage = event.getDamage();

        // =====================================================
        // DAMAGE MULTIPLIER
        // =====================================================

        double dealMul = combat.getDouble("deal-damage-multiplier", 1.0);
        if (dealMul != 1.0) {
            damage *= dealMul;
        }

        // =====================================================
        // CRITICAL HIT
        // =====================================================

        boolean crit = false;
        int critChance = combat.getInt("crit-chance", 0);
        double critMul = combat.getDouble("crit-multiplier", 0.0);

        if (critChance > 0 && critMul > 0.0 && rnd.nextInt(100) < critChance) {
            damage *= (1.0 + critMul);
            crit = true;
        }

        if (damage <= 0.0) return;

        // =====================================================
        // VISUAL DAMAGE NUMBER
        // =====================================================

        DamageNumberService.spawn(
                plugin,
                target,
                damage,
                crit
        );

        event.setDamage(damage);

        // =====================================================
        // LIFESTEAL
        // =====================================================

        double lifesteal = combat.getDouble("lifesteal", 0.0);
        if (lifesteal > 0.0) {
            double heal = damage * (lifesteal / 100.0);

            AttributeInstance maxHp = boss.getAttribute(Attribute.MAX_HEALTH);
            if (maxHp != null) {
                double newHp = Math.min(
                        boss.getHealth() + heal,
                        maxHp.getValue()
                );
                boss.setHealth(newHp);
            }
        }
    }
}
