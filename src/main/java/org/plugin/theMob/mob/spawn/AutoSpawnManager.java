package org.plugin.theMob.mob.spawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
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

    private static final long COLD_TICKS = 20L * 60L;
    private static final long FIRST_SPAWN_DELAY = 20L;

    private final JavaPlugin plugin;
    private final MobManager mobs;
    private final KeyRegistry keys;

    private final Map<String, SpawnPoint> points = new ConcurrentHashMap<>();
    private final Map<String, Integer> spawnedCount = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSpawnTick = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> alive = new ConcurrentHashMap<>();

    private final Map<String, Long> arenaEmptySince = new ConcurrentHashMap<>();
    private final Map<String, Long> spawnBlockedUntil = new ConcurrentHashMap<>();

    private volatile boolean started = false;

    public AutoSpawnManager(JavaPlugin plugin, MobManager mobs, KeyRegistry keys) {
        this.plugin = plugin;
        this.mobs = mobs;
        this.keys = keys;
    }

    // =====================================================
    // LIFECYCLE
    // =====================================================

    public void start() {
        if (started) return;
        started = true;

        new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void stop() {
        points.keySet().forEach(this::hardReset);
        points.clear();
        spawnedCount.clear();
        lastSpawnTick.clear();
        alive.clear();
        arenaEmptySince.clear();
        spawnBlockedUntil.clear();
        started = false;
    }

    // =====================================================
    // REGISTRATION
    // =====================================================

    public void register(SpawnPoint sp) {
        String id = sp.spawnId();
        long now = Bukkit.getCurrentTick();

        points.put(id, sp);
        spawnedCount.put(id, 0);
        lastSpawnTick.put(id, now);
        alive.put(id, ConcurrentHashMap.newKeySet());
        arenaEmptySince.remove(id);
        spawnBlockedUntil.put(id, 0L);
    }

    public void unregister(String spawnId) {
        hardReset(spawnId);
        points.remove(spawnId);
        spawnedCount.remove(spawnId);
        lastSpawnTick.remove(spawnId);
        alive.remove(spawnId);
        arenaEmptySince.remove(spawnId);
        spawnBlockedUntil.remove(spawnId);
    }

    // =====================================================
    // CORE LOOP
    // =====================================================

    private void tick() {
        long now = Bukkit.getCurrentTick();

        for (SpawnPoint sp : points.values()) {
            String id = sp.spawnId();
            Location base = sp.baseLocation();
            if (base == null || base.getWorld() == null) continue;

            boolean hot = false;
            for (Player p : base.getWorld().getPlayers()) {
                if (sp.isInsideArena(p.getLocation())) {
                    hot = true;
                    break;
                }
            }

            // ============================
            // 🔥 HOT
            // ============================
            if (hot) {

                Long emptySince = arenaEmptySince.get(id);
                if (emptySince != null && (now - emptySince) >= COLD_TICKS) {
                    hardReset(id);
                    spawnBlockedUntil.put(id, now + FIRST_SPAWN_DELAY);
                    lastSpawnTick.put(id, now);
                }

                arenaEmptySince.remove(id);

                if (now < spawnBlockedUntil.getOrDefault(id, 0L)) continue;

                int cnt = spawnedCount.getOrDefault(id, 0);
                if (cnt >= sp.maxSpawns()) continue;

                if (cnt == 0 || (now - lastSpawnTick.getOrDefault(id, 0L)) >= sp.intervalSeconds() * 20L) {
                    spawnOne(sp, id, now);
                }
                continue;
            }

            // ============================
            // ❄️ COLD
            // ============================
            if (!hot && spawnedCount.getOrDefault(id, 0) > 0) {
                arenaEmptySince.putIfAbsent(id, now);
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

        if (mob == null) {
            lastSpawnTick.put(id, now);
            return;
        }

        mob.setRemoveWhenFarAway(false);
        mob.setPersistent(true);
        mob.setAI(true);

        mob.getPersistentDataContainer().set(
                keys.SPAWN_TYPE,
                PersistentDataType.STRING,
                "AUTOSPAWN"
        );
        mob.getPersistentDataContainer().set(
                keys.AUTO_SPAWN_ID,
                PersistentDataType.STRING,
                id
        );

        alive.get(id).add(mob.getUniqueId());
        spawnedCount.put(id, spawnedCount.getOrDefault(id, 0) + 1);
        lastSpawnTick.put(id, now);
    }

    /**
     * 🔥 HARTER RESET
     * Entfernt ALLES, was zu diesem SpawnPoint gehört – inkl. Bosse
     */
    private void hardReset(String spawnId) {
        Set<UUID> set = alive.get(spawnId);
        if (set == null || set.isEmpty()) return;

        Bukkit.getWorlds().forEach(world -> {
            for (UUID uuid : set) {
                LivingEntity e = (LivingEntity) world.getEntity(uuid);
                if (e != null && !e.isDead()) {
                    e.remove();
                }
            }
        });

        set.clear();
        spawnedCount.put(spawnId, 0);
    }
}
