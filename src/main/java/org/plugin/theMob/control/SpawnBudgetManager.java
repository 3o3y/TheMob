package org.plugin.theMob.control;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class SpawnBudgetManager implements Listener {
  private final Plugin plugin;
  private final NamespacedKey keyMobTag;
  private final NamespacedKey keyRole;
  private final BudgetConfig config = new BudgetConfig();
  private final Set<UUID> aliveAll = ConcurrentHashMap.newKeySet();
  private final Set<UUID> aliveBosses = ConcurrentHashMap.newKeySet();
  private final Set<UUID> aliveMinions = ConcurrentHashMap.newKeySet();
  private final Map<String, Set<UUID>> aliveByWorld = new ConcurrentHashMap<>();

  public SpawnBudgetManager(Plugin plugin) {
    this.plugin = plugin;
    this.keyMobTag = new NamespacedKey(plugin, "themob");
    this.keyRole = new NamespacedKey(plugin, "themob_role");
    boolean hasRemoval =
        org.plugin.theMob.compat.OptionalEvents.bind(
            plugin,
            this,
            "org.bukkit.event.entity.EntityRemoveEvent",
            EventPriority.MONITOR,
            true,
            event -> onRemove((org.bukkit.event.entity.EntityEvent) event));
    if (!hasRemoval && Bukkit.getServer() != null && plugin.isEnabled())
      Bukkit.getScheduler().runTaskTimer(plugin, this::pruneLegacy, 20, 20);
  }

  public void reload(FileConfiguration cfg) {
    this.config.reload(cfg);
  }

  public BudgetConfig config() {
    return this.config;
  }

  public void tagSpawnedEntity(LivingEntity entity, SpawnRole role) {
    if (entity != null) {
      PersistentDataContainer pdc = entity.getPersistentDataContainer();
      pdc.set(this.keyMobTag, PersistentDataType.BYTE, (byte) 1);
      pdc.set(this.keyRole, PersistentDataType.STRING, role.name());
      this.addAlive(entity.getUniqueId(), entity.getWorld(), role);
    }
  }

  public void rebuild() {
    this.aliveAll.clear();
    this.aliveBosses.clear();
    this.aliveMinions.clear();
    this.aliveByWorld.clear();
    for (World world : Bukkit.getWorlds())
      for (LivingEntity entity : world.getLivingEntities()) restore(entity);
  }

  private void restore(LivingEntity entity) {
    var pdc = entity.getPersistentDataContainer();
    if (!entity.isValid() || entity.isDead()) return;
    if (isTheMobEntity(entity)
        || pdc.has(new NamespacedKey(this.plugin, "mob_id"), PersistentDataType.STRING)
        || pdc.has(new NamespacedKey(this.plugin, "minion"), PersistentDataType.BYTE)) {
      SpawnRole role = roleOf(entity);
      if (role == null)
        role =
            pdc.getOrDefault(
                        new NamespacedKey(this.plugin, "is_boss"), PersistentDataType.INTEGER, 0)
                    == 1
                ? SpawnRole.BOSS
                : SpawnRole.MOB;
      tagSpawnedEntity(entity, role);
    }
  }

  @EventHandler
  public void onLoad(org.bukkit.event.world.EntitiesLoadEvent event) {
    for (Entity entity : event.getEntities())
      if (entity instanceof LivingEntity living) restore(living);
  }

  public boolean isTheMobEntity(Entity e) {
    if (e == null) {
      return false;
    } else {
      Byte tag = (Byte) e.getPersistentDataContainer().get(this.keyMobTag, PersistentDataType.BYTE);
      return tag != null && tag == 1;
    }
  }

  public SpawnRole roleOf(Entity e) {
    if (e == null) {
      return null;
    } else {
      String r =
          (String) e.getPersistentDataContainer().get(this.keyRole, PersistentDataType.STRING);
      if (r == null) {
        return null;
      } else {
        try {
          return SpawnRole.valueOf(r);
        } catch (Exception var4) {
          return null;
        }
      }
    }
  }

  public int aliveTotal() {
    return this.aliveAll.size();
  }

  public int aliveBosses() {
    return this.aliveBosses.size();
  }

  public int aliveMinions() {
    return this.aliveMinions.size();
  }

  public int aliveInWorld(String worldName) {
    Set<UUID> set = this.aliveByWorld.get(worldName);
    return set == null ? 0 : set.size();
  }

  public Map<String, Integer> aliveWorldSnapshot() {
    Map<String, Integer> out = new LinkedHashMap<>();

    for (World w : Bukkit.getWorlds()) {
      out.put(w.getName(), this.aliveInWorld(w.getName()));
    }

    return out;
  }

  private void addAlive(UUID id, World world, SpawnRole role) {
    if (id != null && world != null) {
      this.aliveAll.add(id);
      this.aliveByWorld
          .computeIfAbsent(world.getName(), k -> ConcurrentHashMap.newKeySet())
          .add(id);
      if (role == SpawnRole.BOSS) {
        this.aliveBosses.add(id);
      }

      if (role == SpawnRole.MINION) {
        this.aliveMinions.add(id);
      }
    }
  }

  private void removeAlive(UUID id, World world, SpawnRole role) {
    if (id != null) {
      this.aliveAll.remove(id);
      if (world != null) {
        Set<UUID> set = this.aliveByWorld.get(world.getName());
        if (set != null) {
          set.remove(id);
        }
      }

      if (role == SpawnRole.BOSS) {
        this.aliveBosses.remove(id);
      }

      if (role == SpawnRole.MINION) {
        this.aliveMinions.remove(id);
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onDeath(EntityDeathEvent e) {
    LivingEntity ent = e.getEntity();
    if (this.isTheMobEntity(ent)) {
      SpawnRole role = this.roleOf(ent);
      this.removeAlive(ent.getUniqueId(), ent.getWorld(), role);
    }
  }

  public void onRemove(org.bukkit.event.entity.EntityEvent e) {
    if (e.getEntity() instanceof LivingEntity le) {
      if (this.isTheMobEntity(le)) {
        SpawnRole role = this.roleOf(le);
        this.removeAlive(le.getUniqueId(), le.getWorld(), role);
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void unloaded(org.bukkit.event.world.EntitiesUnloadEvent event) {
    for (Entity entity : event.getEntities())
      if (isTheMobEntity(entity))
        removeAlive(entity.getUniqueId(), event.getWorld(), roleOf(entity));
  }

  private int legacyCursor;

  private void pruneLegacy() {
    var ids = new java.util.ArrayList<>(aliveAll);
    if (ids.isEmpty()) {
      legacyCursor = 0;
      return;
    }
    int count = Math.min(100, ids.size());
    for (int n = 0; n < count; n++) {
      UUID id = ids.get((legacyCursor + n) % ids.size());
      Entity entity = Bukkit.getEntity(id);
      if (entity == null || !entity.isValid() || entity.isDead()) {
        aliveAll.remove(id);
        aliveBosses.remove(id);
        aliveMinions.remove(id);
        aliveByWorld.values().forEach(set -> set.remove(id));
      }
    }
    legacyCursor = (legacyCursor + count) % ids.size();
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onWorldUnload(WorldUnloadEvent e) {
    String name = e.getWorld().getName();
    Set<UUID> set = this.aliveByWorld.remove(name);
    if (set != null) {
      for (UUID id : set) {
        this.aliveAll.remove(id);
        this.aliveBosses.remove(id);
        this.aliveMinions.remove(id);
      }
    }
  }

  public Set<String> aliveBossIds() {
    Set<String> out = new HashSet<>();
    NamespacedKey mobIdKey = new NamespacedKey(this.plugin, "mob_id");

    for (UUID id : this.aliveBosses) {
      if (Bukkit.getEntity(id) instanceof LivingEntity le) {
        String mobId =
            (String) le.getPersistentDataContainer().get(mobIdKey, PersistentDataType.STRING);
        if (mobId != null && !mobId.isBlank()) {
          out.add(mobId);
        }
      }
    }

    return out;
  }

  public Set<UUID> aliveBossUuids() {
    return Set.copyOf(this.aliveBosses);
  }
}
