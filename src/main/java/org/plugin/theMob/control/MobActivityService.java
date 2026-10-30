package org.plugin.theMob.control;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;
import org.plugin.theMob.TheMob;

/** Main-thread spatial player index and bounded round-robin updates; never loads chunks. */
public final class MobActivityService implements Listener, AutoCloseable {
  private record Cell(int x, int z) {}

  private record Viewer(Player player, Location location) {}

  private record Work(UUID id, long generation) {}

  private final TheMob plugin;
  private final Map<UUID, Map<Cell, List<Viewer>>> viewers = new HashMap<>();
  private final ArrayDeque<Work> queue = new ArrayDeque<>();
  private final Set<UUID> queued = new HashSet<>();
  private final Map<UUID, Long> generations = new HashMap<>();
  private long nextGeneration;
  private final Map<UUID, Long> combatUntil = new HashMap<>(), coldSince = new HashMap<>();
  private final Set<UUID> cold = new HashSet<>();
  private final NamespacedKey originalAi, originalAware;
  private final int budget;
  private final double hotRadius, coldRadius;
  private final long graceTicks, combatTicks;
  private final boolean enabled;
  private final org.bukkit.scheduler.BukkitTask playerTask, activityTask;

  public MobActivityService(TheMob plugin) {
    this.plugin = plugin;
    var config = plugin.getConfig();
    enabled = config.getBoolean("performance.activity.enabled", true);
    hotRadius =
        org.plugin.theMob.content.MobCatalog.number(
            config, "performance.activity.hot-radius", 64, 16, 192);
    coldRadius =
        org.plugin.theMob.content.MobCatalog.number(
            config, "performance.activity.cold-radius", Math.max(hotRadius, 96), hotRadius, 256);
    budget =
        org.plugin.theMob.compat.Numbers.clamp(
            config.getInt("performance.activity.entities-per-cycle", 64), 8, 512);
    graceTicks =
        org.plugin.theMob.compat.Numbers.clamp(
                config.getLong("performance.activity.cold-grace-seconds", 10), 0, 300)
            * 20;
    combatTicks =
        org.plugin.theMob.compat.Numbers.clamp(
                config.getLong("performance.activity.combat-keepalive-seconds", 20), 1, 300)
            * 20;
    originalAi = new NamespacedKey(plugin, "activity_original_ai");
    originalAware = new NamespacedKey(plugin, "activity_original_aware");
    refreshPlayers();
    Bukkit.getPluginManager().registerEvents(this, plugin);
    playerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshPlayers, 20, 20);
    activityTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 2, 2);
  }

  private static Cell cell(Location l) {
    return new Cell(Math.floorDiv(l.getBlockX(), 64), Math.floorDiv(l.getBlockZ(), 64));
  }

  public void refreshPlayers() {
    viewers.clear();
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (!player.isValid()
          || player.isDead()
          || player.getGameMode() == GameMode.SPECTATOR
          || player.hasMetadata("NPC")) continue;
      Location location = player.getLocation();
      viewers
          .computeIfAbsent(player.getWorld().getUID(), ignored -> new HashMap<>())
          .computeIfAbsent(cell(location), ignored -> new ArrayList<>())
          .add(new Viewer(player, location));
    }
  }

  public List<Player> nearby(Location at, double radius, boolean combatTargets) {
    if (at.getWorld() == null) return List.of();
    var world = viewers.get(at.getWorld().getUID());
    if (world == null) return List.of();
    Cell center = cell(at);
    int cells = (int) Math.ceil(Math.min(radius, 512) / 64) + 1;
    double range = radius * radius;
    var result = new ArrayList<Player>();
    for (int x = center.x - cells; x <= center.x + cells; x++)
      for (int z = center.z - cells; z <= center.z + cells; z++)
        for (Viewer viewer : world.getOrDefault(new Cell(x, z), List.of())) {
          Player p = viewer.player;
          if (!p.isOnline()
              || p.isDead()
              || !p.getWorld().equals(at.getWorld())
              || p.hasMetadata("NPC")
              || p.getGameMode() == GameMode.SPECTATOR
              || combatTargets && !org.plugin.theMob.boss.behavior.BossTargets.valid(p)) continue;
          if (viewer.location.distanceSquared(at) <= range) result.add(p);
        }
    return result;
  }

  public Player nearest(LivingEntity entity, double radius) {
    Location at = entity.getLocation();
    Player best = null;
    double distance = radius * radius;
    for (Player player : nearby(at, radius, true)) {
      double candidate = player.getLocation().distanceSquared(at);
      if (candidate < distance) {
        best = player;
        distance = candidate;
      }
    }
    return best;
  }

  public void track(LivingEntity entity) {
    UUID id = entity.getUniqueId();
    if (queued.add(id)) {
      long generation = ++nextGeneration;
      generations.put(id, generation);
      queue.addLast(new Work(id, generation));
      if (entity.getPersistentDataContainer().has(originalAi, PersistentDataType.BYTE))
        cold.add(id);
    }
  }

  public void forget(UUID id) {
    // Queue entries are discarded in the next bounded pass, avoiding linear queue removal.
    queued.remove(id);
    generations.remove(id);
    combatUntil.remove(id);
    coldSince.remove(id);
    cold.remove(id);
  }

  public boolean active(LivingEntity entity) {
    if (!enabled) return true;
    if (combatUntil.getOrDefault(entity.getUniqueId(), 0L)
        > plugin.automation().tps().currentTick()) return true;
    return !cold.contains(entity.getUniqueId());
  }

  public int coldCount() {
    return cold.size();
  }

  public int trackedCount() {
    return queued.size();
  }

  public void tick() {
    int work = Math.min(budget, queue.size());
    long now = plugin.automation().tps().currentTick();
    for (int i = 0; i < work; i++) {
      Work entry = queue.removeFirst();
      UUID id = entry.id;
      if (!queued.contains(id) || !Objects.equals(generations.get(id), entry.generation)) continue;
      var entity = plugin.mobs().tracked(id);
      if (entity == null || !entity.isValid() || entity.isDead()) {
        forget(id);
        continue;
      }
      queue.addLast(entry);
      if (!enabled) {
        wake(entity);
        continue;
      }
      boolean near =
          !nearby(entity.getLocation(), cold.contains(id) ? hotRadius : coldRadius, false)
              .isEmpty();
      if (!near)
        near =
            !org.plugin
                .theMob
                .boss
                .behavior
                .BossTargets
                .petsNear(entity.getLocation(), cold.contains(id) ? hotRadius : coldRadius)
                .isEmpty();
      if (near || combatUntil.getOrDefault(id, 0L) > now) {
        coldSince.remove(id);
        wake(entity);
      } else {
        long since = coldSince.computeIfAbsent(id, ignored -> now);
        if (now - since >= graceTicks) sleep(entity);
      }
    }
  }

  private void sleep(LivingEntity entity) {
    var data = entity.getPersistentDataContainer();
    if (cold.contains(entity.getUniqueId()) && data.has(originalAi, PersistentDataType.BYTE))
      return;
    if (!data.has(originalAi, PersistentDataType.BYTE)) {
      data.set(originalAi, PersistentDataType.BYTE, (byte) (entity.hasAI() ? 1 : 0));
      if (entity instanceof Mob mob)
        data.set(originalAware, PersistentDataType.BYTE, (byte) (mob.isAware() ? 1 : 0));
    }
    if (entity instanceof Mob mob) {
      mob.setTarget(null);
      mob.setAware(false);
    }
    entity.setAI(false);
    cold.add(entity.getUniqueId());
  }

  public void wake(LivingEntity entity) {
    var data = entity.getPersistentDataContainer();
    Byte ai = data.get(originalAi, PersistentDataType.BYTE);
    if (ai != null) {
      entity.setAI(ai == 1);
      Byte aware = data.get(originalAware, PersistentDataType.BYTE);
      if (entity instanceof Mob mob && aware != null) mob.setAware(aware == 1);
      data.remove(originalAi);
      data.remove(originalAware);
    }
    cold.remove(entity.getUniqueId());
  }

  public void engage(LivingEntity entity) {
    if (plugin.api().mob(entity).isEmpty()) return;
    combatUntil.put(entity.getUniqueId(), plugin.automation().tps().currentTick() + combatTicks);
    coldSince.remove(entity.getUniqueId());
    wake(entity);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void damage(EntityDamageEvent event) {
    if (event.getFinalDamage() <= 0) return;
    if (event.getEntity() instanceof LivingEntity entity) engage(entity);
    if (event instanceof EntityDamageByEntityEvent hit) {
      var source = hit.getDamager();
      if (source instanceof Projectile projectile
          && projectile.getShooter() instanceof LivingEntity living) engage(living);
      else if (source instanceof LivingEntity living) engage(living);
    }
  }

  public void close() {
    playerTask.cancel();
    activityTask.cancel();
    for (LivingEntity entity : plugin.mobs().getAllLivingMobs()) wake(entity);
    HandlerList.unregisterAll(this);
    queue.clear();
    queued.clear();
    generations.clear();
    viewers.clear();
    combatUntil.clear();
    cold.clear();
    coldSince.clear();
  }
}
