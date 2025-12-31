package org.plugin.theMob.boss.spawn;

import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Zombie;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.plugin.theMob.core.KeyRegistry;

public final class ZombieBossFactory {

    private ZombieBossFactory() {}

    public static Zombie spawnZombieBoss(
            Plugin plugin,
            Location loc,
            String bossId,
            KeyRegistry keys,
            FileConfiguration cfg
    ) {
        if (loc == null || loc.getWorld() == null) {
            throw new IllegalArgumentException("Location/world is null");
        }
        Zombie zombie = (Zombie) loc.getWorld().spawnEntity(loc, EntityType.ZOMBIE);

        zombie.setAdult();
        zombie.setPersistent(true);
        zombie.setRemoveWhenFarAway(false);
        zombie.setCanPickupItems(false);
        zombie.addScoreboardTag("themob_boss");
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

        if (cfg != null && cfg.contains("stats.health.max")) {
            double max = cfg.getDouble("stats.health.max");
            var attr = zombie.getAttribute(Attribute.MAX_HEALTH);
            if (attr != null) {
                attr.setBaseValue(max);
                zombie.setHealth(max);
            }
        }

        return zombie;
    }
}
