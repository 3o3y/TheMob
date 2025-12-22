// src/main/java/org/plugin/theMob/mob/spawn/MobSpawnService.java
package org.plugin.theMob.mob.spawn;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.plugin.theMob.boss.BossTemplate;
import org.plugin.theMob.boss.phase.BossPhaseController;
import org.plugin.theMob.boss.bar.BossBarService;
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
// SPAWN
    public boolean spawn(String id, Location loc) {
        if (id == null || loc == null || loc.getWorld() == null) return false;
        id = id.toLowerCase(Locale.ROOT);
        FileConfiguration cfg = mobs.mobConfigById(id);
        if (cfg == null) return false;
        String baseTypeStr = cfg.getString("base-type");
        if (baseTypeStr == null) return false;
        final EntityType type;
        try {
            type = EntityType.valueOf(baseTypeStr.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return false;
        }
        final LivingEntity mob;
        try {
            mob = (LivingEntity) loc.getWorld().spawnEntity(loc, type);
        } catch (Exception e) {
            return false;
        }
        final boolean isBoss = mobs.hasBossTemplate(id);
// PDC SETUP
        PersistentDataContainer pdc = mob.getPersistentDataContainer();
        pdc.set(keys.MOB_ID, PersistentDataType.STRING, id);
        pdc.set(keys.IS_BOSS, PersistentDataType.INTEGER, isBoss ? 1 : 0);
        String baseName = ChatColor.translateAlternateColorCodes(
                '&',
                cfg.getString("name", type.name())
        );
        pdc.set(keys.BASE_NAME, PersistentDataType.STRING, baseName);
        pdc.set(keys.AUTO_SPAWNED, PersistentDataType.INTEGER, 1);
// HEALTH
        if (cfg.contains("stats.health.max")) {
            double max = cfg.getDouble("stats.health.max");
            var attr = mob.getAttribute(Attribute.MAX_HEALTH);
            if (attr != null) {
                attr.setBaseValue(max);
                mob.setHealth(max);
            }
        }
// UI
        if (healthDisplay != null) {
            healthDisplay.onSpawn(mob);
        }
// BOSS INIT
        if (isBoss) {
            BossTemplate tpl = mobs.bossTemplate(id);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!mob.isValid() || mob.isDead()) return;
                if (bossBars != null) {
                    bossBars.registerBoss(mob);
                    bossBars.markDirty(mob);
                }
                if (tpl != null && phaseController != null) {
                    phaseController.onBossSpawn(mob, tpl);
                }
            });
        }
        if (isBoss && phaseController != null) {
            BossTemplate tpl = mobs.bossTemplate(id);
            if (tpl != null) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        phaseController.onBossSpawn(mob, tpl)
                );
            }
        }
        return true;
    }
}
