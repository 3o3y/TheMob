package org.plugin.theMob.boss;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BossLockService {

    // spawnId -> boss UUID
    private final Map<String, UUID> activeBossBySpawn = new ConcurrentHashMap<>();

    // =====================================================
    // QUERY
    // =====================================================

    public boolean hasBoss(String spawnId) {
        if (spawnId == null) return false;

        UUID id = activeBossBySpawn.get(spawnId);
        if (id == null) return false;

        LivingEntity boss = getLiving(id);
        if (boss == null || !boss.isValid() || boss.isDead()) {
            activeBossBySpawn.remove(spawnId);
            return false;
        }
        return true;
    }

    // =====================================================
    // LIFECYCLE
    // =====================================================

    public void register(String spawnId, LivingEntity boss) {
        if (spawnId == null || boss == null) return;
        if (!boss.isValid()) return;

        activeBossBySpawn.put(spawnId, boss.getUniqueId());
    }

    public void release(String spawnId) {
        if (spawnId != null) {
            activeBossBySpawn.remove(spawnId);
        }
    }

    public void clearAll() {
        for (UUID id : activeBossBySpawn.values()) {
            LivingEntity boss = getLiving(id);
            if (boss != null && boss.isValid()) {
                boss.remove();
            }
        }
        activeBossBySpawn.clear();
    }

    // =====================================================
    // INTERNAL
    // =====================================================

    private LivingEntity getLiving(UUID id) {
        var e = Bukkit.getEntity(id);
        return (e instanceof LivingEntity le) ? le : null;
    }
}
