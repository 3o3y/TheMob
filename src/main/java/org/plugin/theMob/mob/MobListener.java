// src/main/java/org/plugin/theMob/mob/MobListener.java
package org.plugin.theMob.mob;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.persistence.PersistentDataType;
import org.plugin.theMob.boss.bar.BossBarService;
import org.plugin.theMob.core.KeyRegistry;
import org.plugin.theMob.mob.spawn.AutoSpawnManager;
import org.plugin.theMob.ui.MobHealthDisplay;

public final class MobListener implements Listener {

    private final MobManager mobs;
    private final MobHealthDisplay healthDisplay;
    private final AutoSpawnManager autoSpawnManager;
    private final KeyRegistry keys;
    private final BossBarService bossBars;
    public MobListener(
            MobManager mobs,
            MobHealthDisplay healthDisplay,
            AutoSpawnManager autoSpawnManager,
            KeyRegistry keys,
            BossBarService bossBars
    ) {
        this.mobs = mobs;
        this.healthDisplay = healthDisplay;
        this.autoSpawnManager = autoSpawnManager;
        this.keys = keys;
        this.bossBars = bossBars;
    }
// SPAWN
    @EventHandler
    public void onSpawn(CreatureSpawnEvent e) {
        if (!(e.getEntity() instanceof LivingEntity mob)) return;
        if (!mobs.isCustomMob(mob)) return;
        if (healthDisplay != null) {
            healthDisplay.update(mob);
        }
    }
// DAMAGE
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity mob)) return;
        if (!mobs.isCustomMob(mob)) return;
        if (healthDisplay != null) {
            healthDisplay.update(mob);
        }
    }
// HEAL
    @EventHandler(ignoreCancelled = true)
    public void onHeal(EntityRegainHealthEvent e) {
        if (!(e.getEntity() instanceof LivingEntity mob)) return;
        if (!mobs.isCustomMob(mob)) return;
        if (healthDisplay != null) {
            healthDisplay.update(mob);
        }
    }
// DEATH
    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        LivingEntity mob = e.getEntity();
        if (!mobs.isCustomMob(mob)) return;
        mobs.onMobDeath(mob, e);
        if (mobs.isBoss(mob) && bossBars != null) {
            bossBars.unregisterBoss(mob);
        }
        if (mob.getPersistentDataContainer()
                .has(keys.AUTO_SPAWNED, PersistentDataType.INTEGER)) {
            String id = mobs.mobIdOf(mob);
            if (id != null) {
                autoSpawnManager.decrementAlive(id);
            }
        }
    }
}
