package org.plugin.theMob.boss.minion;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.plugin.theMob.TheMob;

public final class BossMinionController implements Listener {
  private final TheMob plugin;
  private final Map<UUID, Set<UUID>> bossToMinions = new ConcurrentHashMap<>();
  private final Map<UUID, UUID> minionToBoss = new ConcurrentHashMap<>();
  private final Map<UUID, Map<String, Set<UUID>>> bossPhaseToMinions = new ConcurrentHashMap<>();

  public BossMinionController(TheMob plugin) {
    this.plugin = plugin;
    Bukkit.getPluginManager().registerEvents(this, plugin);
  }

  public int countBossMinions(UUID bossId) {
    Set<UUID> set = this.bossToMinions.get(bossId);
    return set == null ? 0 : set.size();
  }

  public int countBossPhaseMinions(UUID bossId, String phaseId) {
    Map<String, Set<UUID>> phaseMap = this.bossPhaseToMinions.get(bossId);
    if (phaseMap == null) {
      return 0;
    } else {
      Set<UUID> set = phaseMap.get(phaseId);
      return set == null ? 0 : set.size();
    }
  }

  public void register(UUID bossId, String phaseId, LivingEntity minion) {
    if (bossId != null && phaseId != null && minion != null) {
      UUID minionId = minion.getUniqueId();
      this.bossToMinions.computeIfAbsent(bossId, k -> ConcurrentHashMap.newKeySet()).add(minionId);
      this.minionToBoss.put(minionId, bossId);
      this.bossPhaseToMinions
          .computeIfAbsent(bossId, k -> new ConcurrentHashMap<>())
          .computeIfAbsent(phaseId, k -> ConcurrentHashMap.newKeySet())
          .add(minionId);
      // Respect the no-drops setting applied by the spawner.
    }
  }

  public void unregister(UUID minionId) {
    if (minionId != null) {
      UUID bossId = this.minionToBoss.remove(minionId);
      if (bossId != null) {
        Set<UUID> bossSet = this.bossToMinions.get(bossId);
        if (bossSet != null) {
          bossSet.remove(minionId);
          if (bossSet.isEmpty()) {
            this.bossToMinions.remove(bossId);
          }
        }

        Map<String, Set<UUID>> phaseMap = this.bossPhaseToMinions.get(bossId);
        if (phaseMap != null) {
          Iterator<Entry<String, Set<UUID>>> it = phaseMap.entrySet().iterator();

          while (it.hasNext()) {
            Entry<String, Set<UUID>> e = it.next();
            e.getValue().remove(minionId);
            if (e.getValue().isEmpty()) {
              it.remove();
            }
          }

          if (phaseMap.isEmpty()) {
            this.bossPhaseToMinions.remove(bossId);
          }
        }
      }
    }
  }

  public void tick() {
    Iterator<UUID> it = this.bossToMinions.keySet().iterator();

    while (it.hasNext()) {
      UUID bossId = it.next();
      LivingEntity boss = this.findLiving(bossId);
      if (boss == null || boss.isDead() || !boss.isValid()) {
        this.cleanupBoss(bossId);
        it.remove();
      }
    }

    Iterator<Entry<UUID, UUID>> entries = this.minionToBoss.entrySet().iterator();

    while (entries.hasNext()) {
      Entry<UUID, UUID> e = (Entry<UUID, UUID>) entries.next();
      UUID minionId = e.getKey();
      LivingEntity minion = this.findLiving(minionId);
      if (minion == null || minion.isDead() || !minion.isValid()) {
        this.unregister(minionId);
        entries.remove();
      }
    }
  }

  public void cleanupBoss(UUID bossId) {
    if (bossId != null) {
      Set<UUID> minions = this.bossToMinions.remove(bossId);
      this.bossPhaseToMinions.remove(bossId);
      if (minions != null && !minions.isEmpty()) {
        for (UUID mid : new HashSet<>(minions)) {
          this.minionToBoss.remove(mid);
          LivingEntity le = this.findLiving(mid);
          if (le != null) {
            le.remove();
          }
        }
      }
    }
  }

  @EventHandler
  public void onMinionDeath(EntityDeathEvent e) {
    LivingEntity entity = e.getEntity();
    if (entity != null) {
      if (this.minionToBoss.containsKey(entity.getUniqueId())
          && entity
                  .getPersistentDataContainer()
                  .getOrDefault(this.plugin.keys().NO_DROPS, PersistentDataType.INTEGER, 0)
              == 1) {
        e.getDrops().clear();
        e.setDroppedExp(0);
      }
      this.unregister(entity.getUniqueId());
    }
  }

  public void shutdown() {
    for (UUID bossId : new HashSet<>(this.bossToMinions.keySet())) this.cleanupBoss(bossId);
    this.bossToMinions.clear();
    this.bossPhaseToMinions.clear();
    this.minionToBoss.clear();
  }

  public void clearAll() {
    this.shutdown();
  }

  private LivingEntity findLiving(UUID id) {
    return Bukkit.getEntity(id) instanceof LivingEntity le && le.isValid() ? le : null;
  }
}
