package org.plugin.theMob.boss.spawn;

import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Zombie;
import org.bukkit.persistence.PersistentDataType;
import org.plugin.theMob.core.KeyRegistry;

public final class ZombieBossFactory {

    private ZombieBossFactory() {}

    public static Zombie spawnZombieBoss(
            Location loc,
            String bossId,
            KeyRegistry keys,
            FileConfiguration cfg
    ) {
        if (keys == null) throw new IllegalArgumentException("keys is null");
        if (bossId == null || bossId.isBlank())
            throw new IllegalArgumentException("bossId is null/blank");
        if (loc == null || loc.getWorld() == null)
            throw new IllegalArgumentException("location/world is null");

        Zombie zombie = (Zombie) loc.getWorld().spawnEntity(loc, EntityType.ZOMBIE);

        // =====================================================
        // ENTITY BASICS
        // =====================================================
        zombie.setAdult();
        zombie.setPersistent(true);
        zombie.setRemoveWhenFarAway(false);
        zombie.setCanPickupItems(false);
        zombie.setAI(true);                 // 🔒 explizit
        zombie.setAware(true);              // 🔒 verhindert „Moonwalk“-Bugs
        zombie.addScoreboardTag("themob_boss");

        // =====================================================
        // PDC IDENTITY
        // =====================================================
        zombie.getPersistentDataContainer().set(
                keys.MOB_ID,
                PersistentDataType.STRING,
                bossId
        );
        zombie.getPersistentDataContainer().set(
                keys.IS_BOSS,
                PersistentDataType.INTEGER,
                1
        );
        zombie.getPersistentDataContainer().set(
                keys.BOSS_SPAWN_TIME,
                PersistentDataType.LONG,
                System.currentTimeMillis()
        );

        // =====================================================
        // HEALTH (BASE ONLY – PHASE SYSTEM WILL ADJUST LATER)
        // =====================================================
        if (cfg != null && cfg.contains("stats.health.max")) {
            double max = Math.max(1.0, cfg.getDouble("stats.health.max"));
            AttributeInstance attr = zombie.getAttribute(Attribute.MAX_HEALTH);
            if (attr != null) {
                attr.setBaseValue(max);
                zombie.setHealth(max);
            }
        }

        return zombie;
    }
}
