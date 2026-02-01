package org.plugin.theMob.mob.spawn;

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
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.boss.BossTemplate;
import org.plugin.theMob.boss.bar.BossBarService;
import org.plugin.theMob.boss.phase.BossPhaseController;
import org.plugin.theMob.boss.spawn.ZombieBossFactory;
import org.plugin.theMob.core.KeyRegistry;
import org.plugin.theMob.item.ItemBuilderFromConfig;
import org.plugin.theMob.mob.MobManager;
import org.plugin.theMob.mob.stats.BaseMobStatApplier;
import org.plugin.theMob.mob.stats.MobEquipmentStatApplier;
import org.plugin.theMob.spawn.SpawnLocationResolver;
import org.plugin.theMob.spawn.SpawnUtil;
import org.plugin.theMob.ui.MobHealthDisplay;
import org.plugin.theMob.visual.MobVisualService;
import org.bukkit.entity.Ageable;


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
    private final BaseMobStatApplier baseStatApplier;

    private final ItemBuilderFromConfig itemBuilder;
    private final MobEquipmentStatApplier statApplier;

    public MobSpawnService(
            TheMob plugin,
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

        this.baseStatApplier = new BaseMobStatApplier(keys);
        this.itemBuilder = new ItemBuilderFromConfig(plugin);
        this.statApplier = new MobEquipmentStatApplier(plugin.itemStats(), keys);
    }

    public LivingEntity spawn(String mobId, String spawnId, Location loc) {
        if (mobId == null || loc == null || loc.getWorld() == null) return null;

        // SAFE SPAWN
        loc = SpawnLocationResolver.resolveSafe(plugin.getConfig(), loc, null);
        loc = SpawnUtil.resolveSafeSpawn(loc);

        mobId = mobId.toLowerCase(Locale.ROOT);

        FileConfiguration cfg = mobs.mobConfigById(mobId);
        if (cfg == null) return null;

        EntityType type;
        try {
            type = EntityType.valueOf(
                    cfg.getString("base-type", "ZOMBIE").toUpperCase(Locale.ROOT)
            );
        } catch (Exception e) {
            return null;
        }

        boolean isBoss = mobs.hasBossTemplate(mobId);

        LivingEntity mob;
        if (isBoss && type == EntityType.ZOMBIE) {
            mob = ZombieBossFactory.spawnZombieBoss(loc, mobId, keys, cfg);
        } else {
            mob = (LivingEntity) loc.getWorld().spawnEntity(loc, type);
        }
        if (mob == null) return null;

        // =========================
        // BABY MOB SUPPORT (FIXED)
        // =========================
        if (cfg.getBoolean("baby", false) && mob instanceof Ageable ageable) {
            ageable.setBaby();
            ageable.setAgeLock(true); // optional: verhindert Aufwachsen
        }


        // =========================
        // PDC IDENTITY
        // =========================
        mob.getPersistentDataContainer().set(
                keys.MOB_ID,
                PersistentDataType.STRING,
                mobId
        );
        mob.getPersistentDataContainer().set(
                keys.IS_BOSS,
                PersistentDataType.INTEGER,
                isBoss ? 1 : 0
        );

        String name = ChatColor.translateAlternateColorCodes(
                '&',
                cfg.getString("name", type.name())
        );
        mob.getPersistentDataContainer().set(
                keys.BASE_NAME,
                PersistentDataType.STRING,
                name
        );

        if (spawnId != null) {
            mob.getPersistentDataContainer().set(
                    keys.AUTO_SPAWN_ID,
                    PersistentDataType.STRING,
                    spawnId
            );
        }

        // =========================
        // BASE STATS (nach Baby!)
        // =========================
        if (cfg.contains("stats.scale")) {
            AttributeInstance scale = mob.getAttribute(Attribute.SCALE);
            if (scale != null) {
                scale.setBaseValue(
                        Math.max(0.25, Math.min(5.0, cfg.getDouble("stats.scale", 1.0)))
                );
            }
        }

        if (cfg.contains("stats.health.max")) {
            AttributeInstance hp = mob.getAttribute(Attribute.MAX_HEALTH);
            if (hp != null) {
                double max = cfg.getDouble("stats.health.max");
                hp.setBaseValue(max);
                mob.setHealth(
                        Math.min(max, Math.max(1.0,
                                cfg.getDouble("stats.health.current", max)))
                );
            }
        }

        AttributeInstance armor = mob.getAttribute(Attribute.ARMOR);
        if (armor != null && armor.getBaseValue() <= 0.0) {
            armor.setBaseValue(0.01);
        }

        // =========================
        // EQUIPMENT
        // =========================
        EntityEquipment eqp = mob.getEquipment();
        ConfigurationSection eq = cfg.getConfigurationSection("equipment");

        if (eq != null && eqp != null) {
            eqp.setHelmet(rollEquipment(eq.getConfigurationSection("helmet")));
            eqp.setChestplate(rollEquipment(eq.getConfigurationSection("chestplate")));
            eqp.setLeggings(rollEquipment(eq.getConfigurationSection("leggings")));
            eqp.setBoots(rollEquipment(eq.getConfigurationSection("boots")));
            eqp.setItemInMainHand(rollEquipment(eq.getConfigurationSection("main-hand")));
            eqp.setItemInOffHand(rollEquipment(eq.getConfigurationSection("off-hand")));

            eqp.setHelmetDropChance(0f);
            eqp.setChestplateDropChance(0f);
            eqp.setLeggingsDropChance(0f);
            eqp.setBootsDropChance(0f);
            eqp.setItemInMainHandDropChance(0f);
            eqp.setItemInOffHandDropChance(0f);

            List<ItemStack> items = new ArrayList<>(6);
            if (eqp.getHelmet() != null) items.add(eqp.getHelmet());
            if (eqp.getChestplate() != null) items.add(eqp.getChestplate());
            if (eqp.getLeggings() != null) items.add(eqp.getLeggings());
            if (eqp.getBoots() != null) items.add(eqp.getBoots());
            if (eqp.getItemInMainHand() != null) items.add(eqp.getItemInMainHand());
            if (eqp.getItemInOffHand() != null) items.add(eqp.getItemInOffHand());

            statApplier.apply(mob, items);
        }

        baseStatApplier.apply(mob, cfg.getConfigurationSection("stats"));

        // =========================
        // UI
        // =========================
        if (healthDisplay != null) {
            healthDisplay.onSpawn(mob);
        }

        // =========================
        // BOSS INIT (SPAWN)
        // =========================
        if (isBoss) {
            bossBars.registerBoss(mob);
            bossBars.markDirty(mob);

            BossTemplate tpl = mobs.bossTemplate(mobId);
            if (tpl != null) {
                phaseController.onBossSpawn(mob, tpl);
            }

            // ✅ SAUBERER MINI-FIX:
            // Phase-Trigger ohne Damage, delayed
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!mob.isValid() || mob.isDead()) return;
                    phaseController.onBossUpdate(mob);
                }
            }.runTaskLater(plugin, 10L);
        }

        // =========================
        // HARD BOSS RESET
        // =========================
        if (isBoss && mob instanceof Mob m) {
            m.setAI(true);
            m.setAware(true);
            m.setTarget(null);

            m.removePotionEffect(PotionEffectType.BLINDNESS);
            m.removePotionEffect(PotionEffectType.SLOWNESS);
            m.removePotionEffect(PotionEffectType.WEAKNESS);
        }

        // =========================
        // VISUALS
        // =========================
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
