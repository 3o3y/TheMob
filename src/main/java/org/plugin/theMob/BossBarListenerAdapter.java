package org.plugin.theMob;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.entity.LivingEntity;
import org.plugin.theMob.boss.phase.BossPhaseController;
import org.plugin.theMob.mob.MobManager;
import org.plugin.theMob.ui.MobHealthDisplay;

public final class BossBarListenerAdapter implements Listener {

    private final MobManager mobs;
    private final BossPhaseController controller;
    private final MobHealthDisplay healthDisplay;

    public BossBarListenerAdapter(
            MobManager mobs,
            BossPhaseController controller,
            MobHealthDisplay healthDisplay
    ) {
        this.mobs = mobs;
        this.controller = controller;
        this.healthDisplay = healthDisplay;
    }

    // =====================================================
    // DAMAGE → Health + Boss Phase Update
    // =====================================================
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity entity)) return;
        if (!entity.isValid() || entity.isDead()) return;

        // ✅ Health ist hier bereits korrekt
        healthDisplay.update(entity);

        if (mobs.isBoss(entity)) {
            controller.onBossUpdate(entity);
        }
    }

    // =====================================================
    // HEAL / REGEN
    // =====================================================
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeal(EntityRegainHealthEvent e) {
        if (!(e.getEntity() instanceof LivingEntity entity)) return;
        if (!entity.isValid() || entity.isDead()) return;

        healthDisplay.update(entity);

        if (mobs.isBoss(entity)) {
            controller.onBossUpdate(entity);
        }
    }

    // =====================================================
    // DEATH
    // =====================================================
    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof LivingEntity entity)) return;
        if (!mobs.isBoss(entity)) return;

        controller.onBossDeath(entity);
    }
}
