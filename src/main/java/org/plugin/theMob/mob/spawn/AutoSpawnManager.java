package org.plugin.theMob.mob.spawn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
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
import org.plugin.theMob.world.ChunkTicketTracker;

public final class AutoSpawnManager {
  private static final long COLD_TICKS = 1200L;
  private static final int RANDOM_WORLD_RADIUS = 1000;
  private static final int RANDOM_ANYWHERE_TRIES = 6;
  private static final long SCHEDULER_PERIOD_TICKS = 20L;
  private final TheMob plugin;
  private final MobManager mobs;
  private final KeyRegistry keys;
  private final BossLockService bossLocks;
  private final ChunkTicketTracker chunkTickets;
  private final Random rnd = new Random();
  private final Map<String, SpawnPoint> points = new ConcurrentHashMap<>();
  private final Map<String, Set<UUID>> alive = new ConcurrentHashMap<>();
  private final Map<String, Integer> spawnedTotal = new ConcurrentHashMap<>();
  private final Map<String, Long> nextDueTick = new ConcurrentHashMap<>();
  private final Map<String, Long> lastSpawnTick = new ConcurrentHashMap<>();
  private final Map<String, Long> arenaEpoch = new java.util.HashMap<>();

  public long arenaEpoch(String id) {
    return arenaEpoch.getOrDefault(id, 0L);
  }

  public boolean currentEpoch(LivingEntity entity) {
    String id =
        entity.getPersistentDataContainer().get(keys.AUTO_SPAWN_ID, PersistentDataType.STRING);
    if (id == null || !arenaEpoch.containsKey(id)) return true;
    long epoch =
        entity
            .getPersistentDataContainer()
            .getOrDefault(
                new org.bukkit.NamespacedKey(plugin, "arena_epoch"), PersistentDataType.LONG, 0L);
    return epoch == arenaEpoch(id);
  }

  private SpawnController controller;
  private final Set<String> hot = ConcurrentHashMap.newKeySet();
  private final Map<String, Long> coldSince = new ConcurrentHashMap<>();
  private final Map<String, Set<Chunk>> forcedChunks = new ConcurrentHashMap<>();
  private final Map<String, Location> randomWorldAnchor = new ConcurrentHashMap<>();
  private final Map<String, Integer> messageTasks = new ConcurrentHashMap<>();
  private BukkitRunnable task;
  private boolean started;

  public AutoSpawnManager(
      TheMob plugin, MobManager mobs, KeyRegistry keys, BossLockService bossLocks) {
    this.plugin = plugin;
    this.mobs = mobs;
    this.keys = keys;
    this.bossLocks = bossLocks;
    this.chunkTickets = new ChunkTicketTracker(plugin);
  }

  public void start() {
    if (!this.started) {
      this.started = true;
      Bukkit.getScheduler().runTask(this.plugin, this::purgeAllStaleEntities);
      this.task =
          new BukkitRunnable() {
            public void run() {
              AutoSpawnManager.this.tick();
            }
          };
      this.task.runTaskTimer(this.plugin, 20L, 20L);
    }
  }

  public void stop() {
    if (this.task != null) {
      this.task.cancel();
      this.task = null;
    }

    this.started = false;

    for (String id : new ArrayList<>(this.points.keySet())) {
      this.hardKillAll(id);
      this.releaseArenaChunks(id);
      this.stopMessageTask(id);
    }

    this.points.clear();
    this.arenaEpoch.clear();
    this.alive.clear();
    this.spawnedTotal.clear();
    this.nextDueTick.clear();
    this.lastSpawnTick.clear();
    this.hot.clear();
    this.coldSince.clear();
    this.forcedChunks.clear();
    this.randomWorldAnchor.clear();
    this.chunkTickets.releaseAll();

    for (Integer taskId : this.messageTasks.values()) {
      Bukkit.getScheduler().cancelTask(taskId);
    }

    this.messageTasks.clear();
    this.purgeAllStaleEntities();
  }

  public void bindController(SpawnController controller) {
    this.controller = controller;
  }

  public void register(SpawnPoint sp) {
    arenaEpoch.putIfAbsent(sp.spawnId(), 0L);
    this.points.put(sp.spawnId(), sp);
    this.alive.put(sp.spawnId(), ConcurrentHashMap.newKeySet());
    this.spawnedTotal.putIfAbsent(sp.spawnId(), 0);
    long now = plugin.automation().tps().currentTick();
    long interval = Math.max(1, sp.intervalSeconds()) * 20L;
    this.lastSpawnTick.put(sp.spawnId(), -1L);
    this.nextDueTick.put(sp.spawnId(), now + interval);
  }

  public void unregister(String spawnId) {
    this.hardKillAll(spawnId);
    this.releaseArenaChunks(spawnId);
    this.randomWorldAnchor.remove(spawnId);
    this.stopMessageTask(spawnId);
    this.points.remove(spawnId);
    this.alive.remove(spawnId);
    this.spawnedTotal.remove(spawnId);
    this.nextDueTick.remove(spawnId);
    this.lastSpawnTick.remove(spawnId);
    this.hot.remove(spawnId);
    this.coldSince.remove(spawnId);
    this.forcedChunks.remove(spawnId);
  }

  public SpawnPoint get(String spawnId) {
    return this.points.get(spawnId);
  }

  public Collection<SpawnPoint> all() {
    return Collections.unmodifiableCollection(this.points.values());
  }

  private void tick() {
    long now = plugin.automation().tps().currentTick();

    for (SpawnPoint sp : this.points.values()) {
      if (sp.enabled()) {
        switch (sp.type()) {
          case FIXED_POINT:
            this.tickFixedOrRadius(sp, now, false);
            break;
          case RANDOM_RADIUS:
            this.tickFixedOrRadius(sp, now, true);
            break;
          case FOLLOW_PLAYER:
            this.tickFollowPlayer(sp, now);
            break;
          case RANDOM_WORLD:
            this.tickRandomWorld(sp, now);
        }
      }
    }
  }

  private void tickFixedOrRadius(SpawnPoint sp, long now, boolean radiusMode) {
    Location base = sp.baseLocation();
    if (base != null && base.getWorld() != null) {
      boolean hotNow = this.isHot(sp);
      boolean wasHot = this.hot.contains(sp.spawnId());
      if (hotNow) {
        if (!wasHot) {
          boolean hardCold =
              this.coldSince.containsKey(sp.spawnId())
                  && now - this.coldSince.get(sp.spawnId()) >= 1200L;
          if (hardCold) {
            this.hardKillAll(sp.spawnId());
            this.purgeArenaVisuals(sp);
            this.bossLocks.release(sp.spawnId());
            this.alive.computeIfAbsent(sp.spawnId(), k -> ConcurrentHashMap.newKeySet()).clear();
            this.spawnedTotal.put(sp.spawnId(), 0);
            this.lastSpawnTick.put(sp.spawnId(), -1L);
          }

          this.hot.add(sp.spawnId());
          this.forceLoadArenaChunks(sp);
        }

        this.coldSince.remove(sp.spawnId());
        this.nextDueTick.putIfAbsent(sp.spawnId(), now + Math.max(1, sp.intervalSeconds()) * 20L);
      }

      if (!hotNow && wasHot) {
        this.hot.remove(sp.spawnId());
        this.coldSince.put(sp.spawnId(), now);
        this.releaseArenaChunks(sp.spawnId());
      } else if (!hotNow
          && coldSince.containsKey(sp.spawnId())
          && now - coldSince.get(sp.spawnId()) >= COLD_TICKS) {
        hardKillAll(sp.spawnId());
      } else if (hotNow) {
        long due = this.nextDueTick.getOrDefault(sp.spawnId(), now);
        if (now >= due) {
          int total = this.spawnedTotal.getOrDefault(sp.spawnId(), 0);
          if (total >= sp.maxSpawns()) {
            this.scheduleNext(sp, now);
          } else {
            Location spawnLoc =
                radiusMode ? this.randomAroundBase(base, sp.minRadius(), sp.maxRadius()) : base;
            this.spawnOne(sp, spawnLoc, now);
            this.scheduleNext(sp, now);
          }
        }
      }
    }
  }

  private void scheduleNext(SpawnPoint sp, long now) {
    long interval =
        org.plugin.theMob.spawn.SpawnSchedule.intervalTicks(
            sp.intervalSeconds(), this.plugin.automation().gate().effectiveIntervalMultiplier());
    this.nextDueTick.put(sp.spawnId(), now + interval);
  }

  private boolean isHot(SpawnPoint sp) {
    Location base = sp.baseLocation();
    if (base != null && base.getWorld() != null) {
      for (Player p : base.getWorld().getPlayers()) {
        if (!p.isDead() && sp.isInsideArena(p.getLocation())) {
          return true;
        }
      }

      return false;
    } else {
      return false;
    }
  }

  private Location randomAroundBase(Location base, int minR, int maxR) {
    if (maxR <= 0) {
      return base;
    } else {
      int a = Math.max(0, minR);
      int b = Math.max(a, maxR);
      double dist = a + (b == a ? 0.0 : this.rnd.nextDouble() * (b - a));
      double ang = this.rnd.nextDouble() * Math.PI * 2.0;
      double dx = Math.cos(ang) * dist;
      double dz = Math.sin(ang) * dist;
      Location loc = base.clone().add(dx, 0.0, dz);
      World w = loc.getWorld();
      if (w != null) {
        int y = w.getHighestBlockYAt(loc) + 1;
        y = Math.max(w.getMinHeight() + 1, Math.min(w.getMaxHeight() - 2, y));
        loc.setY(y);
      }

      return loc;
    }
  }

  private void tickFollowPlayer(SpawnPoint sp, long now) {
    Player target = Bukkit.getPlayerExact(sp.playerName());
    if (target != null && target.isOnline() && !target.isDead()) {
      long due = this.nextDueTick.getOrDefault(sp.spawnId(), -1L);
      if (due == -1L) {
        this.nextDueTick.put(sp.spawnId(), now + Math.max(1, sp.intervalSeconds()) * 20L);
        due = this.nextDueTick.get(sp.spawnId());
      }

      if (now >= due) {
        int aliveNow = this.alive.getOrDefault(sp.spawnId(), Set.of()).size();
        if (aliveNow >= sp.maxSpawns()) {
          this.scheduleNext(sp, now);
        } else {
          int total = this.spawnedTotal.getOrDefault(sp.spawnId(), 0);
          if (sp.mode() == SpawnMode.ONETIME && total >= sp.maxSpawns()) {
            this.scheduleNext(sp, now);
          } else {
            Location spawnLoc =
                this.randomAroundPlayer(target.getLocation(), sp.minDistance(), sp.maxDistance());
            this.spawnOne(sp, spawnLoc, now);
            if (sp.message() != null && !sp.message().isBlank()) {
              target.sendMessage(this.color(sp.message()));
            }

            this.scheduleNext(sp, now);
          }
        }
      }
    }
  }

  private Location randomAroundPlayer(Location base, int minD, int maxD) {
    int a = Math.max(0, minD);
    int b = Math.max(a, maxD);
    double dist = a + (b == a ? 0.0 : this.rnd.nextDouble() * (b - a));
    double ang = this.rnd.nextDouble() * Math.PI * 2.0;
    double dx = Math.cos(ang) * dist;
    double dz = Math.sin(ang) * dist;
    Location loc = base.clone().add(dx, 0.0, dz);
    World w = loc.getWorld();
    if (w != null) {
      int y = w.getHighestBlockYAt(loc) + 1;
      y = Math.max(w.getMinHeight() + 1, Math.min(w.getMaxHeight() - 2, y));
      loc.setY(y);
    }

    return loc;
  }

  private void tickRandomWorld(SpawnPoint sp, long now) {
    World world = Bukkit.getWorld(sp.worldName());
    if (world != null) {
      long due = this.nextDueTick.getOrDefault(sp.spawnId(), -1L);
      if (due == -1L) {
        this.nextDueTick.put(sp.spawnId(), now + Math.max(1, sp.intervalSeconds()) * 20L);
        due = this.nextDueTick.get(sp.spawnId());
      }

      if (now >= due) {
        int aliveNow = this.alive.getOrDefault(sp.spawnId(), Set.of()).size();
        if (aliveNow >= sp.maxSpawns()) {
          this.scheduleNext(sp, now);
        } else {
          int total = this.spawnedTotal.getOrDefault(sp.spawnId(), 0);
          if (sp.mode() == SpawnMode.ONETIME && total >= sp.maxSpawns()) {
            this.scheduleNext(sp, now);
          } else {
            Location spawnLoc;
            if (sp.mode() == SpawnMode.ONETIME) {
              spawnLoc = this.randomWorldAnchor.get(sp.spawnId());
              if (spawnLoc == null) {
                spawnLoc = this.randomAnywhere(world, 6);
                if (spawnLoc == null) {
                  this.scheduleNext(sp, now);
                  return;
                }
              }
            } else {
              spawnLoc = this.randomAnywhere(world, 6);
              if (spawnLoc == null) {
                this.scheduleNext(sp, now);
                return;
              }
            }

            this.randomWorldAnchor.put(sp.spawnId(), spawnLoc);
            this.spawnOne(sp, spawnLoc, now);
            this.ensureMessageTask(sp);
            this.scheduleNext(sp, now);
          }
        }
      }
    }
  }

  private Location randomAnywhere(World world, int tries) {
    Location center = world.getSpawnLocation();
    int attempt = Math.max(1, tries);

    for (int i = 0; i < attempt; i++) {
      int x = center.getBlockX() + this.rnd.nextInt(2000) - 1000;
      int z = center.getBlockZ() + this.rnd.nextInt(2000) - 1000;
      int cx = x >> 4;
      int cz = z >> 4;
      if (!world.getWorldBorder().isInside(new Location(world, x, world.getMinHeight() + 1, z)))
        continue;
      Chunk chunk = world.getChunkAt(cx, cz);
      this.chunkTickets.add(chunk);

      Location var13;
      try {
        int y = world.getHighestBlockYAt(x, z) + 1;
        y = Math.max(world.getMinHeight() + 1, Math.min(world.getMaxHeight() - 2, y));
        Location loc = new Location(world, x + 0.5, y, z + 0.5);
        if (!loc.getBlock().getType().isAir()
            || !loc.clone().subtract(0.0, 1.0, 0.0).getBlock().getType().isSolid()) {
          continue;
        }

        var13 = loc;
      } finally {
        this.chunkTickets.remove(chunk);
      }

      return var13;
    }

    return null;
  }

  private void spawnOne(SpawnPoint sp, Location loc, long now) {
    LivingEntity mob = this.mobs.spawnCustomMob(sp.mobId(), sp.spawnId(), loc);
    if (mob == null) return;
    mob.setPersistent(true);
    mob.setRemoveWhenFarAway(false);
    this.alive
        .computeIfAbsent(sp.spawnId(), k -> ConcurrentHashMap.newKeySet())
        .add(mob.getUniqueId());
    this.spawnedTotal.merge(sp.spawnId(), 1, Integer::sum);
    this.lastSpawnTick.put(sp.spawnId(), now);
    if (sp.type() == SpawnType.RANDOM_WORLD && this.controller != null)
      this.controller.updateRuntimeLocation(
          sp.spawnId(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    if (this.mobs.isBoss(mob) && sp.type() != SpawnType.FOLLOW_PLAYER)
      this.bossLocks.register(sp.spawnId(), mob);
  }

  private void hardKillAll(String spawnId) {
    arenaEpoch.merge(spawnId, 1L, Long::sum);
    for (Entity e : mobs.getAllLivingMobs()) {
      if (e instanceof LivingEntity le && !(le instanceof Player)) {
        PersistentDataContainer pdc = le.getPersistentDataContainer();
        String autoId = (String) pdc.get(this.keys.AUTO_SPAWN_ID, PersistentDataType.STRING);
        if (spawnId.equals(autoId)) {
          mobs.removeWithoutRewards(le);
        }
      }
    }
    Set<UUID> set = this.alive.remove(spawnId);
    if (set != null) {
      set.clear();
    }

    this.spawnedTotal.remove(spawnId);
    this.nextDueTick.remove(spawnId);
    this.lastSpawnTick.remove(spawnId);
    this.coldSince.remove(spawnId);
    this.hot.remove(spawnId);
    this.bossLocks.release(spawnId);
  }

  private void purgeArenaVisuals(SpawnPoint sp) {
    if (sp != null) {
      Location base = sp.baseLocation();
      if (base != null && base.getWorld() != null) {
        double r = Math.max(32.0, sp.arenaRadiusChunks() * 16.0 + 16.0);

        for (Entity e : base.getWorld().getNearbyEntities(base, r, r, r)) {
          if (e instanceof ArmorStand stand
              && stand
                  .getPersistentDataContainer()
                  .has(this.keys.VISUAL_HEAD, PersistentDataType.INTEGER)
              && sp.isInsideArena(stand.getLocation())) {
            stand.remove();
          }
        }
      }
    }
  }

  private void forceLoadArenaChunks(SpawnPoint sp) {
    if (sp.type() == SpawnType.FIXED_POINT || sp.type() == SpawnType.RANDOM_RADIUS) {
      Location c = sp.baseLocation();
      if (c != null && c.getWorld() != null) {
        Set<Chunk> set = new HashSet<>();
        World w = c.getWorld();
        int r =
            Math.min(
                sp.arenaRadiusChunks(),
                org.plugin.theMob.compat.Numbers.clamp(
                    plugin.getConfig().getInt("performance.arena-ticket-radius-chunks", 2), 0, 4));
        int cx = c.getBlockX() >> 4;
        int cz = c.getBlockZ() >> 4;

        for (int x = -r; x <= r; x++) {
          for (int z = -r; z <= r; z++) {
            if (!w.isChunkLoaded(cx + x, cz + z)) continue;
            Chunk chunk = w.getChunkAt(cx + x, cz + z);
            this.chunkTickets.add(chunk);
            set.add(chunk);
          }
        }

        this.forcedChunks.put(sp.spawnId(), set);
      }
    }
  }

  private void releaseArenaChunks(String spawnId) {
    Set<Chunk> set = this.forcedChunks.remove(spawnId);
    if (set != null) {
      for (Chunk c : set) {
        this.chunkTickets.remove(c);
      }
    }
  }

  public void onMobDeath(LivingEntity mob) {
    String spawnId =
        (String)
            mob.getPersistentDataContainer()
                .get(this.keys.AUTO_SPAWN_ID, PersistentDataType.STRING);
    if (spawnId != null) {
      Set<UUID> set = this.alive.get(spawnId);
      if (set != null) {
        set.remove(mob.getUniqueId());
      }

      SpawnPoint sp = this.points.get(spawnId);
      if (sp != null
          && sp.type() == SpawnType.RANDOM_WORLD
          && sp.mode() == SpawnMode.ENDLESS
          && this.alive.getOrDefault(spawnId, Set.of()).isEmpty()) {
        this.stopMessageTask(spawnId);
      }

      if (sp != null
          && sp.type() == SpawnType.RANDOM_WORLD
          && sp.mode() == SpawnMode.ONETIME
          && this.alive.getOrDefault(spawnId, Set.of()).isEmpty()) {
        this.unregister(spawnId);
        if (this.controller != null) {
          this.controller.onRuntimeSpawnRemoved(spawnId);
        }
      }
    }
  }

  public void releaseBossLock(LivingEntity mob) {
    String spawnId =
        (String)
            mob.getPersistentDataContainer()
                .get(this.keys.AUTO_SPAWN_ID, PersistentDataType.STRING);
    if (spawnId != null) {
      this.bossLocks.release(spawnId);
    }
  }

  /** Administrative removal frees capacity without completing ONETIME spawn definitions. */
  public void onMobRemoved(LivingEntity mob) {
    String source =
        mob.getPersistentDataContainer().get(keys.AUTO_SPAWN_ID, PersistentDataType.STRING);
    if (source != null) {
      Set<UUID> entries = alive.get(source);
      if (entries != null) entries.remove(mob.getUniqueId());
    }
    releaseBossLock(mob);
  }

  public boolean isBossInHotArena(LivingEntity boss) {
    String id =
        (String)
            boss.getPersistentDataContainer()
                .get(this.keys.AUTO_SPAWN_ID, PersistentDataType.STRING);
    return id != null && this.hot.contains(id);
  }

  private void ensureMessageTask(SpawnPoint sp) {
    if (sp.type() == SpawnType.RANDOM_WORLD) {
      if (sp.message() != null && !sp.message().isBlank()) {
        if (!this.messageTasks.containsKey(sp.spawnId())) {
          int interval = Math.max(1, sp.messageTimerSeconds());
          int taskId =
              Bukkit.getScheduler()
                  .scheduleSyncRepeatingTask(
                      this.plugin, () -> this.broadcastRandomWorldMessage(sp), 20L, interval * 20L);
          this.messageTasks.put(sp.spawnId(), taskId);
        }
      }
    }
  }

  private void stopMessageTask(String spawnId) {
    Integer tid = this.messageTasks.remove(spawnId);
    if (tid != null) {
      Bukkit.getScheduler().cancelTask(tid);
    }
  }

  private void broadcastRandomWorldMessage(SpawnPoint sp) {
    if (sp.message() != null && !sp.message().isBlank()) {
      Location loc = null;
      Set<UUID> set = this.alive.get(sp.spawnId());
      if (set != null && !set.isEmpty()) {
        UUID id = set.iterator().next();
        if (Bukkit.getEntity(id) instanceof LivingEntity le && le.isValid()) {
          loc = le.getLocation();
        }
      }

      if (loc == null) {
        loc = this.randomWorldAnchor.get(sp.spawnId());
      }

      if (loc != null) {
        String msg =
            sp.message()
                .replace("{world}", loc.getWorld().getName())
                .replace("{x}", String.valueOf(loc.getBlockX()))
                .replace("{y}", String.valueOf(loc.getBlockY()))
                .replace("{z}", String.valueOf(loc.getBlockZ()));
        String colored = ChatColor.translateAlternateColorCodes('&', msg);

        for (Player p : Bukkit.getOnlinePlayers()) {
          p.sendMessage(colored);
        }
      }
    }
  }

  public void onKillAll() {
    for (Integer taskId : this.messageTasks.values()) {
      Bukkit.getScheduler().cancelTask(taskId);
    }

    this.messageTasks.clear();
    this.randomWorldAnchor.clear();

    for (Set<UUID> set : this.alive.values()) {
      set.clear();
    }

    this.bossLocks.clearAll();
    this.purgeAllStaleEntities();
    this.chunkTickets.releaseAll();
  }

  private void purgeAllStaleEntities() {
    int removed = 0;

    for (World w : Bukkit.getWorlds()) {
      for (Entity e : new ArrayList<>(w.getEntities())) {
        if (!(e instanceof Player)) {
          PersistentDataContainer pdc = e.getPersistentDataContainer();
          boolean owned =
              pdc.has(this.keys.MOB_ID, PersistentDataType.STRING)
                  || pdc.has(this.keys.AUTO_SPAWN_ID, PersistentDataType.STRING)
                  || pdc.has(this.keys.VISUAL_HEAD, PersistentDataType.INTEGER);
          if (owned) {
            e.remove();
            removed++;
          }
        }
      }
    }

    if (removed > 0) {
      this.plugin
          .getLogger()
          .warning("[TheMob] HARD PURGE removed " + removed + " orphan entities");
    }
  }

  private String color(String s) {
    return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
  }
}
