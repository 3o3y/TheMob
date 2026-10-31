package org.plugin.theMob.mob.spawn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.boss.BossTemplate;
import org.plugin.theMob.boss.bar.BossBarService;
import org.plugin.theMob.boss.behavior.BossBehaviorController;
import org.plugin.theMob.boss.phase.BossPhaseController;
import org.plugin.theMob.boss.spawn.ZombieBossFactory;
import org.plugin.theMob.core.KeyRegistry;
import org.plugin.theMob.item.ItemBuilderFromConfig;
import org.plugin.theMob.mob.MobManager;
import org.plugin.theMob.mob.stats.BaseMobStatApplier;
import org.plugin.theMob.mob.stats.MobEquipmentStatApplier;
import org.plugin.theMob.spawn.SpawnLocationResolver;
import org.plugin.theMob.ui.MobHealthDisplay;
import org.plugin.theMob.visual.MobVisualService;

public final class MobSpawnService {
  private final TheMob plugin;
  private final MobManager mobs;
  private final KeyRegistry keys;
  private final MobHealthDisplay healthDisplay;
  private final BossBarService bossBars;
  private final BossPhaseController phaseController;
  private final BaseMobStatApplier baseStatApplier;
  private BossBehaviorController behaviorController;
  private final ItemBuilderFromConfig itemBuilder;
  private final MobEquipmentStatApplier statApplier;

  public MobSpawnService(
      TheMob plugin,
      MobManager mobs,
      KeyRegistry keys,
      MobHealthDisplay healthDisplay,
      BossBarService bossBars,
      BossPhaseController phaseController) {
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
    mobId = mobId.toLowerCase(Locale.ROOT);
    var automation = this.plugin.automation();
    var role =
        this.mobs.hasBossTemplate(mobId)
            ? org.plugin.theMob.control.SpawnRole.BOSS
            : org.plugin.theMob.control.SpawnRole.MOB;
    if (automation == null || !automation.gate().check(loc.getWorld(), role).allowed()) return null;
    if (role == org.plugin.theMob.control.SpawnRole.BOSS
        && !automation.bossCooldowns().canSpawnNow(mobId)) return null;
    var reason = plugin.api().reason(spawnId);
    loc = SpawnLocationResolver.resolveSafe(this.plugin.getConfig(), loc, null);
    if (!plugin.api().beforeSpawn(mobId, loc, reason)) return null;
    LivingEntity result = this.spawnUnchecked(mobId, spawnId, loc);
    if (result != null && result.isValid() && !result.isDead()) {
      automation.budgets().tagSpawnedEntity(result, role);
      plugin.api().spawned(result, reason);
    }
    return result;
  }

  private LivingEntity spawnUnchecked(String mobId, String spawnId, Location loc) {
    if (mobId != null && loc != null && loc.getWorld() != null) {
      // Location was resolved once, before the cancellable region/API checks.
      mobId = mobId.toLowerCase(Locale.ROOT);
      FileConfiguration cfg = this.mobs.mobConfigById(mobId);
      if (cfg == null) {
        return null;
      } else {
        EntityType type;
        try {
          type =
              EntityType.valueOf(
                  cfg.getString("base-type", cfg.getString("type", "ZOMBIE"))
                      .toUpperCase(Locale.ROOT));
        } catch (Exception var13) {
          return null;
        }

        if (!type.isAlive() || !type.isSpawnable()) return null;
        boolean isBoss = this.mobs.hasBossTemplate(mobId);
        final LivingEntity mob;
        if (isBoss && type == EntityType.ZOMBIE) {
          mob = ZombieBossFactory.spawnZombieBoss(loc, mobId, this.keys, cfg);
        } else {
          mob = (LivingEntity) loc.getWorld().spawnEntity(loc, type);
        }

        if (mob == null || !mob.isValid() || mob.isDead()) {
          return null;
        } else {
          try {
            if (cfg.getBoolean("baby", false) && mob instanceof Ageable ageable) {
              ageable.setBaby();
              ageable.setAgeLock(true);
            }

            mob.getPersistentDataContainer()
                .set(this.keys.MOB_ID, PersistentDataType.STRING, mobId);
            mob.getPersistentDataContainer()
                .set(
                    new org.bukkit.NamespacedKey(plugin, "api_source"),
                    PersistentDataType.STRING,
                    plugin.api().reason(spawnId).name());
            mob.getPersistentDataContainer()
                .set(
                    new org.bukkit.NamespacedKey(this.plugin, "spawn_session"),
                    PersistentDataType.STRING,
                    this.plugin.runtimeSession());
            mob.getPersistentDataContainer()
                .set(this.keys.IS_BOSS, PersistentDataType.INTEGER, isBoss ? 1 : 0);
            String name =
                ChatColor.translateAlternateColorCodes('&', cfg.getString("name", type.name()));
            mob.getPersistentDataContainer()
                .set(this.keys.BASE_NAME, PersistentDataType.STRING, name);
            if (spawnId != null) {
              mob.getPersistentDataContainer()
                  .set(this.keys.AUTO_SPAWN_ID, PersistentDataType.STRING, spawnId);
              var auto = mobs.getAutoSpawnManager();
              if (auto != null)
                mob.getPersistentDataContainer()
                    .set(
                        new org.bukkit.NamespacedKey(plugin, "arena_epoch"),
                        PersistentDataType.LONG,
                        auto.arenaEpoch(spawnId));
            }

            if (cfg.contains("stats.scale")) {
              AttributeInstance scale = org.plugin.theMob.compat.BukkitTypes.instance(mob, "SCALE");
              if (scale != null) {
                scale.setBaseValue(
                    Math.max(0.25, Math.min(5.0, cfg.getDouble("stats.scale", 1.0))));
              }
            }

            if (cfg.contains("stats.health.max")) {
              AttributeInstance hp =
                  org.plugin.theMob.compat.BukkitTypes.instance(mob, "MAX_HEALTH");
              if (hp != null) {
                double max = cfg.getDouble("stats.health.max");
                hp.setBaseValue(max);
                mob.setHealth(
                    Math.min(
                        hp.getValue(), Math.max(1.0, cfg.getDouble("stats.health.current", max))));
              }
            }

            AttributeInstance armor = org.plugin.theMob.compat.BukkitTypes.instance(mob, "ARMOR");
            if (armor != null && armor.getBaseValue() <= 0.0) {
              armor.setBaseValue(0.01);
            }

            this.baseStatApplier.apply(mob, cfg.getConfigurationSection("stats"));
            if (plugin.abilities().enabled("bosses.phase-abilities"))
              new org.plugin.theMob.mob.ability.AbilityEngine(this.keys).apply(mob, cfg);
            EntityEquipment eqp = mob.getEquipment();
            ConfigurationSection eq = cfg.getConfigurationSection("equipment");
            if (eq != null && eqp != null) {
              eqp.setHelmet(this.rollEquipment(eq.getConfigurationSection("helmet")));
              eqp.setChestplate(this.rollEquipment(eq.getConfigurationSection("chestplate")));
              eqp.setLeggings(this.rollEquipment(eq.getConfigurationSection("leggings")));
              eqp.setBoots(this.rollEquipment(eq.getConfigurationSection("boots")));
              eqp.setItemInMainHand(this.rollEquipment(eq.getConfigurationSection("main-hand")));
              eqp.setItemInOffHand(this.rollEquipment(eq.getConfigurationSection("off-hand")));
              eqp.setHelmetDropChance(0.0F);
              eqp.setChestplateDropChance(0.0F);
              eqp.setLeggingsDropChance(0.0F);
              eqp.setBootsDropChance(0.0F);
              eqp.setItemInMainHandDropChance(0.0F);
              eqp.setItemInOffHandDropChance(0.0F);
              List<ItemStack> items = new ArrayList<>(6);
              if (eqp.getHelmet() != null) {
                items.add(eqp.getHelmet());
              }

              if (eqp.getChestplate() != null) {
                items.add(eqp.getChestplate());
              }

              if (eqp.getLeggings() != null) {
                items.add(eqp.getLeggings());
              }

              if (eqp.getBoots() != null) {
                items.add(eqp.getBoots());
              }

              if (eqp.getItemInMainHand() != null) {
                items.add(eqp.getItemInMainHand());
              }

              if (eqp.getItemInOffHand() != null) {
                items.add(eqp.getItemInOffHand());
              }

              this.statApplier.apply(mob, items);
            }

            if (this.healthDisplay != null) {
              this.healthDisplay.onSpawn(mob);
            }

            if (isBoss) {
              this.bossBars.registerBoss(mob);
              this.bossBars.markDirty(mob);
              BossTemplate tpl = this.mobs.bossTemplate(mobId);
              if (tpl != null) {
                this.phaseController.onBossSpawn(mob, tpl);
              }

              (new BukkitRunnable() {
                    public void run() {
                      if (mob.isValid() && !mob.isDead()) {
                        MobSpawnService.this.phaseController.onBossUpdate(mob);
                      }
                    }
                  })
                  .runTaskLater(this.plugin, 10L);
            }

            if (cfg.contains("visual.helmet.type")) {
              MobVisualService.attachVisual(this.plugin, mob, cfg, this.keys);
            }

            return mob;
          } catch (RuntimeException failure) {
            mob.remove();
            this.phaseController.onBossDeath(mob);
            this.plugin
                .getLogger()
                .log(java.util.logging.Level.WARNING, "Failed to initialize mob " + mobId, failure);
            return null;
          }
        }
      }
    } else {
      return null;
    }
  }

  private ItemStack rollEquipment(ConfigurationSection sec) {
    if (sec == null) {
      return null;
    } else {
      double chance = sec.getDouble("chance", 1.0);
      if (chance <= 0.0) {
        return null;
      } else if (chance < 1.0 && Math.random() > chance) {
        return null;
      } else {
        String type = sec.getString("type");
        if ("BASE64".equalsIgnoreCase(type)) {
          String value = sec.getString("value");
          return value != null && !value.isEmpty() ? this.itemBuilder.fromBase64(value) : null;
        } else if ("HEAD_TEXTURE".equalsIgnoreCase(type)) {
          String texture = sec.getString("value");
          return texture != null && !texture.isEmpty()
              ? this.itemBuilder.skullFromTexture(texture)
              : null;
        } else {
          return !sec.contains("material") && !sec.contains("item")
              ? null
              : this.itemBuilder.build(sec.getValues(false));
        }
      }
    }
  }
}
