// src/main/java/org/plugin/theMob/BossBarListenerAdapter.java
package org.plugin.theMob;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.plugin.theMob.boss.phase.BossPhaseController;
import org.plugin.theMob.mob.MobManager;

public final class BossBarListenerAdapter implements Listener {

    private final MobManager mobs;
    private final BossPhaseController controller;
    public BossBarListenerAdapter(
            MobManager mobs,
            BossPhaseController controller
    ) {
        this.mobs = mobs;
        this.controller = controller;
    }
// DAMAGE → PHASE UPDATE
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity boss)) return;
        if (!mobs.isBoss(boss)) return;
        controller.onBossUpdate(boss);
    }
// HEAL → PHASE UPDATE
    @EventHandler(ignoreCancelled = true)
    public void onHeal(EntityRegainHealthEvent e) {
        if (!(e.getEntity() instanceof LivingEntity boss)) return;
        if (!mobs.isBoss(boss)) return;
        controller.onBossUpdate(boss);
    }
// DEATH → CLEANUP
    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof LivingEntity boss)) return;
        if (!mobs.isBoss(boss)) return;
        controller.onBossDeath(boss);
    }
}
