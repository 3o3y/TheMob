package org.plugin.theMob.mob.spawn;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.boss.BossLockService;
import org.plugin.theMob.core.KeyRegistry;
import org.plugin.theMob.mob.MobManager;
import org.plugin.theMob.spawn.SpawnPoint;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class AutoSpawnManager {

    private static final long COLD_TICKS = 20L * 60L;

    private final TheMob plugin;
    private final MobManager mobs;
    private final KeyRegistry keys;
    private final BossLockService bossLocks;

    private final Map<String, SpawnPoint> points = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> alive = new ConcurrentHashMap<>();

    // 🔒 RAID COUNTER (ever spawned)
    private final Map<String, Integer> spawnedTotal = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSpawnTick = new ConcurrentHashMap<>();

    private final Set<String> hotArenas = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> coldSince = new ConcurrentHashMap<>();
    private final Map<String, Set<Chunk>> forcedChunks = new ConcurrentHashMap<>();

    private boolean started;

    public AutoSpawnManager(TheMob plugin, MobManager mobs, KeyRegistry keys, BossLockService bossLocks) {
        this.plugin = plugin;
        this.mobs = mobs;
        this.keys = keys;
        this.bossLocks = bossLocks;
    }

    public void start() {
        if (started) return;
        started = true;

        new BukkitRunnable() {
            @Override public void run() { tick(); }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void stop() {
        for (String id : points.keySet()) {
            hardKillAll(id);
            releaseArenaChunks(id);
        }
        points.clear();
        alive.clear();
        spawnedTotal.clear();
        lastSpawnTick.clear();
        hotArenas.clear();
        coldSince.clear();
        forcedChunks.clear();
        started = false;
    }

    public void register(SpawnPoint sp) {
        points.put(sp.spawnId(), sp);
        alive.put(sp.spawnId(), ConcurrentHashMap.newKeySet());
        hardKillAll(sp.spawnId());
    }

    public void unregister(String spawnId) {
        hardKillAll(spawnId);
        releaseArenaChunks(spawnId);
        points.remove(spawnId);
        alive.remove(spawnId);
        spawnedTotal.remove(spawnId);
        lastSpawnTick.remove(spawnId);
        hotArenas.remove(spawnId);
        coldSince.remove(spawnId);
    }

    private void tick() {
        long now = Bukkit.getCurrentTick();

        for (SpawnPoint sp : points.values()) {
            String id = sp.spawnId();
            if (sp.baseLocation() == null) continue;

            boolean hotNow = isHot(sp);
            boolean wasHot = hotArenas.contains(id);

            if (hotNow && !wasHot) {
                hardKillAll(id);
                forceLoadArenaChunks(sp);
                hotArenas.add(id);
                coldSince.remove(id);
            }

            if (!hotNow && wasHot) {
                hotArenas.remove(id);
                coldSince.put(id, now);
            }

            if (!hotNow && coldSince.containsKey(id)
                    && now - coldSince.get(id) >= COLD_TICKS) {
                hardKillAll(id);
                releaseArenaChunks(id);
                coldSince.remove(id);
            }

            if (hotNow) {
                int spawnedEver = spawnedTotal.getOrDefault(id, 0);

                // 🔒 RAID LIMIT: no respawn after limit
                if (spawnedEver < sp.maxSpawns()
                        && now - lastSpawnTick.getOrDefault(id, 0L)
                        >= sp.intervalSeconds() * 20L) {
                    spawnOne(sp, id, now);
                }
            }
        }
    }

    private boolean isHot(SpawnPoint sp) {
        for (Player p : sp.baseLocation().getWorld().getPlayers()) {
            if (!p.isDead() && sp.isInsideArena(p.getLocation())) return true;
        }
        return false;
    }

    private void spawnOne(SpawnPoint sp, String id, long now) {
        LivingEntity mob = mobs.spawnCustomMob(sp.mobId(), id, sp.baseLocation());
        if (mob == null) return;

        mob.setPersistent(true);
        mob.setRemoveWhenFarAway(false);

        mob.getPersistentDataContainer().set(
                keys.AUTO_SPAWN_ID,
                PersistentDataType.STRING,
                id
        );

        alive.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet())
                .add(mob.getUniqueId());

        // 🔒 increment ONCE
        spawnedTotal.put(id, spawnedTotal.getOrDefault(id, 0) + 1);
        lastSpawnTick.put(id, now);

        if (mobs.isBoss(mob)) {
            bossLocks.register(id, mob);
            Bukkit.getScheduler().runTask(plugin,
                    () -> plugin.bossPhases().onBossSpawn(
                            mob, mobs.getBossTemplate(mob)));
        }
    }

    private void hardKillAll(String spawnId) {
        SpawnPoint sp = points.get(spawnId);
        if (sp == null) return;

        World w = sp.baseLocation().getWorld();
        if (w == null) return;

        for (LivingEntity e : w.getLivingEntities()) {
            if (e instanceof Player) continue;

            String id = e.getPersistentDataContainer().get(
                    keys.AUTO_SPAWN_ID,
                    PersistentDataType.STRING
            );
            if (spawnId.equals(id)) {
                e.remove();
            }
        }

        alive.computeIfAbsent(spawnId, k -> ConcurrentHashMap.newKeySet()).clear();

        // 🔁 RESET RAID
        spawnedTotal.put(spawnId, 0);
        lastSpawnTick.put(spawnId, 0L);
        bossLocks.release(spawnId);
    }

    private void forceLoadArenaChunks(SpawnPoint sp) {
        Set<Chunk> set = new HashSet<>();
        Location c = sp.baseLocation();
        World w = c.getWorld();
        int r = sp.arenaRadiusChunks();

        int cx = c.getBlockX() >> 4;
        int cz = c.getBlockZ() >> 4;

        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                Chunk chunk = w.getChunkAt(cx + x, cz + z);
                chunk.addPluginChunkTicket(plugin);
                set.add(chunk);
            }
        }
        forcedChunks.put(sp.spawnId(), set);
    }

    private void releaseArenaChunks(String spawnId) {
        Set<Chunk> set = forcedChunks.remove(spawnId);
        if (set == null) return;
        for (Chunk c : set) {
            c.removePluginChunkTicket(plugin);
        }
    }

    public void onMobDeath(LivingEntity mob) {
        String spawnId = mob.getPersistentDataContainer().get(
                keys.AUTO_SPAWN_ID,
                PersistentDataType.STRING
        );
        if (spawnId == null) return;

        Set<UUID> set = alive.get(spawnId);
        if (set != null) {
            set.remove(mob.getUniqueId());
        }

        // ❌ KEIN spawnedTotal-- !!
    }

    public void releaseBossLock(LivingEntity mob) {
        String spawnId = mob.getPersistentDataContainer().get(
                keys.AUTO_SPAWN_ID,
                PersistentDataType.STRING
        );
        if (spawnId != null) {
            bossLocks.release(spawnId);
        }
    }

    public boolean isBossInHotArena(LivingEntity boss) {
        String id = boss.getPersistentDataContainer().get(
                keys.AUTO_SPAWN_ID,
                PersistentDataType.STRING
        );
        return id != null && hotArenas.contains(id);
    }
}
