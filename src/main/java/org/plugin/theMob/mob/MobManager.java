package org.plugin.theMob.mob;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.plugin.theMob.boss.BossActionEngine;
import org.plugin.theMob.boss.BossTemplate;
import org.plugin.theMob.boss.BossTemplateParser;
import org.plugin.theMob.core.ConfigService;
import org.plugin.theMob.core.KeyRegistry;
import org.plugin.theMob.mob.spawn.AutoSpawnManager;
import org.plugin.theMob.mob.spawn.MobSpawnService;
import org.plugin.theMob.ui.MobHealthDisplay;

public final class MobManager {
  private final JavaPlugin plugin;
  private final ConfigService configs;
  private final KeyRegistry keys;
  private MobDropEngine dropEngine;
  private MobHealthDisplay healthDisplay;
  private MobSpawnService spawnService;
  private AutoSpawnManager autoSpawn;
  private BossActionEngine bossActionEngine;
  private final Map<String, FileConfiguration> mobConfigs = new HashMap<>();
  private final Map<String, BossTemplate> bossTemplates = new HashMap<>();
  private final Map<java.util.UUID, LivingEntity> tracked = new java.util.LinkedHashMap<>();

  public void track(LivingEntity entity) {
    tracked.put(entity.getUniqueId(), entity);
    if (plugin instanceof org.plugin.theMob.TheMob owner && owner.activity() != null)
      owner.activity().track(entity);
  }

  public LivingEntity tracked(java.util.UUID id) {
    return tracked.get(id);
  }

  public void restoreLoaded(LivingEntity entity) {
    track(entity);
    if (plugin instanceof org.plugin.theMob.TheMob owner
        && isBoss(entity)
        && owner.bossPhases() != null) {
      if (owner.activity() != null) owner.activity().wake(entity);
      BossTemplate template = getBossTemplate(entity);
      if (template != null) owner.bossPhases().onBossSpawn(entity, template);
    }
    if (healthDisplay != null) healthDisplay.onSpawn(entity);
  }

  public void untrack(java.util.UUID id) {
    tracked.remove(id);
    if (plugin instanceof org.plugin.theMob.TheMob owner && owner.activity() != null)
      owner.activity().forget(id);
  }

  public MobManager(JavaPlugin plugin, ConfigService configs, KeyRegistry keys) {
    this.plugin = plugin;
    this.configs = configs;
    this.keys = keys;
  }

  public void setAutoSpawnManager(AutoSpawnManager autoSpawn) {
    this.autoSpawn = autoSpawn;
  }

  public AutoSpawnManager getAutoSpawnManager() {
    return this.autoSpawn;
  }

  public void setSpawnService(MobSpawnService spawnService) {
    this.spawnService = spawnService;
  }

  public void setDropEngine(MobDropEngine dropEngine) {
    this.dropEngine = dropEngine;
    if (dropEngine == null) {
      this.plugin.getLogger().warning("[TheMob] DropEngine cleared");
    }
  }

  public void setHealthDisplay(MobHealthDisplay display) {
    this.healthDisplay = display;
  }

  public void setBossActionEngine(BossActionEngine engine) {
    this.bossActionEngine = engine;
  }

  public void reloadFromConfigs() {
    this.mobConfigs.clear();
    this.mobConfigs.putAll(this.configs.mobConfigs());
    this.bossTemplates.clear();

    for (Entry<String, FileConfiguration> e : this.mobConfigs.entrySet()) {
      BossTemplate tpl = BossTemplateParser.tryParse(e.getKey(), e.getValue());
      if (tpl != null && tpl.hasPhases()) {
        this.bossTemplates.put(e.getKey().toLowerCase(Locale.ROOT), tpl);
      }
    }

    this.plugin
        .getLogger()
        .info(
            "[TheMob] Loaded "
                + this.mobConfigs.size()
                + " mob configs | bosses="
                + this.bossTemplates.size());
  }

  public LivingEntity spawnCustomMob(String mobId, String spawnId, Location loc) {
    if (this.spawnService == null) {
      this.plugin.getLogger().severe("[TheMob] SpawnService not set!");
      return null;
    } else {
      return this.spawnService.spawn(mobId, spawnId, loc);
    }
  }

  public String baseNameOf(LivingEntity e) {
    return e == null
        ? null
        : (String)
            e.getPersistentDataContainer().get(this.keys.BASE_NAME, PersistentDataType.STRING);
  }

  public FileConfiguration mobConfigOf(LivingEntity mob) {
    if (mob == null) {
      return null;
    } else {
      String id = this.mobIdOf(mob);
      return id != null && !id.isBlank() ? this.mobConfigs.get(id.toLowerCase(Locale.ROOT)) : null;
    }
  }

  public FileConfiguration mobConfigById(String id) {
    return id == null ? null : this.mobConfigs.get(id.toLowerCase(Locale.ROOT));
  }

  public boolean mobExists(String id) {
    return id != null && this.mobConfigs.containsKey(id.toLowerCase(Locale.ROOT));
  }

  public Set<String> registeredIds() {
    return Collections.unmodifiableSet(this.mobConfigs.keySet());
  }

  public boolean hasBossTemplate(String mobId) {
    return mobId == null ? false : this.bossTemplates.containsKey(mobId.toLowerCase(Locale.ROOT));
  }

  public BossTemplate bossTemplate(String id) {
    return id == null ? null : this.bossTemplates.get(id.toLowerCase(Locale.ROOT));
  }

  public BossTemplate getBossTemplate(LivingEntity boss) {
    if (boss == null) {
      return null;
    } else {
      String mobId = this.mobIdOf(boss);
      return mobId == null ? null : this.bossTemplates.get(mobId.toLowerCase(Locale.ROOT));
    }
  }

  public boolean isCustomMob(LivingEntity e) {
    return e != null && this.mobIdOf(e) != null;
  }

  public String mobIdOf(LivingEntity e) {
    return e == null
        ? null
        : (String) e.getPersistentDataContainer().get(this.keys.MOB_ID, PersistentDataType.STRING);
  }

  public boolean isBoss(LivingEntity e) {
    if (e == null) {
      return false;
    } else {
      Integer flag =
          (Integer)
              e.getPersistentDataContainer().get(this.keys.IS_BOSS, PersistentDataType.INTEGER);
      if (flag != null) {
        return flag == 1;
      } else {
        String id = this.mobIdOf(e);
        return id != null && this.bossTemplates.containsKey(id.toLowerCase(Locale.ROOT));
      }
    }
  }

  public KeyRegistry keys() {
    return this.keys;
  }

  public List<String> getDeathCommands(LivingEntity mob) {
    if (mob == null) {
      return List.of();
    } else {
      String id = this.mobIdOf(mob);
      if (id == null) {
        return List.of();
      } else {
        FileConfiguration cfg = this.mobConfigs.get(id.toLowerCase(Locale.ROOT));
        if (cfg == null) {
          return List.of();
        } else if (!cfg.contains("death-commands")) {
          return List.of();
        } else {
          List<String> list = cfg.getStringList("death-commands");
          return list != null ? list : List.of();
        }
      }
    }
  }

  public void onMobDeath(LivingEntity mob, EntityDeathEvent e) {
    if (mob != null) {
      if (this.bossActionEngine != null && this.isBoss(mob)) {
        Integer done =
            (Integer)
                mob.getPersistentDataContainer()
                    .get(this.keys.BOSS_DEATH_HANDLED, PersistentDataType.INTEGER);
        if (done == null || done != 1) {
          mob.getPersistentDataContainer()
              .set(this.keys.BOSS_DEATH_HANDLED, PersistentDataType.INTEGER, 1);
          this.bossActionEngine.onBossDeath(mob);
        }
      }

      if (this.dropEngine != null) {
        this.dropEngine.handleDeath(mob, e);
      }

      if (this.healthDisplay != null) {
        this.healthDisplay.onDeath(mob);
      }

      if (this.autoSpawn != null) {
        this.autoSpawn.onMobDeath(mob);
      }
      untrack(mob.getUniqueId());
    }
  }

  public int killAll() {
    int removed = 0;

    for (World world : Bukkit.getWorlds()) {
      for (LivingEntity entity : world.getLivingEntities()) {
        if (!(entity instanceof Player)) {
          PersistentDataContainer pdc = entity.getPersistentDataContainer();
          boolean owned =
              pdc.has(this.keys.MOB_ID, PersistentDataType.STRING)
                  || pdc.has(this.keys.AUTO_SPAWN_ID, PersistentDataType.STRING);
          if (owned) {
            removeWithoutRewards(entity);
            removed++;
          }
        }
      }
    }

    if (this.autoSpawn != null) {
      this.autoSpawn.onKillAll();
    }

    return removed;
  }

  public void removeWithoutRewards(LivingEntity entity) {
    if (plugin instanceof org.plugin.theMob.TheMob owner) {
      if (isBoss(entity) && owner.bossPhases() != null) owner.bossPhases().onBossDeath(entity);
      if (owner.getStuckDefense() != null) owner.getStuckDefense().remove(entity);
    }
    if (autoSpawn != null) autoSpawn.onMobRemoved(entity);
    untrack(entity.getUniqueId());
    entity.remove();
  }

  public void hardReset() {
    this.killAll();
    this.spawnService = null;
    this.dropEngine = null;
    this.healthDisplay = null;
    this.autoSpawn = null;
    this.bossActionEngine = null;
  }

  public Collection<LivingEntity> getAllLivingMobs() {
    return List.copyOf(tracked.values());
  }
}
