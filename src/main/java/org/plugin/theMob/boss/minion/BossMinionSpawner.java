package org.plugin.theMob.boss.minion;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.boss.BossPhase;

public final class BossMinionSpawner {
  private final TheMob plugin;
  private final BossMinionController controller;
  private final Random rnd = new Random();
  private final Map<String, BukkitRunnable> repeatTasks = new ConcurrentHashMap<>();

  public BossMinionSpawner(TheMob plugin, BossMinionController controller) {
    this.plugin = plugin;
    this.controller = controller;
  }

  public void shutdown() {
    for (BukkitRunnable r : this.repeatTasks.values()) {
      try {
        r.cancel();
      } catch (Throwable var4) {
      }
    }

    this.repeatTasks.clear();
  }

  public void stopAllForBoss(UUID bossId) {
    if (bossId != null) {
      for (String key : this.repeatTasks.keySet().toArray(new String[0])) {
        if (key.startsWith(bossId.toString() + "|")) {
          BukkitRunnable r = this.repeatTasks.remove(key);
          if (r != null) {
            r.cancel();
          }
        }
      }
    }
  }

  public void onPhaseEnter(LivingEntity boss, BossPhase phase, ConfigurationSection cfg) {
    if (boss != null && phase != null && cfg != null) {
      UUID bossId = boss.getUniqueId();
      String phaseId = phase.id();
      if (!cfg.getBoolean("enabled", false)) {
        this.stopRepeatTask(bossId, phaseId);
      } else {
        boolean repeat = cfg.getBoolean("repeat", false);
        this.summonOnceControlled(boss, phase, cfg);
        if (!repeat) {
          this.stopRepeatTask(bossId, phaseId);
        } else {
          int cooldownSeconds = Math.max(1, cfg.getInt("cooldown", 15));
          this.startRepeatTask(boss, phase, cfg, cooldownSeconds);
        }
      }
    }
  }

  public void onPhaseLeave(LivingEntity boss, BossPhase phase) {
    if (boss != null && phase != null) {
      this.stopRepeatTask(boss.getUniqueId(), phase.id());
    }
  }

  private void startRepeatTask(
      final LivingEntity boss,
      final BossPhase phase,
      final ConfigurationSection cfg,
      int cooldownSeconds) {
    final UUID bossId = boss.getUniqueId();
    final String phaseId = phase.id();
    String key = bossId + "|" + phaseId;
    this.stopRepeatTask(bossId, phaseId);
    BukkitRunnable task =
        new BukkitRunnable() {
          public void run() {
            if (boss.isValid() && !boss.isDead()) {
              BossMinionSpawner.this.summonOnceControlled(boss, phase, cfg);
            } else {
              BossMinionSpawner.this.stopRepeatTask(bossId, phaseId);
            }
          }
        };
    task.runTaskTimer(this.plugin, cooldownSeconds * 20L, cooldownSeconds * 20L);
    this.repeatTasks.put(key, task);
  }

  private void stopRepeatTask(UUID bossId, String phaseId) {
    String key = bossId + "|" + phaseId;
    BukkitRunnable r = this.repeatTasks.remove(key);
    if (r != null) {
      r.cancel();
    }
  }

  private void summonOnceControlled(LivingEntity boss, BossPhase phase, ConfigurationSection cfg) {
    if (!plugin.skills().enabled("bosses.summon-minions")) return;
    if (plugin.activity() != null && !plugin.activity().active(boss)) return;
    if (boss.isValid()) {
      UUID bossId = boss.getUniqueId();
      String phaseId = phase.id();
      int maxAlive = Math.max(0, cfg.getInt("max-alive", 0));
      if (maxAlive <= 0 || this.controller.countBossPhaseMinions(bossId, phaseId) < maxAlive) {
        int amount = Math.max(1, cfg.getInt("amount", 1));
        double radius = Math.max(0.0, cfg.getDouble("radius", 5.0));
        int lifetimeSeconds =
            Math.max(
                1,
                cfg.contains("lifetime") ? cfg.getInt("lifetime", 15) : cfg.getInt("cooldown", 15));
        double scale = Math.max(0.1, Math.min(cfg.getDouble("scale", 1.0), 5.0));
        EntityType type = this.parseType(cfg.getString("type", "ZOMBIE"));
        if (MinionRules.isAllowed(type)) {
          String name = cfg.getString("name", "");
          boolean noDrops = cfg.getBoolean("no-drops", true);
          NamespacedKey guardKey = new NamespacedKey(this.plugin, "minion_tick_" + phaseId);
          long tick = plugin.automation().tps().currentTick();
          Long last =
              (Long) boss.getPersistentDataContainer().get(guardKey, PersistentDataType.LONG);
          if (!Objects.equals(last, tick)) {
            boss.getPersistentDataContainer().set(guardKey, PersistentDataType.LONG, tick);

            for (int i = 0;
                i < amount
                    && boss.isValid()
                    && (maxAlive <= 0
                        || this.controller.countBossPhaseMinions(bossId, phaseId) < maxAlive);
                i++) {
              Location loc =
                  boss.getLocation()
                      .clone()
                      .add(
                          (this.rnd.nextDouble() * 2.0 - 1.0) * radius,
                          0.0,
                          (this.rnd.nextDouble() * 2.0 - 1.0) * radius);
              if (!this.plugin
                  .automation()
                  .gate()
                  .check(boss.getWorld(), org.plugin.theMob.control.SpawnRole.MINION)
                  .allowed()) break;
              String parentId = plugin.mobs().mobIdOf(boss);
              if (parentId != null
                  && !plugin
                      .api()
                      .beforeSpawn(parentId, loc, org.plugin.theMob.api.SpawnReason.MINION)) break;
              Entity spawned = boss.getWorld().spawnEntity(loc, type);
              if (spawned instanceof LivingEntity minion) {
                if (!minion.isValid() || minion.isDead()) continue;
                minion
                    .getPersistentDataContainer()
                    .set(
                        new NamespacedKey(this.plugin, "minion"),
                        PersistentDataType.BYTE,
                        (byte) 1);
                this.plugin
                    .automation()
                    .budgets()
                    .tagSpawnedEntity(minion, org.plugin.theMob.control.SpawnRole.MINION);
                minion.setPersistent(false);
                minion
                    .getPersistentDataContainer()
                    .set(
                        new NamespacedKey(this.plugin, "spawn_session"),
                        PersistentDataType.STRING,
                        this.plugin.runtimeSession());
                minion.setRemoveWhenFarAway(false);
                if (scale != 1.0) {
                  AttributeInstance scaleAttr =
                      org.plugin.theMob.compat.BukkitTypes.instance(minion, "SCALE");
                  if (scaleAttr != null) {
                    scaleAttr.setBaseValue(scale);
                  }
                }

                if (name != null && !name.isEmpty()) {
                  minion.setCustomName(ChatColor.translateAlternateColorCodes('&', name));
                  minion.setCustomNameVisible(true);
                }

                if (noDrops) {
                  minion
                      .getPersistentDataContainer()
                      .set(this.plugin.keys().NO_DROPS, PersistentDataType.INTEGER, 1);
                }

                this.controller.register(bossId, phaseId, minion);
                if (parentId != null) {
                  minion
                      .getPersistentDataContainer()
                      .set(
                          new NamespacedKey(plugin, "api_parent_mob"),
                          PersistentDataType.STRING,
                          parentId);
                  minion
                      .getPersistentDataContainer()
                      .set(
                          new NamespacedKey(plugin, "api_phase"),
                          PersistentDataType.STRING,
                          phaseId);
                  plugin.api().spawned(minion, org.plugin.theMob.api.SpawnReason.MINION);
                }
                (new BukkitRunnable() {
                      public void run() {
                        if (minion.isValid()) {
                          minion.remove();
                        }
                      }
                    })
                    .runTaskLater(this.plugin, lifetimeSeconds * 20L);
              } else {
                spawned.remove();
              }
            }
          }
        }
      }
    }
  }

  private EntityType parseType(String raw) {
    if (raw != null && !raw.isBlank()) {
      try {
        return EntityType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException var3) {
        this.plugin.getLogger().warning("[TheMob] Invalid minion type: " + raw);
        return EntityType.ZOMBIE;
      }
    } else {
      return EntityType.ZOMBIE;
    }
  }
}
