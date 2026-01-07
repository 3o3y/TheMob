package org.plugin.theMob.mob.spawn;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.boss.BossTemplate;
import org.plugin.theMob.boss.bar.BossBarService;
import org.plugin.theMob.boss.phase.BossPhaseController;
import org.plugin.theMob.boss.spawn.ZombieBossFactory;
import org.plugin.theMob.control.SpawnRole;
import org.plugin.theMob.core.KeyRegistry;
import org.plugin.theMob.mob.MobManager;
import org.plugin.theMob.mob.ai.MobAIProfile;
import org.plugin.theMob.mob.ai.MobAIService;
import org.plugin.theMob.ui.MobHealthDisplay;
import org.plugin.theMob.visual.MobVisualService;

import java.util.Locale;

public final class MobSpawnService {

    private final TheMob plugin;
    private final MobManager mobs;
    private final KeyRegistry keys;
    private final MobHealthDisplay healthDisplay;
    private final BossBarService bossBars;
    private final BossPhaseController phaseController;
    private final MobAIService mobAI;

    public MobSpawnService(
            TheMob plugin,
            MobManager mobs,
            KeyRegistry keys,
            MobHealthDisplay healthDisplay,
            BossBarService bossBars,
            BossPhaseController phaseController,
            MobAIService mobAI
    ) {
        this.plugin = plugin;
        this.mobs = mobs;
        this.keys = keys;
        this.healthDisplay = healthDisplay;
        this.bossBars = bossBars;
        this.phaseController = phaseController;
        this.mobAI = mobAI;
    }

    public LivingEntity spawn(String mobId, String spawnId, Location loc) {

        if (mobId == null || loc == null || loc.getWorld() == null) return null;

        mobId = mobId.toLowerCase(Locale.ROOT);
        FileConfiguration cfg = mobs.mobConfigById(mobId);
        if (cfg == null) return null;

        EntityType type;
        try {
            type = EntityType.valueOf(cfg.getString("base-type", "ZOMBIE").toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return null;
        }

        boolean isBoss = mobs.hasBossTemplate(mobId);
        LivingEntity mob;

        if (isBoss && type == EntityType.ZOMBIE) {
            mob = ZombieBossFactory.spawnZombieBoss(plugin, loc, mobId, keys, cfg);
        } else {
            mob = (LivingEntity) loc.getWorld().spawnEntity(loc, type);
        }

        if (mob == null) return null;

        // =========================
        // STATS
        // =========================
        if (cfg.contains("stats.scale")) {
            double scale = Math.max(0.25, Math.min(5.0, cfg.getDouble("stats.scale", 1.0)));
            AttributeInstance scaleAttr = mob.getAttribute(Attribute.SCALE);
            if (scaleAttr != null) scaleAttr.setBaseValue(scale);
        }

        AttributeInstance armor = mob.getAttribute(Attribute.ARMOR);
        if (armor != null && armor.getBaseValue() <= 0.0) {
            armor.setBaseValue(0.01);
        }

        mob.getPersistentDataContainer().set(keys.MOB_ID, PersistentDataType.STRING, mobId);
        mob.getPersistentDataContainer().set(keys.IS_BOSS, PersistentDataType.INTEGER, isBoss ? 1 : 0);

        String name = ChatColor.translateAlternateColorCodes('&',
                cfg.getString("name", type.name()));
        mob.getPersistentDataContainer().set(keys.BASE_NAME, PersistentDataType.STRING, name);

        if (spawnId != null) {
            mob.getPersistentDataContainer().set(keys.AUTO_SPAWN_ID, PersistentDataType.STRING, spawnId);
        }

        if (cfg.contains("stats.health.max")) {
            double max = cfg.getDouble("stats.health.max");
            AttributeInstance hp = mob.getAttribute(Attribute.MAX_HEALTH);
            if (hp != null) {
                hp.setBaseValue(max);
                mob.setHealth(max);
            }
        }

        if (healthDisplay != null) {
            healthDisplay.onSpawn(mob);
        }

        if (mob instanceof Mob bukkitMob) {
            MobAIProfile profile =
                    MobAIProfile.fromConfig(cfg.getConfigurationSection("ai"));
            mobAI.register(bukkitMob, profile);
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

        if (cfg.contains("visual.helmet.type")) {
            MobVisualService.attachVisual(plugin, mob, cfg, keys);
        }

        return mob;
    }
}
