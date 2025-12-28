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

    private static final long RESET_TICKS = 20L * 60L; // 60s cold reset

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
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /**
     * 🔥 HARD STOP (Reload / Restart Safe)
     * Entfernt ALLE TheMob-Mobs aus allen Welten
     */
    public void stop() {

        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity e : world.getLivingEntities()) {

                if (!e.getPersistentDataContainer()
                        .has(keys.MOB_ID, PersistentDataType.STRING)) {
                    continue;
                }

                e.remove();
            }
        }

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
        lastSpawnTick.put(id, 0L);
        activeCycle.put(id, true);
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
    // PLAYER EXIT NOTIFY
    // =====================================================

    public void markPlayerLeft(SpawnPoint sp) {
        // bewusst leer – Cold-Timeout läuft im Tick
    }

    // =====================================================
    // DEATH TRACKING (NO REFILL)
    // =====================================================

    public void onEntityDeath(LivingEntity e) {
        String id = e.getPersistentDataContainer()
                .get(keys.AUTO_SPAWN_ID, PersistentDataType.STRING);
        if (id == null) return;

        Set<UUID> set = alive.get(id);
        if (set != null) set.remove(e.getUniqueId());
    }

    // =====================================================
    // CORE LOOP
    // =====================================================

    private void tick() {
        long now = Bukkit.getCurrentTick();

        for (SpawnPoint sp : points.values()) {
            if (!sp.enabled()) continue;

            Location base = sp.baseLocation();
            if (base == null) continue;

            World world = base.getWorld();
            boolean playerNearby = false;

            for (Player p : world.getPlayers()) {
                if (isInArena(p.getLocation(), sp)) {
                    playerNearby = true;
                    sp.markPlayerSeen(now);
                    break;
                }
            }

            String id = sp.spawnId();

            // =================================================
            // 🔒 BOSS FAILSAFE
            // Wenn ein Boss im Arena-Radius lebt → KEIN RESET
            // =================================================
            if (hasLivingBossInArena(sp)) {
                continue;
            }

            // -------------------------------
            // COLD RESET
            // -------------------------------
            if (!playerNearby && sp.inactiveFor(RESET_TICKS, now)) {
                hardReset(sp, now);
                continue;
            }

            if (!playerNearby || !Boolean.TRUE.equals(activeCycle.get(id))) {
                continue;
            }

            // -------------------------------
            // SPAWN LOGIC
            // -------------------------------
            if (spawnedCount.get(id) >= sp.maxSpawns()) {
                activeCycle.put(id, false);
                continue;
            }

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

    /**
     * 🔥 HARD RESET
     * Löscht ALLE TheMob-Custom-Mobs im Arena-Radius
     * (Bosses sind hier bereits ausgeschlossen!)
     */
    private void hardReset(SpawnPoint sp, long now) {
        World world = sp.baseLocation().getWorld();
        if (world == null) return;

        int baseCx = sp.baseChunkX();
        int baseCz = sp.baseChunkZ();
        int radius = sp.arenaRadiusChunks();

        for (LivingEntity e : world.getLivingEntities()) {

            if (!e.getPersistentDataContainer()
                    .has(keys.MOB_ID, PersistentDataType.STRING)) {
                continue;
            }

            // ❌ Boss NIE löschen
            if (e.getPersistentDataContainer()
                    .has(keys.IS_BOSS, PersistentDataType.INTEGER)) {
                continue;
            }

            int cx = e.getLocation().getBlockX() >> 4;
            int cz = e.getLocation().getBlockZ() >> 4;

            if (Math.abs(cx - baseCx) <= radius
                    && Math.abs(cz - baseCz) <= radius) {
                e.remove();
            }
        }

        String id = sp.spawnId();
        alive.get(id).clear();
        spawnedCount.put(id, 0);
        activeCycle.put(id, true);
        lastSpawnTick.put(id, now);
    }

    private boolean isInArena(Location loc, SpawnPoint sp) {
        int cx = loc.getBlockX() >> 4;
        int cz = loc.getBlockZ() >> 4;

        return Math.abs(cx - sp.baseChunkX()) <= sp.arenaRadiusChunks()
                && Math.abs(cz - sp.baseChunkZ()) <= sp.arenaRadiusChunks();
    }

    /**
     * 🔒 Prüft, ob ein lebender Boss im Arena-Radius existiert
     */
    private boolean hasLivingBossInArena(SpawnPoint sp) {
        World world = sp.baseLocation().getWorld();
        if (world == null) return false;

        int baseCx = sp.baseChunkX();
        int baseCz = sp.baseChunkZ();
        int radius = sp.arenaRadiusChunks();

        for (LivingEntity e : world.getLivingEntities()) {

            if (!e.getPersistentDataContainer()
                    .has(keys.IS_BOSS, PersistentDataType.INTEGER)) {
                continue;
            }

            int cx = e.getLocation().getBlockX() >> 4;
            int cz = e.getLocation().getBlockZ() >> 4;

            if (Math.abs(cx - baseCx) <= radius
                    && Math.abs(cz - baseCz) <= radius) {
                return true;
            }
        }
        return false;
    }
}
