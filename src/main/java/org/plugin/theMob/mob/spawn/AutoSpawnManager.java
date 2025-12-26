package org.plugin.theMob.mob.spawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
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

    private static final long RESET_TICKS = 20L * 60L; // 60s

    private final JavaPlugin plugin;
    private final MobManager mobs;
    private final KeyRegistry keys;

    private final Map<String, SpawnPoint> points = new ConcurrentHashMap<>();
    private final Map<String, Integer> spawnedCount = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSpawnTick = new ConcurrentHashMap<>();
    private final Map<String, Boolean> activeCycle = new ConcurrentHashMap<>();
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
            @Override public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void stop() {
        points.clear();
        spawnedCount.clear();
        lastSpawnTick.clear();
        activeCycle.clear();
        alive.clear();
    }
    // =====================================================
    // REGISTRATION
    // =====================================================

    public void register(SpawnPoint sp) {
        String id = sp.spawnId();
        points.put(id, sp);
        spawnedCount.put(id, 0);
        lastSpawnTick.put(id, (long) Bukkit.getCurrentTick());
        activeCycle.put(id, true); // START IMMEDIATELY
        alive.put(id, ConcurrentHashMap.newKeySet());
    }

    public void unregister(String spawnId) {
        points.remove(spawnId);
        spawnedCount.remove(spawnId);
        lastSpawnTick.remove(spawnId);
        activeCycle.remove(spawnId);
        alive.remove(spawnId);
    }
    // =====================================================
    // DEATH TRACKING (NO REFILL!)
    // =====================================================

    public void onEntityDeath(LivingEntity e) {
        String id = e.getPersistentDataContainer()
                .get(keys.AUTO_SPAWN_ID, PersistentDataType.STRING);
        if (id == null) return;

        Set<UUID> set = alive.get(id);
        if (set != null) set.remove(e.getUniqueId());
    }
    // =====================================================
    // CORE TICK
    // =====================================================

    private void tick() {
        long now = Bukkit.getCurrentTick();

        for (SpawnPoint sp : points.values()) {
            if (!sp.enabled()) continue;

            String id = sp.spawnId();
            Location base = sp.baseLocation();
            if (base == null) continue;

            World world = base.getWorld();
            if (world == null) continue;

            boolean playerNearby = false;

            int cx = sp.baseChunkX();
            int cz = sp.baseChunkZ();

            for (Player p : world.getPlayers()) {
                int pcx = p.getLocation().getBlockX() >> 4;
                int pcz = p.getLocation().getBlockZ() >> 4;

                if (Math.abs(pcx - cx) <= sp.arenaRadiusChunks()
                        && Math.abs(pcz - cz) <= sp.arenaRadiusChunks()) {
                    playerNearby = true;
                    sp.markPlayerSeen(now);
                    break;
                }
            }
            if (!playerNearby && sp.inactiveFor(RESET_TICKS, now)) {

                World w = base.getWorld();
                if (w != null) {
                    for (LivingEntity e : w.getLivingEntities()) {

                        String sid = e.getPersistentDataContainer()
                                .get(keys.AUTO_SPAWN_ID, PersistentDataType.STRING);

                        if (id.equals(sid)) {
                            e.remove();
                        }
                    }
                }
                alive.get(id).clear();
                spawnedCount.put(id, 0);
                activeCycle.put(id, true);
                lastSpawnTick.put(id, now);

                continue;
            }
            if (!playerNearby) {
                continue;
            }

            if (Boolean.TRUE.equals(activeCycle.get(id))) {

                int spawned = spawnedCount.get(id);
                if (spawned >= sp.maxSpawns()) {
                    activeCycle.put(id, false);
                    continue;
                }

                long last = lastSpawnTick.get(id);
                long intervalTicks = sp.intervalSeconds() * 20L;

                if (now - last >= intervalTicks) {
                    spawnOne(sp);
                    spawnedCount.put(id, spawned + 1);
                    lastSpawnTick.put(id, now);
                }
            }
        }
    }


    private void spawnOne(SpawnPoint sp) {
        LivingEntity mob = mobs.spawnCustomMob(
                sp.mobId(),
                sp.spawnId(),
                sp.baseLocation()
        );
        if (mob == null) return;

        mob.getPersistentDataContainer()
                .set(keys.AUTO_SPAWN_ID, PersistentDataType.STRING, sp.spawnId());

        alive.get(sp.spawnId()).add(mob.getUniqueId());
    }
    public void forceCleanupIfEmpty(SpawnPoint sp) {
        Location base = sp.baseLocation();
        if (base == null || base.getWorld() == null) return;

        World world = base.getWorld();

        int cx = sp.baseChunkX();
        int cz = sp.baseChunkZ();
        int radius = sp.arenaRadiusChunks();

        for (Player p : world.getPlayers()) {
            int pcx = p.getLocation().getBlockX() >> 4;
            int pcz = p.getLocation().getBlockZ() >> 4;

            if (Math.abs(pcx - cx) <= radius
                    && Math.abs(pcz - cz) <= radius) {
                return; // ❌ noch Spieler da
            }
        }

        for (LivingEntity e : world.getLivingEntities()) {

            if (!e.getPersistentDataContainer()
                    .has(keys.MOB_ID, PersistentDataType.STRING)) {
                continue;
            }

            if (isInArenaChunks(e.getLocation(), sp)) {
                e.remove();
            }

        }

        String id = sp.spawnId();
        Set<UUID> set = alive.get(id);
        if (set != null) set.clear();

        spawnedCount.put(id, 0);
        activeCycle.put(id, true);
        lastSpawnTick.put(id, (long) Bukkit.getCurrentTick());
    }
    private boolean isInArenaChunks(Location loc, SpawnPoint sp) {
        if (loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(sp.worldName())) return false;

        int cx = loc.getBlockX() >> 4;
        int cz = loc.getBlockZ() >> 4;

        return Math.abs(cx - sp.baseChunkX()) <= sp.arenaRadiusChunks()
                && Math.abs(cz - sp.baseChunkZ()) <= sp.arenaRadiusChunks();
    }
}
