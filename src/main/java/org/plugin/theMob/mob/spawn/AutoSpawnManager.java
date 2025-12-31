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
import org.plugin.theMob.spawn.SpawnController;
import org.plugin.theMob.spawn.SpawnPoint;
import org.plugin.theMob.spawn.type.SpawnMode;
import org.plugin.theMob.spawn.type.SpawnType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class AutoSpawnManager {

    private static final long COLD_TICKS = 20L * 60L;
    private static final int RANDOM_WORLD_RADIUS = 1_000;

    private static final int RANDOM_ANYWHERE_TRIES = 6;

    private final TheMob plugin;
    private final MobManager mobs;
    private final KeyRegistry keys;
    private final BossLockService bossLocks;

    private final Random rnd = new Random();

    private final Map<String, SpawnPoint> points = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> alive = new ConcurrentHashMap<>();

    private final Map<String, Integer> spawnedTotal = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSpawnTick = new ConcurrentHashMap<>();
    private SpawnController controller;

    private final Set<String> hot = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> coldSince = new ConcurrentHashMap<>();
    private final Map<String, Set<Chunk>> forcedChunks = new ConcurrentHashMap<>();

    private final Map<String, Location> randomWorldAnchor = new ConcurrentHashMap<>();
    private final Map<String, Integer> messageTasks = new ConcurrentHashMap<>();
    private BukkitRunnable task;

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

        task = new BukkitRunnable() {
            @Override public void run() { tick(); }
        };
        task.runTaskTimer(plugin, 20L, 20L);
    }


    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        started = false;

        for (String id : points.keySet()) {
            hardKillAll(id);
            releaseArenaChunks(id);
            stopMessageTask(id);
        }
        points.clear();
        alive.clear();
        spawnedTotal.clear();
        lastSpawnTick.clear();
        hot.clear();
        coldSince.clear();
        forcedChunks.clear();
        randomWorldAnchor.clear();
        messageTasks.clear();
        started = false;
    }

    public void bindController(SpawnController controller) {
        this.controller = controller;
    }

    public void register(SpawnPoint sp) {
        points.put(sp.spawnId(), sp);
        alive.put(sp.spawnId(), ConcurrentHashMap.newKeySet());
        spawnedTotal.putIfAbsent(sp.spawnId(), 0);
        lastSpawnTick.put(sp.spawnId(), -1L);
    }

    public void unregister(String spawnId) {
        hardKillAll(spawnId);
        releaseArenaChunks(spawnId);
        randomWorldAnchor.remove(spawnId);
        stopMessageTask(spawnId);

        points.remove(spawnId);
        alive.remove(spawnId);
        spawnedTotal.remove(spawnId);
        lastSpawnTick.remove(spawnId);
        hot.remove(spawnId);
        coldSince.remove(spawnId);
    }

    public SpawnPoint get(String spawnId) {
        return points.get(spawnId);
    }

    public Collection<SpawnPoint> all() {
        return Collections.unmodifiableCollection(points.values());
    }

    private void tick() {
        long now = Bukkit.getCurrentTick();

        for (SpawnPoint sp : points.values()) {
            if (!sp.enabled()) continue;

            switch (sp.type()) {
                case FIXED_POINT -> tickFixedOrRadius(sp, now, false);
                case RANDOM_RADIUS -> tickFixedOrRadius(sp, now, true);
                case FOLLOW_PLAYER -> tickFollowPlayer(sp, now);
                case RANDOM_WORLD -> tickRandomWorld(sp, now);
            }
        }
    }

    // =========================================================
    // FIXED_POINT / RANDOM_RADIUS (HOT/COLD)
    // =========================================================
    private void tickFixedOrRadius(SpawnPoint sp, long now, boolean radiusMode) {
        Location base = sp.baseLocation();
        if (base == null || base.getWorld() == null) return;

        boolean hotNow = isHot(sp);
        boolean wasHot = hot.contains(sp.spawnId());

        if (hotNow && !wasHot) {
            forceLoadArenaChunks(sp);
            hot.add(sp.spawnId());
            coldSince.remove(sp.spawnId());
        }


        if (!hotNow && wasHot) {
            hot.remove(sp.spawnId());
            coldSince.put(sp.spawnId(), now);
        }

        if (!hotNow && coldSince.containsKey(sp.spawnId())
                && now - coldSince.get(sp.spawnId()) >= COLD_TICKS) {
            hardKillAll(sp.spawnId());
            releaseArenaChunks(sp.spawnId());
            coldSince.remove(sp.spawnId());
        }

        if (!hotNow) return;

        long last = lastSpawnTick.getOrDefault(sp.spawnId(), 0L);
        if (now - last < sp.intervalSeconds() * 20L) return;

        int total = spawnedTotal.getOrDefault(sp.spawnId(), 0);
        if (total >= sp.maxSpawns()) return;

        Location spawnLoc = radiusMode ? randomAroundBase(base, sp.minRadius(), sp.maxRadius()) : base;
        spawnOne(sp, spawnLoc, now);
    }

    private boolean isHot(SpawnPoint sp) {
        Location base = sp.baseLocation();
        if (base == null || base.getWorld() == null) return false;

        for (Player p : base.getWorld().getPlayers()) {
            if (!p.isDead() && sp.isInsideArena(p.getLocation())) return true;
        }
        return false;
    }

    private Location randomAroundBase(Location base, int minR, int maxR) {
        if (maxR <= 0) return base;
        int a = Math.max(0, minR);
        int b = Math.max(a, maxR);

        double dist = a + (b == a ? 0.0 : (rnd.nextDouble() * (b - a)));
        double ang = rnd.nextDouble() * Math.PI * 2.0;

        double dx = Math.cos(ang) * dist;
        double dz = Math.sin(ang) * dist;

        return base.clone().add(dx, 0.0, dz);
    }

    // =========================================================
    // FOLLOW_PLAYER
    // =========================================================
    private void tickFollowPlayer(SpawnPoint sp, long now) {
        Player target = Bukkit.getPlayerExact(sp.playerName());
        if (target == null || !target.isOnline() || target.isDead()) return;

        long last = lastSpawnTick.getOrDefault(sp.spawnId(), 0L);
        if (now - last < sp.intervalSeconds() * 20L) return;

        int aliveNow = alive.getOrDefault(sp.spawnId(), Set.of()).size();
        if (aliveNow >= sp.maxSpawns()) return;

        int total = spawnedTotal.getOrDefault(sp.spawnId(), 0);
        if (sp.mode() == SpawnMode.ONETIME && total >= sp.maxSpawns()) return;

        Location spawnLoc = randomAroundPlayer(
                target.getLocation(),
                sp.minDistance(),
                sp.maxDistance()
        );

        spawnOne(sp, spawnLoc, now);

        if (sp.message() != null && !sp.message().isBlank()) {
            target.sendMessage(color(sp.message()));
        }
    }


    private Location randomAroundPlayer(Location base, int minD, int maxD) {
        int a = Math.max(0, minD);
        int b = Math.max(a, maxD);

        double dist = a + (b == a ? 0.0 : (rnd.nextDouble() * (b - a)));
        double ang = rnd.nextDouble() * Math.PI * 2.0;

        double dx = Math.cos(ang) * dist;
        double dz = Math.sin(ang) * dist;

        Location loc = base.clone().add(dx, 0.0, dz);
        World w = loc.getWorld();
        if (w != null) {
            int y = Math.max(w.getMinHeight() + 1, Math.min(w.getMaxHeight() - 2, loc.getBlockY()));
            loc.setY(y);
        }
        return loc;
    }

    // =====================================================
    // RANDOM WORLD
    // =====================================================
    private void tickRandomWorld(SpawnPoint sp, long now) {
        World world = Bukkit.getWorld(sp.worldName());
        if (world == null) return;

        long last = lastSpawnTick.getOrDefault(sp.spawnId(), -1L);

        if (last != -1L && now - last < sp.intervalSeconds() * 20L) return;

        int aliveNow = alive.getOrDefault(sp.spawnId(), Set.of()).size();
        if (aliveNow >= sp.maxSpawns()) return;

        int total = spawnedTotal.getOrDefault(sp.spawnId(), 0);
        if (sp.mode() == SpawnMode.ONETIME && total >= sp.maxSpawns()) return;

        Location spawnLoc;

        if (sp.mode() == SpawnMode.ONETIME) {
            spawnLoc = randomWorldAnchor.get(sp.spawnId());
            if (spawnLoc == null) {
                spawnLoc = randomAnywhere(world, RANDOM_ANYWHERE_TRIES);
                if (spawnLoc == null) {
                    lastSpawnTick.put(sp.spawnId(), now);
                    return;
                }
            }
        } else {
            spawnLoc = randomAnywhere(world, RANDOM_ANYWHERE_TRIES);
            if (spawnLoc == null) {
                lastSpawnTick.put(sp.spawnId(), now);
                return;
            }
        }

        randomWorldAnchor.put(sp.spawnId(), spawnLoc);

        spawnOne(sp, spawnLoc, now);

        ensureMessageTask(sp);
    }

    // =====================================================
    // RANDOM POSITION (1'000 BLOCKS) - bounded tries, low spike
    // =====================================================
    private Location randomAnywhere(World world, int tries) {
        Location center = world.getSpawnLocation();
        int attempt = Math.max(1, tries);

        for (int i = 0; i < attempt; i++) {
            int x = center.getBlockX() + rnd.nextInt(RANDOM_WORLD_RADIUS * 2) - RANDOM_WORLD_RADIUS;
            int z = center.getBlockZ() + rnd.nextInt(RANDOM_WORLD_RADIUS * 2) - RANDOM_WORLD_RADIUS;

            int cx = x >> 4;
            int cz = z >> 4;

            Chunk chunk = world.getChunkAt(cx, cz);
            chunk.addPluginChunkTicket(plugin);

            try {
                int y = world.getHighestBlockYAt(x, z) + 1;

                y = Math.max(world.getMinHeight() + 1, Math.min(world.getMaxHeight() - 2, y));

                Location loc = new Location(world, x + 0.5, y, z + 0.5);

                if (!loc.getBlock().getType().isAir()) continue;

                if (!loc.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) continue;

                return loc;

            } finally {
                chunk.removePluginChunkTicket(plugin);
            }
        }

        return null;
    }

    // =========================================================
    // SPAWN CORE
    // =========================================================
    private void spawnOne(SpawnPoint sp, Location loc, long now) {
        LivingEntity mob = mobs.spawnCustomMob(sp.mobId(), sp.spawnId(), loc);
        if (mob == null) return;

        mob.setPersistent(true);
        mob.setRemoveWhenFarAway(false);

        mob.getPersistentDataContainer().set(
                keys.AUTO_SPAWN_ID,
                PersistentDataType.STRING,
                sp.spawnId()
        );

        alive.computeIfAbsent(sp.spawnId(), k -> ConcurrentHashMap.newKeySet())
                .add(mob.getUniqueId());

        spawnedTotal.put(sp.spawnId(), spawnedTotal.getOrDefault(sp.spawnId(), 0) + 1);
        lastSpawnTick.put(sp.spawnId(), now);

        if (sp.type() == SpawnType.RANDOM_WORLD && controller != null) {
            controller.updateRuntimeLocation(
                    sp.spawnId(),
                    loc.getBlockX(),
                    loc.getBlockY(),
                    loc.getBlockZ()
            );
        }

        if (mobs.isBoss(mob)
                && sp.type() != SpawnType.FOLLOW_PLAYER) {

            bossLocks.register(sp.spawnId(), mob);
        }

    }

    // =========================================================
    // KILL / CLEANUP
    // =========================================================
    private void hardKillAll(String spawnId) {
        SpawnPoint sp = points.get(spawnId);
        if (sp == null) return;

        World w = Bukkit.getWorld(sp.worldName());
        if (w != null) {
            for (LivingEntity e : w.getLivingEntities()) {
                if (e instanceof Player) continue;

                String id = e.getPersistentDataContainer()
                        .get(keys.AUTO_SPAWN_ID, PersistentDataType.STRING);

                if (spawnId.equals(id)) {
                    e.remove();
                }
            }
        }

        alive.computeIfAbsent(spawnId, k -> ConcurrentHashMap.newKeySet()).clear();

        if (sp.mode() != SpawnMode.ONETIME) {
            spawnedTotal.put(spawnId, 0);
        }
        lastSpawnTick.put(spawnId, -1L);

        bossLocks.release(spawnId);
    }

    private void forceLoadArenaChunks(SpawnPoint sp) {
        if (sp.type() != SpawnType.FIXED_POINT && sp.type() != SpawnType.RANDOM_RADIUS) return;

        Location c = sp.baseLocation();
        if (c == null || c.getWorld() == null) return;

        Set<Chunk> set = new HashSet<>();
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
        String spawnId = mob.getPersistentDataContainer().get(keys.AUTO_SPAWN_ID, PersistentDataType.STRING);
        if (spawnId == null) return;

        Set<UUID> set = alive.get(spawnId);
        if (set != null) set.remove(mob.getUniqueId());

        SpawnPoint sp = points.get(spawnId);

        if (sp != null
                && sp.type() == SpawnType.RANDOM_WORLD
                && sp.mode() == SpawnMode.ENDLESS) {

            if (alive.getOrDefault(spawnId, Set.of()).isEmpty()) {
                stopMessageTask(spawnId);
            }
        }

        if (sp != null
                && sp.type() == SpawnType.RANDOM_WORLD
                && sp.mode() == SpawnMode.ONETIME
                && alive.getOrDefault(spawnId, Set.of()).isEmpty()) {

            unregister(spawnId);

            if (controller != null) {
                controller.onRuntimeSpawnRemoved(spawnId);
            }
        }
    }

    public void releaseBossLock(LivingEntity mob) {
        String spawnId = mob.getPersistentDataContainer().get(keys.AUTO_SPAWN_ID, PersistentDataType.STRING);
        if (spawnId != null) bossLocks.release(spawnId);
    }

    public boolean isBossInHotArena(LivingEntity boss) {
        String id = boss.getPersistentDataContainer().get(keys.AUTO_SPAWN_ID, PersistentDataType.STRING);
        return id != null && hot.contains(id);
    }

    // =========================================================
    // RANDOM_WORLD MESSAGE LOOP
    // =========================================================
    private void ensureMessageTask(SpawnPoint sp) {
        if (sp.type() != SpawnType.RANDOM_WORLD) return;
        if (sp.message() == null || sp.message().isBlank()) return;
        if (messageTasks.containsKey(sp.spawnId())) return;

        int interval = Math.max(1, sp.messageTimerSeconds());

        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                plugin,
                () -> broadcastRandomWorldMessage(sp),
                20L,
                interval * 20L
        );

        messageTasks.put(sp.spawnId(), taskId);
    }

    private void stopMessageTask(String spawnId) {
        Integer tid = messageTasks.remove(spawnId);
        if (tid != null) Bukkit.getScheduler().cancelTask(tid);
    }

    private void broadcastRandomWorldMessage(SpawnPoint sp) {
        if (sp.message() == null || sp.message().isBlank()) return;

        Location loc = null;

        Set<UUID> set = alive.get(sp.spawnId());
        if (set != null && !set.isEmpty()) {
            UUID id = set.iterator().next();
            var ent = Bukkit.getEntity(id);
            if (ent instanceof LivingEntity le && le.isValid()) {
                loc = le.getLocation();
            }
        }

        if (loc == null) {
            loc = randomWorldAnchor.get(sp.spawnId());
        }

        if (loc == null) return;

        String msg = sp.message()
                .replace("{world}", loc.getWorld().getName())
                .replace("{x}", String.valueOf(loc.getBlockX()))
                .replace("{y}", String.valueOf(loc.getBlockY()))
                .replace("{z}", String.valueOf(loc.getBlockZ()));

        String colored = org.bukkit.ChatColor.translateAlternateColorCodes('&', msg);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(colored);
        }
    }
    public void onKillAll() {

        for (Integer taskId : messageTasks.values()) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        messageTasks.clear();
        randomWorldAnchor.clear();
        for (Set<UUID> set : alive.values()) {
            set.clear();
        }

        bossLocks.clearAll();
    }

    private String color(String s) {
        return s == null ? "" : org.bukkit.ChatColor.translateAlternateColorCodes('&', s);
    }
}
