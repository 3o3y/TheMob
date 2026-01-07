package org.plugin.theMob.control;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.NamespacedKey;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks alive TheMob entities by role/world using PDC tags.
 */
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
    }

    public void reload(FileConfiguration cfg) {
        config.reload(cfg);
    }

    public BudgetConfig config() {
        return config;
    }

    public void tagSpawnedEntity(LivingEntity entity, SpawnRole role) {
        if (entity == null) return;

        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(keyMobTag, PersistentDataType.BYTE, (byte) 1);
        pdc.set(keyRole, PersistentDataType.STRING, role.name());

        addAlive(entity.getUniqueId(), entity.getWorld(), role);
    }

    public boolean isTheMobEntity(Entity e) {
        if (e == null) return false;
        Byte tag = e.getPersistentDataContainer().get(keyMobTag, PersistentDataType.BYTE);
        return tag != null && tag == (byte) 1;
    }

    public SpawnRole roleOf(Entity e) {
        if (e == null) return null;
        String r = e.getPersistentDataContainer().get(keyRole, PersistentDataType.STRING);
        if (r == null) return null;
        try {
            return SpawnRole.valueOf(r);
        } catch (Exception ignored) {
            return null;
        }
    }

    public int aliveTotal() {
        return aliveAll.size();
    }

    public int aliveBosses() {
        return aliveBosses.size();
    }

    public int aliveMinions() {
        return aliveMinions.size();
    }

    public int aliveInWorld(String worldName) {
        Set<UUID> set = aliveByWorld.get(worldName);
        return set == null ? 0 : set.size();
    }

    public Map<String, Integer> aliveWorldSnapshot() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (World w : Bukkit.getWorlds()) {
            out.put(w.getName(), aliveInWorld(w.getName()));
        }
        return out;
    }

    private void addAlive(UUID id, World world, SpawnRole role) {
        if (id == null || world == null) return;

        aliveAll.add(id);
        aliveByWorld.computeIfAbsent(world.getName(), k -> ConcurrentHashMap.newKeySet()).add(id);

        if (role == SpawnRole.BOSS) aliveBosses.add(id);
        if (role == SpawnRole.MINION) aliveMinions.add(id);
    }

    private void removeAlive(UUID id, World world, SpawnRole role) {
        if (id == null) return;

        aliveAll.remove(id);

        if (world != null) {
            Set<UUID> set = aliveByWorld.get(world.getName());
            if (set != null) set.remove(id);
        }

        if (role == SpawnRole.BOSS) aliveBosses.remove(id);
        if (role == SpawnRole.MINION) aliveMinions.remove(id);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        LivingEntity ent = e.getEntity();
        if (!isTheMobEntity(ent)) return;

        SpawnRole role = roleOf(ent);
        removeAlive(ent.getUniqueId(), ent.getWorld(), role);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRemove(EntityRemoveEvent e) {
        Entity ent = e.getEntity();
        if (!(ent instanceof LivingEntity le)) return;
        if (!isTheMobEntity(le)) return;

        SpawnRole role = roleOf(le);
        removeAlive(le.getUniqueId(), le.getWorld(), role);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent e) {
        String name = e.getWorld().getName();
        Set<UUID> set = aliveByWorld.remove(name);
        if (set == null) return;

        for (UUID id : set) {
            aliveAll.remove(id);
            aliveBosses.remove(id);
            aliveMinions.remove(id);
        }
    }
    // =========================
// READ-ONLY ACCESSORS
// =========================
    public Set<String> aliveBossIds() {
        Set<String> out = new HashSet<>();
        NamespacedKey mobIdKey = new NamespacedKey(plugin, "mob_id");

        for (UUID id : aliveBosses) {
            Entity e = Bukkit.getEntity(id);
            if (!(e instanceof LivingEntity le)) continue;

            String mobId = le.getPersistentDataContainer()
                    .get(mobIdKey, PersistentDataType.STRING);

            if (mobId != null && !mobId.isBlank()) {
                out.add(mobId);
            }
        }
        return out;
    }

    public Set<UUID> aliveBossUuids() {
        return Set.copyOf(aliveBosses);
    }



}
