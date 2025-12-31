package org.plugin.theMob.mob.spawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
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

    private static final long COLD_TICKS = 20L * 60L; // 60s

    private final TheMob plugin;
    private final MobManager mobs;
    private final KeyRegistry keys;
    private final BossLockService bossLocks;

    private final Map<String, SpawnPoint> points = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> alive = new ConcurrentHashMap<>();
    private final Map<String, Long> lastHotTick = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSpawnTick = new ConcurrentHashMap<>();
    private final Map<String, Integer> spawnedTotal = new ConcurrentHashMap<>();

    private final Set<String> hotArenas = ConcurrentHashMap.newKeySet();

    private volatile boolean started;

    public AutoSpawnManager(
            TheMob plugin,
            MobManager mobs,
            KeyRegistry keys,
            BossLockService bossLocks
    ) {
        this.plugin = plugin;
        this.mobs = mobs;
        this.keys = keys;
        this.bossLocks = bossLocks;
    }

    // LIFECYCLE

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
        alive.clear();
        lastHotTick.clear();
        lastSpawnTick.clear();
        spawnedTotal.clear();
        hotArenas.clear();
        started = false;
    }

    // REGISTRATION

    public void register(SpawnPoint sp) {
        String id = sp.spawnId();
        points.put(id, sp);
        alive.put(id, ConcurrentHashMap.newKeySet());
        lastHotTick.remove(id);
        lastSpawnTick.put(id, 0L);
        spawnedTotal.put(id, 0);
        hotArenas.remove(id);
    }

    public void unregister(String id) {
        hardReset(id);
        points.remove(id);
        alive.remove(id);
        lastHotTick.remove(id);
        lastSpawnTick.remove(id);
        spawnedTotal.remove(id);
        hotArenas.remove(id);
    }

    // CORE LOOP

    private void tick() {
        long now = Bukkit.getCurrentTick();

        for (SpawnPoint sp : points.values()) {
            String id = sp.spawnId();
            Location base = sp.baseLocation();
            if (base == null || base.getWorld() == null) continue;

            boolean hotNow = isHot(sp);
            boolean wasHot = hotArenas.contains(id);

            if (hotNow && !wasHot) {
                hotArenas.add(id);
                lastHotTick.put(id, now);
                notifyPlayerEnter(sp);
            }

            if (hotNow) {
                int spawned = spawnedTotal.getOrDefault(id, 0);
                if (spawned < sp.maxSpawns()) {
                    if (now - lastSpawnTick.getOrDefault(id, 0L)
                            >= sp.intervalSeconds() * 20L) {
                        spawnOne(sp, id, now);
                    }
                }
            }

            if (!hotNow && wasHot) {

                // 🔥 START COLD TIMER ONCE (on leave)
                lastHotTick.putIfAbsent(id, now);
            }

            if (!hotNow && wasHot) {
                long lastHot = lastHotTick.get(id);

                if (now - lastHot >= COLD_TICKS) {
                    hotArenas.remove(id);
                    lastHotTick.remove(id);
                    hardReset(id);
                }
            }



        }
    }

    // HOT CHECK

    private boolean isHot(SpawnPoint sp) {
        for (Player p : sp.baseLocation().getWorld().getPlayers()) {
            if (!p.isOnline() || p.isDead()) continue;
            if (sp.isInsideArena(p.getLocation())) return true;
        }
        return false;
    }

    // SPAWN

    private void spawnOne(SpawnPoint sp, String id, long now) {
        LivingEntity mob = mobs.spawnCustomMob(
                sp.mobId(),
                id,
                sp.baseLocation()
        );
        if (mob == null) return;

        mob.setRemoveWhenFarAway(false);
        mob.setPersistent(true);

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
        spawnedTotal.put(id, spawnedTotal.get(id) + 1);
        lastSpawnTick.put(id, now);

        if (mobs.isBoss(mob)) {
            bossLocks.register(id, mob);
            lastHotTick.put(id, now);

            Bukkit.getScheduler().runTask(plugin, () ->
                    plugin.bossPhases().onBossSpawn(
                            mob,
                            mobs.getBossTemplate(mob)
                    )
            );
        }
    }

    // HARD RESET

    private void hardReset(String id) {
        Set<UUID> set = alive.get(id);
        if (set != null) {
            for (UUID uuid : set) {
                for (var w : Bukkit.getWorlds()) {
                    Entity e = w.getEntity(uuid);
                    if (e instanceof LivingEntity le) le.remove();
                }
            }
            set.clear();
        }

        bossLocks.release(id);
        lastSpawnTick.put(id, 0L);
        spawnedTotal.put(id, 0);
    }

    private boolean hasAliveBoss(String id) {
        Set<UUID> set = alive.get(id);
        if (set == null) return false;

        for (UUID uuid : set) {
            for (var w : Bukkit.getWorlds()) {
                var e = w.getEntity(uuid);
                if (e instanceof LivingEntity le &&
                        mobs.isBoss(le) &&
                        le.isValid() &&
                        !le.isDead()) {
                    return true;
                }
            }
        }
        return false;
    }
    // PLAYER ENTER
    private void notifyPlayerEnter(SpawnPoint sp) {
        for (LivingEntity boss : getAliveBosses(sp.spawnId())) {
            for (Player p : sp.baseLocation().getWorld().getPlayers()) {
                if (sp.isInsideArena(p.getLocation())) {
                    plugin.bossPhases().onPlayerEnterArena(p, boss);
                }
            }
        }
    }

    private Collection<LivingEntity> getAliveBosses(String spawnId) {
        Set<UUID> set = alive.get(spawnId);
        if (set == null) return List.of();

        List<LivingEntity> result = new ArrayList<>();
        for (UUID uuid : set) {
            for (var w : Bukkit.getWorlds()) {
                var e = w.getEntity(uuid);
                if (e instanceof LivingEntity le && mobs.isBoss(le)) {
                    result.add(le);
                }
            }
        }
        return result;
    }
// DEATH CALLBACK (API for MobListener)
    public void onMobDeath(LivingEntity mob) {
        if (mob == null) return;

        String spawnId = mob.getPersistentDataContainer().get(
                keys.AUTO_SPAWN_ID,
                PersistentDataType.STRING
        );
        if (spawnId == null) return;

        Set<UUID> set = alive.get(spawnId);
        if (set != null) {
            set.remove(mob.getUniqueId());
        }
    }

    public void releaseBossLock(LivingEntity mob) {
        if (mob == null) return;

        String spawnId = mob.getPersistentDataContainer().get(
                keys.AUTO_SPAWN_ID,
                PersistentDataType.STRING
        );
        if (spawnId != null) {
            bossLocks.release(spawnId);
        }
    }

}
