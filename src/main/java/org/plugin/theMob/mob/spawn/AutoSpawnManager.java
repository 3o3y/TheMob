package org.plugin.theMob.mob.spawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
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

    private static final long COLD_TICKS = 20L * 60L;

    private final TheMob plugin;
    private final MobManager mobs;
    private final KeyRegistry keys;
    private final BossLockService bossLocks;

    private final Map<String, SpawnPoint> points = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> alive = new ConcurrentHashMap<>();
    private final Map<String, Integer> spawnedTotal = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSpawnTick = new ConcurrentHashMap<>();

    private final Set<String> hotArenas = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> coldSince = new ConcurrentHashMap<>();

    private boolean started;

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
        spawnedTotal.clear();
        lastSpawnTick.clear();
        hotArenas.clear();
        coldSince.clear();
        started = false;
    }

    public void register(SpawnPoint sp) {
        String id = sp.spawnId();

        points.put(id, sp);
        alive.put(id, ConcurrentHashMap.newKeySet());

        hardKillAll(id);

        spawnedTotal.put(id, 0);
        lastSpawnTick.put(id, 0L);
        hotArenas.remove(id);
        coldSince.remove(id);
    }

    public void unregister(String id) {
        hardKillAll(id);

        points.remove(id);
        alive.remove(id);
        spawnedTotal.remove(id);
        lastSpawnTick.remove(id);
        hotArenas.remove(id);
        coldSince.remove(id);
    }

    private void tick() {
        long now = Bukkit.getCurrentTick();

        for (SpawnPoint sp : points.values()) {
            String id = sp.spawnId();
            if (sp.baseLocation() == null || sp.baseLocation().getWorld() == null) continue;

            boolean hotNow = isHot(sp);
            boolean wasHot = hotArenas.contains(id);

            // 🔥 RE-ENTER → IMMER HARD RESET + KILL ALL
            if (hotNow && !wasHot) {
                hardKillAll(id);
                hotArenas.add(id);
                coldSince.remove(id);
            }

            // ❄ Leave arena
            if (!hotNow && wasHot) {
                hotArenas.remove(id);
                coldSince.put(id, now);
            }

            // ❄ Cold expired (failsafe)
            if (!hotNow && coldSince.containsKey(id)) {
                if (now - coldSince.get(id) >= COLD_TICKS) {
                    hardKillAll(id);
                    coldSince.remove(id);
                }
            }

            // 🔁 Spawning
            if (hotNow) {
                int spawned = spawnedTotal.getOrDefault(id, 0);
                if (spawned < sp.maxSpawns()) {
                    if (now - lastSpawnTick.getOrDefault(id, 0L)
                            >= sp.intervalSeconds() * 20L) {
                        spawnOne(sp, id, now);
                    }
                }
            }
        }
    }

    private boolean isHot(SpawnPoint sp) {
        for (Player p : sp.baseLocation().getWorld().getPlayers()) {
            if (!p.isOnline() || p.isDead()) continue;
            if (sp.isInsideArena(p.getLocation())) return true;
        }
        return false;
    }

    private void spawnOne(SpawnPoint sp, String id, long now) {
        LivingEntity mob = mobs.spawnCustomMob(sp.mobId(), id, sp.baseLocation());
        if (mob == null) return;

        mob.setRemoveWhenFarAway(false);
        mob.setPersistent(true);

        mob.getPersistentDataContainer().set(
                keys.AUTO_SPAWN_ID,
                PersistentDataType.STRING,
                id
        );

        alive.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet())
                .add(mob.getUniqueId());

        spawnedTotal.put(id, spawnedTotal.get(id) + 1);
        lastSpawnTick.put(id, now);

        if (mobs.isBoss(mob)) {
            bossLocks.register(id, mob);
            Bukkit.getScheduler().runTask(plugin,
                    () -> plugin.bossPhases().onBossSpawn(
                            mob, mobs.getBossTemplate(mob)));
        }
    }

    // 💀 ABSOLUTER KILL-ALL (NUR TheMob-Entities)
    private void hardKillAll(String spawnId) {
        SpawnPoint sp = points.get(spawnId);
        if (sp == null || sp.baseLocation() == null) return;

        Location center = sp.baseLocation();
        World world = center.getWorld();
        if (world == null) return;

        int baseCx = center.getBlockX() >> 4;
        int baseCz = center.getBlockZ() >> 4;
        int radius = sp.arenaRadiusChunks();

        int killed = 0;

        for (LivingEntity e : world.getLivingEntities()) {

            // 🚫 NIEMALS SPIELER
            if (e instanceof Player) continue;

            String id = e.getPersistentDataContainer().get(
                    keys.AUTO_SPAWN_ID,
                    PersistentDataType.STRING
            );

            // 🔒 Nur Mobs, die zu UNS gehören
            if (!spawnId.equals(id)) continue;

            int ecx = e.getLocation().getBlockX() >> 4;
            int ecz = e.getLocation().getBlockZ() >> 4;

            if (Math.abs(ecx - baseCx) <= radius
                    && Math.abs(ecz - baseCz) <= radius) {
                e.remove();
                killed++;
            }
        }

        alive.computeIfAbsent(spawnId, k -> ConcurrentHashMap.newKeySet()).clear();
        bossLocks.release(spawnId);
        spawnedTotal.put(spawnId, 0);
        lastSpawnTick.put(spawnId, 0L);

        plugin.getLogger().info(
                "[TheMob] HARD KILL-ALL spawnId=" + spawnId + " killed=" + killed
        );
    }


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
    private void hardReset(String spawnId) {
        hardKillAll(spawnId);
    }

}
