package org.plugin.theMob.boss;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.plugin.theMob.boss.phase.BossPhaseController;
import org.plugin.theMob.mob.MobManager;

public final class BossImmunityListener implements Listener {

    private final MobManager mobs;
    private final BossPhaseController phases;

    public BossImmunityListener(MobManager mobs, BossPhaseController phases) {
        this.mobs = mobs;
        this.phases = phases;
    }
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity boss)) return;
        if (!mobs.isBoss(boss)) return;

        BossPhase phase = phases.currentPhase(boss);
        if (phase == null) return;

        var imm = phase.cfg().getConfigurationSection("immunities");
        if (imm == null) return;

        EntityDamageEvent.DamageCause c = event.getCause();

        if (c == EntityDamageEvent.DamageCause.FIRE && imm.getBoolean("fire")) event.setCancelled(true);
        if (c == EntityDamageEvent.DamageCause.FALL && imm.getBoolean("fall")) event.setCancelled(true);
        if (c == EntityDamageEvent.DamageCause.DROWNING && imm.getBoolean("drowning")) event.setCancelled(true);
        if (c == EntityDamageEvent.DamageCause.POISON && imm.getBoolean("poison")) event.setCancelled(true);
        if (c == EntityDamageEvent.DamageCause.LIGHTNING && imm.getBoolean("lightning")) event.setCancelled(true);
        if (c == EntityDamageEvent.DamageCause.PROJECTILE && imm.getBoolean("projectile")) event.setCancelled(true);
        if (c == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION && imm.getBoolean("explosion")) event.setCancelled(true);
    }
}
