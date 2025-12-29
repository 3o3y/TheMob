package org.plugin.theMob.mob.spawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.plugin.theMob.core.KeyRegistry;
import org.plugin.theMob.mob.MobManager;
import org.plugin.theMob.spawn.SpawnPoint;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AutoSpawnManager {

    private static final long RESET_TICKS = 20L * 60L; // 60s cold

    private final JavaPlugin plugin;
    private final MobManager mobs;
    private final KeyRegistry keys;

    private final Map<String, SpawnPoint> points = new ConcurrentHashMap<>();
    private final Map<String, Integer> spawnedCount = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSpawnTick = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> alive = new ConcurrentHashMap<>();

    public AutoSpawnManager(JavaPlugin plugin, MobManager mobs, KeyRegistry keys) {
        this.plugin = plugin;
        this.mobs = mobs;
        this.keys = keys;
    }

    // =====================================================
    // LIFECYCLE
    // =====================================================

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void stop() {
        points.clear();
        spawnedCount.clear();
        lastSpawnTick.clear();
        alive.clear();
    }

    // =====================================================
    // REGISTRATION
    // =====================================================

    public void register(SpawnPoint sp) {
        String id = sp.spawnId();
        points.put(id, sp);
        spawnedCount.put(id, 0);
        lastSpawnTick.put(id, 0L);
        alive.put(id, ConcurrentHashMap.newKeySet());
    }

    public void unregister(String spawnId) {
        points.remove(spawnId);
        spawnedCount.remove(spawnId);
        lastSpawnTick.remove(spawnId);
        alive.remove(spawnId);
    }

    // =====================================================
    // CORE LOOP (v1.3)
    // =====================================================

    private void tick() {
        long now = Bukkit.getCurrentTick();

        for (SpawnPoint sp : points.values()) {
            if (!sp.enabled()) continue;

            Location base = sp.baseLocation();
            if (base == null) continue;

            World world = base.getWorld();
            boolean worldHot = !world.getPlayers().isEmpty();

            if (worldHot) {
                sp.markPlayerSeen(now);
            }

            // -------------------------------
            // COLD RESET
            // -------------------------------
            if (!worldHot && sp.inactiveFor(RESET_TICKS, now)) {
                reset(sp);
                continue;
            }

            if (!worldHot) continue;

            String id = sp.spawnId();

            // -------------------------------
            // LIMIT
            // -------------------------------
            if (spawnedCount.get(id) >= sp.maxSpawns()) {
                continue;
            }

            // -------------------------------
            // INTERVAL SPAWN (1 MOB)
            // -------------------------------
            if (now - lastSpawnTick.get(id) >= sp.intervalSeconds() * 20L) {
                spawnOne(sp, id, now);
            }
        }
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private void spawnOne(SpawnPoint sp, String id, long now) {
        LivingEntity mob = mobs.spawnCustomMob(
                sp.mobId(),
                id,
                sp.baseLocation()
        );
        if (mob == null) return;

        mob.getPersistentDataContainer()
                .set(keys.AUTO_SPAWN_ID, PersistentDataType.STRING, id);

        alive.get(id).add(mob.getUniqueId());
        spawnedCount.put(id, spawnedCount.get(id) + 1);
        lastSpawnTick.put(id, now);
    }

    private void reset(SpawnPoint sp) {
        String id = sp.spawnId();

        alive.get(id).forEach(uuid -> {
            LivingEntity e = (LivingEntity) Bukkit.getEntity(uuid);
            if (e != null && e.isValid()) {
                e.remove();
            }
        });

        alive.get(id).clear();
        spawnedCount.put(id, 0);
        lastSpawnTick.put(id, 0L);
    }
}
