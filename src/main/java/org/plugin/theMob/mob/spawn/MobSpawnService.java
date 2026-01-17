package org.plugin.theMob.mob.spawn;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.boss.BossTemplate;
import org.plugin.theMob.boss.bar.BossBarService;
import org.plugin.theMob.boss.phase.BossPhaseController;
import org.plugin.theMob.boss.spawn.ZombieBossFactory;
import org.plugin.theMob.core.KeyRegistry;
import org.plugin.theMob.item.ItemBuilderFromConfig;
import org.plugin.theMob.mob.MobManager;
import org.plugin.theMob.mob.ai.MobAIProfile;
import org.plugin.theMob.mob.ai.MobAIService;
import org.plugin.theMob.mob.stats.BaseMobStatApplier;
import org.plugin.theMob.mob.stats.MobEquipmentStatApplier;
import org.plugin.theMob.spawn.SpawnLocationResolver;
import org.plugin.theMob.ui.MobHealthDisplay;
import org.plugin.theMob.visual.MobVisualService;
import org.plugin.theMob.spawn.SpawnUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MobSpawnService {

    private final TheMob plugin;
    private final MobManager mobs;
    private final KeyRegistry keys;
    private final MobHealthDisplay healthDisplay;
    private final BossBarService bossBars;
    private final BossPhaseController phaseController;
    private final MobAIService mobAI;
    private final BaseMobStatApplier baseStatApplier;


    private final ItemBuilderFromConfig itemBuilder;
    private final MobEquipmentStatApplier statApplier;

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

        this.baseStatApplier = new BaseMobStatApplier(keys);

        this.itemBuilder = new ItemBuilderFromConfig(plugin);
        this.statApplier = new MobEquipmentStatApplier(plugin.itemStats(), keys);
    }

    public LivingEntity spawn(String mobId, String spawnId, Location loc) {

        if (mobId == null || loc == null || loc.getWorld() == null) return null;

        // ✅ HIER
        loc = SpawnLocationResolver.resolveSafe(
                plugin.getConfig(),
                loc,
                null
        );
        // ✅ GLOBAL SAFE SPAWN
        loc = SpawnUtil.resolveSafeSpawn(loc);

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

        // =====================================================
        // PDC IDENTITY
        // =====================================================
        mob.getPersistentDataContainer().set(keys.MOB_ID, PersistentDataType.STRING, mobId);
        mob.getPersistentDataContainer().set(keys.IS_BOSS, PersistentDataType.INTEGER, isBoss ? 1 : 0);

        String name = ChatColor.translateAlternateColorCodes('&',
                cfg.getString("name", type.name()));
        mob.getPersistentDataContainer().set(keys.BASE_NAME, PersistentDataType.STRING, name);

        if (spawnId != null) {
            mob.getPersistentDataContainer().set(keys.AUTO_SPAWN_ID, PersistentDataType.STRING, spawnId);
        }

        // =====================================================
        // BASE STATS FIRST (so equipment ADDS on top)
        // =====================================================
        if (cfg.contains("stats.scale")) {
            double scale = Math.max(0.25, Math.min(5.0, cfg.getDouble("stats.scale", 1.0)));
            AttributeInstance scaleAttr = mob.getAttribute(Attribute.SCALE);
            if (scaleAttr != null) scaleAttr.setBaseValue(scale);
        }

        if (cfg.contains("stats.health.max")) {
            double max = cfg.getDouble("stats.health.max");
            AttributeInstance hp = mob.getAttribute(Attribute.MAX_HEALTH);
            if (hp != null) {
                hp.setBaseValue(max);
                mob.setHealth(max);
            }
        }

        // Small armor floor (optional)
        AttributeInstance armor = mob.getAttribute(Attribute.ARMOR);
        if (armor != null && armor.getBaseValue() <= 0.0) {
            armor.setBaseValue(0.01);
        }

        // =====================================================
        // EQUIPMENT
        // =====================================================
        EntityEquipment e = mob.getEquipment();
        ConfigurationSection eq = cfg.getConfigurationSection("equipment");

        if (eq != null && e != null) {

            e.setHelmet(rollEquipment(eq.getConfigurationSection("helmet")));
            e.setChestplate(rollEquipment(eq.getConfigurationSection("chestplate")));
            e.setLeggings(rollEquipment(eq.getConfigurationSection("leggings")));
            e.setBoots(rollEquipment(eq.getConfigurationSection("boots")));
            e.setItemInMainHand(rollEquipment(eq.getConfigurationSection("main-hand")));
            e.setItemInOffHand(rollEquipment(eq.getConfigurationSection("off-hand")));

            // 🔒 DROP SAFETY
            e.setHelmetDropChance(0f);
            e.setChestplateDropChance(0f);
            e.setLeggingsDropChance(0f);
            e.setBootsDropChance(0f);
            e.setItemInMainHandDropChance(0f);
            e.setItemInOffHandDropChance(0f);

            // =================================================
            // APPLY EQUIPMENT STATS
            // =================================================
            List<ItemStack> items = new ArrayList<>(6);
            if (e.getHelmet() != null) items.add(e.getHelmet());
            if (e.getChestplate() != null) items.add(e.getChestplate());
            if (e.getLeggings() != null) items.add(e.getLeggings());
            if (e.getBoots() != null) items.add(e.getBoots());
            if (e.getItemInMainHand() != null) items.add(e.getItemInMainHand());
            if (e.getItemInOffHand() != null) items.add(e.getItemInOffHand());

            statApplier.apply(mob, items);
        }
// =========================
// APPLY BASE MOB STATS
// =========================
        baseStatApplier.apply(
                mob,
                cfg.getConfigurationSection("stats")
        );

        // =====================================================
        // UI / AI
        // =====================================================
        if (healthDisplay != null) {
            healthDisplay.onSpawn(mob);
        }

        if (mob instanceof Mob bukkitMob) {
            MobAIProfile profile = MobAIProfile.fromConfig(cfg.getConfigurationSection("ai"));
            mobAI.register(bukkitMob, profile);
        }

        // =====================================================
        // BOSS HOOKS
        // =====================================================
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

        // =====================================================
        // VISUALS
        // =====================================================
        if (cfg.contains("visual.helmet.type")) {
            MobVisualService.attachVisual(plugin, mob, cfg, keys);
        }

        return mob;
    }

    private ItemStack rollEquipment(ConfigurationSection sec) {
        if (sec == null) return null;

        double chance = sec.getDouble("chance", 1.0);
        if (chance <= 0) return null;
        if (chance < 1.0 && Math.random() > chance) return null;

        String type = sec.getString("type");

        if ("BASE64".equalsIgnoreCase(type)) {
            String value = sec.getString("value");
            if (value == null || value.isEmpty()) return null;
            return itemBuilder.fromBase64(value);
        }

        if ("HEAD_TEXTURE".equalsIgnoreCase(type)) {
            String texture = sec.getString("value");
            if (texture == null || texture.isEmpty()) return null;
            return itemBuilder.skullFromTexture(texture);
        }

        if (sec.contains("material") || sec.contains("item")) {
            return itemBuilder.build(sec.getValues(false));
        }

        return null;
    }
}
