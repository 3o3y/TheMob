package org.plugin.theMob.boss.minion;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.plugin.theMob.TheMob;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * v1.6 – Spigot-safe Minion tracking & cleanup
 */
public final class BossMinionController implements Listener {

    private final TheMob plugin;

    // bossId -> minion UUIDs
    private final Map<UUID, Set<UUID>> bossToMinions = new ConcurrentHashMap<>();

    // minionId -> bossId
    private final Map<UUID, UUID> minionToBoss = new ConcurrentHashMap<>();

    // bossId -> (phaseId -> minion UUIDs)
    private final Map<UUID, Map<String, Set<UUID>>> bossPhaseToMinions = new ConcurrentHashMap<>();

    public BossMinionController(TheMob plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // =====================================================
    // COUNTS (ANTI-ABUSE)
    // =====================================================

    public int countBossMinions(UUID bossId) {
        Set<UUID> set = bossToMinions.get(bossId);
        return set == null ? 0 : set.size();
    }

    public int countBossPhaseMinions(UUID bossId, String phaseId) {
        Map<String, Set<UUID>> phaseMap = bossPhaseToMinions.get(bossId);
        if (phaseMap == null) return 0;
        Set<UUID> set = phaseMap.get(phaseId);
        return set == null ? 0 : set.size();
    }

    // =====================================================
    // REGISTER / UNREGISTER
    // =====================================================

    public void register(UUID bossId, String phaseId, LivingEntity minion) {
        if (bossId == null || phaseId == null || minion == null) return;

        UUID minionId = minion.getUniqueId();

        bossToMinions
                .computeIfAbsent(bossId, k -> ConcurrentHashMap.newKeySet())
                .add(minionId);

        minionToBoss.put(minionId, bossId);

        bossPhaseToMinions
                .computeIfAbsent(bossId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(phaseId, k -> ConcurrentHashMap.newKeySet())
                .add(minionId);

        // Mark as minion (no drops / no XP)
        minion.getPersistentDataContainer().set(
                plugin.keys().NO_DROPS,
                PersistentDataType.INTEGER,
                1
        );
    }

    public void unregister(UUID minionId) {
        if (minionId == null) return;

        UUID bossId = minionToBoss.remove(minionId);
        if (bossId == null) return;

        Set<UUID> bossSet = bossToMinions.get(bossId);
        if (bossSet != null) {
            bossSet.remove(minionId);
            if (bossSet.isEmpty()) bossToMinions.remove(bossId);
        }

        Map<String, Set<UUID>> phaseMap = bossPhaseToMinions.get(bossId);
        if (phaseMap != null) {
            for (Iterator<Map.Entry<String, Set<UUID>>> it = phaseMap.entrySet().iterator(); it.hasNext();) {
                Map.Entry<String, Set<UUID>> e = it.next();
                e.getValue().remove(minionId);
                if (e.getValue().isEmpty()) it.remove();
            }
            if (phaseMap.isEmpty()) bossPhaseToMinions.remove(bossId);
        }
    }

    // =====================================================
    // HARD CLEANUP
    // =====================================================

    public void cleanupBoss(UUID bossId) {
        if (bossId == null) return;

        Set<UUID> minions = bossToMinions.remove(bossId);
        bossPhaseToMinions.remove(bossId);

        if (minions == null || minions.isEmpty()) return;

        for (UUID mid : new HashSet<>(minions)) {
            minionToBoss.remove(mid);

            for (org.bukkit.World w : Bukkit.getWorlds()) {
                org.bukkit.entity.Entity e = w.getEntity(mid);
                if (e instanceof LivingEntity le && le.isValid()) {
                    le.remove();
                }
            }
        }
    }

    // =====================================================
    // EVENTS
    // =====================================================

    @EventHandler
    public void onMinionDeath(EntityDeathEvent e) {
        if (e.getEntity() == null) return;
        unregister(e.getEntity().getUniqueId());
    }
}
