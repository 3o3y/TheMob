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

    private static final long COLD_TICKS = 20L * 60L;   // 60 Sekunden
    private static final long FIRST_SPAWN_DELAY = 20L; // 1 Sekunde

    private final JavaPlugin plugin;
    private final MobManager mobs;
    private final KeyRegistry keys;

    private final Map<String, SpawnPoint> points = new ConcurrentHashMap<>();
    private final Map<String, Integer> spawnedCount = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSpawnTick = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> alive = new ConcurrentHashMap<>();

    // ❄️ Arena leer seit Tick
    private final Map<String, Long> arenaEmptySince = new ConcurrentHashMap<>();

    // ⛔ Spawn-Sperre nach Reset
    private final Map<String, Long> spawnBlockedUntil = new ConcurrentHashMap<>();

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
        // nur beim Stop global
        mobs.killAll();

        points.clear();
        spawnedCount.clear();
        lastSpawnTick.clear();
        alive.clear();
        arenaEmptySince.clear();
        spawnBlockedUntil.clear();
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
        killSpawnPointMobs(spawnId);
        points.remove(spawnId);
        spawnedCount.remove(spawnId);
        lastSpawnTick.remove(spawnId);
        alive.remove(spawnId);
        arenaEmptySince.remove(spawnId);
        spawnBlockedUntil.remove(spawnId);
    }

    // =====================================================
    // CORE LOOP – 60s SAFE RE-ENTRY
    // =====================================================

    private void tick() {
        long now = Bukkit.getCurrentTick();

        for (SpawnPoint sp : points.values()) {
            String id = sp.spawnId();
            Location base = sp.baseLocation();
            if (base == null) continue;

            boolean playerInArena = false;
            for (Player p : base.getWorld().getPlayers()) {
                if (sp.isInsideArena(p.getLocation())) {
                    playerInArena = true;
                    break;
                }
            }

            // ============================
            // 🔥 HOT
            // ============================
            if (playerInArena) {

                Long emptySince = arenaEmptySince.get(id);

                // ❗ Reset NUR wenn ≥ 60s leer
                if (emptySince != null && (now - emptySince) >= COLD_TICKS) {

                    killSpawnPointMobs(id);
                    spawnedCount.put(id, 0);

                    // First-Spawn-Gate
                    spawnBlockedUntil.put(id, now + FIRST_SPAWN_DELAY);
                    lastSpawnTick.put(id, now);
                }

                arenaEmptySince.remove(id);

                // ⛔ Spawn noch blockiert
                long blockedUntil = spawnBlockedUntil.getOrDefault(id, 0L);
                if (now < blockedUntil) continue;

                if (spawnedCount.get(id) >= sp.maxSpawns()) continue;

                if (now - lastSpawnTick.get(id) >= sp.intervalSeconds() * 20L) {
                    spawnOne(sp, id, now);
                }
                continue;
            }

            // ============================
            // ❄️ COLD
            // ============================
            arenaEmptySince.putIfAbsent(id, now);
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

    private void killSpawnPointMobs(String spawnId) {
        Bukkit.getWorlds().forEach(world -> {
            for (LivingEntity e : world.getLivingEntities()) {
                String id = e.getPersistentDataContainer()
                        .get(keys.AUTO_SPAWN_ID, PersistentDataType.STRING);

                if (spawnId.equals(id)) {
                    e.remove();
                }
            }
        });
    }
}
