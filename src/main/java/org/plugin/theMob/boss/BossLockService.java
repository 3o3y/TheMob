package org.plugin.theMob.boss;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BossLockService {

    private final Map<String, UUID> activeBossBySpawn = new ConcurrentHashMap<>();

    // =====================================================
    // LOCK
    // =====================================================

    public boolean hasBoss(String spawnId) {
        UUID id = activeBossBySpawn.get(spawnId);
        if (id == null) return false;

        LivingEntity e = find(id);
        if (e == null || !e.isValid() || e.isDead()) {
            activeBossBySpawn.remove(spawnId);
            return false;
        }
        return true;
    }

    public void register(String spawnId, LivingEntity boss) {
        if (spawnId == null || boss == null) return;
        activeBossBySpawn.put(spawnId, boss.getUniqueId());
    }

    public void release(String spawnId) {
        if (spawnId != null) {
            activeBossBySpawn.remove(spawnId);
        }
    }

    // =====================================================
    // HARD RESET (Reload safe)
    // =====================================================

    public void clearAll() {
        for (UUID id : activeBossBySpawn.values()) {
            LivingEntity e = find(id);
            if (e != null && e.isValid()) {
                e.remove();
            }
        }
        activeBossBySpawn.clear();
    }

    private LivingEntity find(UUID id) {
        for (World w : Bukkit.getWorlds()) {
            var e = w.getEntity(id);
            if (e instanceof LivingEntity le) return le;
        }
        return null;
    }
}
