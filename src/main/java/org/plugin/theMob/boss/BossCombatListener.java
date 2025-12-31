package org.plugin.theMob.boss;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.plugin.theMob.mob.MobManager;
import org.plugin.theMob.boss.phase.BossPhaseController;

import java.util.Random;

public final class BossCombatListener implements Listener {

    private final MobManager mobs;
    private final BossPhaseController phases;
    private final Random rnd = new Random();

    public BossCombatListener(MobManager mobs, BossPhaseController phases) {
        this.mobs = mobs;
        this.phases = phases;
    }
    @EventHandler(ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LivingEntity boss)) return;
        if (!mobs.isBoss(boss)) return;

        BossPhase phase = phases.currentPhase(boss);
        if (phase == null) return;

        var combat = phase.cfg().getConfigurationSection("combat");
        if (combat == null) return;

        double damage = event.getDamage();

        double dealMul = combat.getDouble("deal-damage-multiplier", 1.0);
        damage *= dealMul;

        int critChance = combat.getInt("crit-chance", 0);
        double critMul = combat.getDouble("crit-multiplier", 0);

        if (critChance > 0 && rnd.nextInt(100) < critChance) {
            damage *= (1.0 + critMul);
        }

        event.setDamage(damage);

        double lifesteal = combat.getDouble("lifesteal", 0);
        if (lifesteal > 0) {
            double heal = damage * (lifesteal / 100.0);
            boss.setHealth(Math.min(boss.getHealth() + heal, boss.getMaxHealth()));
        }
    }
}
