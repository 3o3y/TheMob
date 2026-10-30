package org.plugin.theMob.boss;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;

public final class BossLockService {
  private final Map<String, UUID> activeBossBySpawn = new ConcurrentHashMap<>();

  public boolean hasBoss(String spawnId) {
    if (spawnId == null) {
      return false;
    } else {
      UUID id = this.activeBossBySpawn.get(spawnId);
      if (id == null) {
        return false;
      } else {
        LivingEntity boss = this.getLiving(id);
        if (boss != null && boss.isValid() && !boss.isDead()) {
          return true;
        } else {
          this.activeBossBySpawn.remove(spawnId);
          return false;
        }
      }
    }
  }

  public void register(String spawnId, LivingEntity boss) {
    if (spawnId != null && boss != null) {
      if (boss.isValid()) {
        this.activeBossBySpawn.put(spawnId, boss.getUniqueId());
      }
    }
  }

  public void release(String spawnId) {
    if (spawnId != null) {
      this.activeBossBySpawn.remove(spawnId);
    }
  }

  public void clearAll() {
    for (UUID id : this.activeBossBySpawn.values()) {
      LivingEntity boss = this.getLiving(id);
      if (boss != null && boss.isValid()) {
        boss.remove();
      }
    }

    this.activeBossBySpawn.clear();
  }

  private LivingEntity getLiving(UUID id) {
    return Bukkit.getEntity(id) instanceof LivingEntity le ? le : null;
  }
}
