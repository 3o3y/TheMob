package org.plugin.theMob.mob;

import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.plugin.theMob.boss.BossActionEngine;
import org.plugin.theMob.boss.bar.BossBarService;
import org.plugin.theMob.core.KeyRegistry;

public final class MobListener implements Listener {

    private final MobManager mobs;
    private final BossBarService bossBars;
    private final BossActionEngine bossActions;
    private final KeyRegistry keys;

    public MobListener(
            MobManager mobs,
            org.plugin.theMob.ui.MobHealthDisplay ignored,
            BossBarService bossBars,
            BossActionEngine bossActions,
            KeyRegistry keys
    ) {
        this.mobs = mobs;
        this.bossBars = bossBars;
        this.bossActions = bossActions;
        this.keys = keys;
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        LivingEntity mob = e.getEntity();

        if (!mobs.isCustomMob(mob)) return;

        // =========================================
        // 🔥 VISUAL CLEANUP (FLOATING HEAD)
        // =========================================
        if (mobs.isBoss(mob)) {
            for (Entity nearby : mob.getWorld().getNearbyEntities(
                    mob.getLocation(),
                    3.0, 3.0, 3.0
            )) {
                if (nearby instanceof ArmorStand stand &&
                        stand.getPersistentDataContainer().has(
                                keys.VISUAL_HEAD,
                                PersistentDataType.INTEGER
                        )) {
                    stand.remove();
                }
            }
        }

        // =========================================
        // 🌍 WORLD RESTORE (CRITICAL FIX)
        // =========================================
        if (mobs.isBoss(mob)) {
            bossActions.onBossDeath(mob);
        }

        // =========================================
        // CORE DEATH HANDLING
        // =========================================
        mobs.onMobDeath(mob, e);

        if (mobs.isBoss(mob) && bossBars != null) {
            bossBars.unregisterBoss(mob);
        }
    }
}
