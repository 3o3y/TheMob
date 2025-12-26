package org.plugin.theMob.mob;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.plugin.theMob.boss.bar.BossBarService;

public final class MobListener implements Listener {

    private final MobManager mobs;
    private final BossBarService bossBars;

    public MobListener(
            MobManager mobs,
            org.plugin.theMob.ui.MobHealthDisplay ignored, // kept for constructor compatibility
            BossBarService bossBars
    ) {
        this.mobs = mobs;
        this.bossBars = bossBars;
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        LivingEntity mob = e.getEntity();

        if (!mobs.isCustomMob(mob)) return;

        // Single source of truth for death handling
        mobs.onMobDeath(mob, e);

        if (mobs.isBoss(mob) && bossBars != null) {
            bossBars.unregisterBoss(mob);
        }
    }
}
