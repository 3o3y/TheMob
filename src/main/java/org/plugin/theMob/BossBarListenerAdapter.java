package org.plugin.theMob;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.plugin.theMob.boss.phase.BossPhaseController;
import org.plugin.theMob.mob.MobManager;
import org.plugin.theMob.ui.MobHealthDisplay;

public final class BossBarListenerAdapter implements Listener {
  private final MobManager mobs;
  private final org.bukkit.plugin.Plugin plugin;
  private final BossPhaseController controller;
  private final MobHealthDisplay healthDisplay;

  public BossBarListenerAdapter(
      org.bukkit.plugin.Plugin plugin,
      MobManager mobs,
      BossPhaseController controller,
      MobHealthDisplay healthDisplay) {
    this.plugin = plugin;
    this.mobs = mobs;
    this.controller = controller;
    this.healthDisplay = healthDisplay;
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onDamage(EntityDamageEvent e) {
    if (e.getEntity() instanceof LivingEntity entity) {
      if (entity.isValid() && !entity.isDead()) {
        org.bukkit.Bukkit.getScheduler()
            .runTask(
                this.plugin,
                () -> {
                  if (entity.isValid() && !entity.isDead()) {
                    this.healthDisplay.update(entity);
                    if (this.mobs.isBoss(entity)) this.controller.onBossUpdate(entity);
                  }
                });
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onHeal(EntityRegainHealthEvent e) {
    if (e.getEntity() instanceof LivingEntity entity) {
      if (entity.isValid() && !entity.isDead()) {
        org.bukkit.Bukkit.getScheduler()
            .runTask(
                this.plugin,
                () -> {
                  if (entity.isValid() && !entity.isDead()) {
                    this.healthDisplay.update(entity);
                    if (this.mobs.isBoss(entity)) this.controller.onBossUpdate(entity);
                  }
                });
      }
    }
  }

  @EventHandler
  public void onDeath(EntityDeathEvent e) {
    LivingEntity var3 = e.getEntity();
    if (var3 instanceof LivingEntity) {
      if (this.mobs.isBoss(var3)) {
        // MobListener owns death processing; this adapter only updates displays.
      }
    }
  }
}
