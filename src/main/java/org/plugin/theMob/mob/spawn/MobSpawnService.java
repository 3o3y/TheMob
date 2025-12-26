package org.plugin.theMob.mob.spawn;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.plugin.theMob.boss.BossTemplate;
import org.plugin.theMob.boss.bar.BossBarService;
import org.plugin.theMob.boss.phase.BossPhaseController;
import org.plugin.theMob.core.KeyRegistry;
import org.plugin.theMob.mob.MobManager;
import org.plugin.theMob.ui.MobHealthDisplay;

import java.util.Locale;

public final class MobSpawnService {

    private final Plugin plugin;
    private final MobManager mobs;
    private final KeyRegistry keys;
    private final MobHealthDisplay healthDisplay;
    private final BossBarService bossBars;
    private final BossPhaseController phaseController;

    public MobSpawnService(
            Plugin plugin,
            MobManager mobs,
            KeyRegistry keys,
            MobHealthDisplay healthDisplay,
            BossBarService bossBars,
            BossPhaseController phaseController
    ) {
        this.plugin = plugin;
        this.mobs = mobs;
        this.keys = keys;
        this.healthDisplay = healthDisplay;
        this.bossBars = bossBars;
        this.phaseController = phaseController;
    }

    public LivingEntity spawn(String mobId, String spawnId, Location loc) {

        if (mobId == null || spawnId == null || loc == null || loc.getWorld() == null) {
            return null;
        }

        mobId = mobId.toLowerCase(Locale.ROOT);
        FileConfiguration cfg = mobs.mobConfigById(mobId);
        if (cfg == null) return null;

        EntityType type;
        try {
            type = EntityType.valueOf(cfg.getString("base-type").toUpperCase());
        } catch (Exception e) {
            return null;
        }

        LivingEntity mob;
        try {
            mob = (LivingEntity) loc.getWorld().spawnEntity(loc, type);
        } catch (Exception e) {
            return null;
        }

        boolean isBoss = mobs.hasBossTemplate(mobId);

        mob.getPersistentDataContainer().set(keys.MOB_ID, PersistentDataType.STRING, mobId);
        mob.getPersistentDataContainer().set(keys.AUTO_SPAWN_ID, PersistentDataType.STRING, spawnId);
        mob.getPersistentDataContainer().set(keys.IS_BOSS, PersistentDataType.INTEGER, isBoss ? 1 : 0);

        String name = ChatColor.translateAlternateColorCodes(
                '&', cfg.getString("name", type.name())
        );
        mob.getPersistentDataContainer().set(keys.BASE_NAME, PersistentDataType.STRING, name);

        if (cfg.contains("stats.health.max")) {
            double max = cfg.getDouble("stats.health.max");
            var attr = mob.getAttribute(Attribute.MAX_HEALTH);
            if (attr != null) {
                attr.setBaseValue(max);
                mob.setHealth(max);
            }
        }

        if (healthDisplay != null) {
            healthDisplay.onSpawn(mob);
        }

        if (isBoss) {
            BossTemplate tpl = mobs.bossTemplate(mobId);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!mob.isValid()) return;
                if (bossBars != null) bossBars.registerBoss(mob);
                if (tpl != null && phaseController != null) {
                    phaseController.onBossSpawn(mob, tpl);
                }
            });
        }

        return mob;
    }
}
