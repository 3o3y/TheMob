// src/main/java/org/plugin/theMob/spawn/SpawnController.java
package org.plugin.theMob.spawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.core.KeyRegistry;
import org.plugin.theMob.mob.MobManager;
import org.plugin.theMob.mob.spawn.AutoSpawnManager;

import java.util.*;

public final class SpawnController implements Listener {

    private final TheMob plugin;
    private final MobManager mobs;
    private final AutoSpawnManager autoSpawnManager;
    private final KeyRegistry keys;
    private final Map<String, SpawnPoint> registry = new HashMap<>();
    private final Map<String, BukkitRunnable> tasks = new HashMap<>();
    public SpawnController(
            TheMob plugin,
            MobManager mobs,
            AutoSpawnManager autoSpawnManager,
            KeyRegistry keys
    ) {
        this.plugin = plugin;
        this.mobs = mobs;
        this.autoSpawnManager = autoSpawnManager;
        this.keys = keys;
    }
// LIFECYCLE
    public void start() {
        reload();
        plugin.getLogger().info("[AutoSpawn] Started (" + registry.size() + ")");
    }
    public void shutdown() {
        tasks.values().forEach(BukkitRunnable::cancel);
        tasks.clear();
        registry.clear();
    }
    public void reload() {
        shutdown();
        loadFromConfig();
        for (SpawnPoint sp : registry.values()) {
            schedule(sp);
        }
    }
// COMMAND API
    public boolean startAutoSpawnAt(
            String mobId,
            Location loc,
            int intervalSeconds,
            int maxAlive
    ) {
        SpawnPoint sp = SpawnPoint.fromPlayerBlock(
                mobId,
                loc,
                intervalSeconds,
                maxAlive
        );
        registry.put(sp.spawnId(), sp);
        saveToConfig();
        schedule(sp);
        return true;
    }
    public boolean deleteAutoSpawn(String mobId) {
        boolean removed = false;
        Iterator<Map.Entry<String, SpawnPoint>> it = registry.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, SpawnPoint> e = it.next();
            if (!e.getValue().mobId().equalsIgnoreCase(mobId)) continue;
            BukkitRunnable r = tasks.remove(e.getKey());
            if (r != null) r.cancel();
            it.remove();
            removed = true;
        }
        if (removed) saveToConfig();
        return removed;
    }
// INTERNAL
    private void schedule(SpawnPoint sp) {
        World world = Bukkit.getWorld(sp.world());
        if (world == null) return;
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!sp.enabled()) return;
                if (!world.isChunkLoaded(sp.chunkX(), sp.chunkZ())) return;
                autoSpawnManager.validateAliveCounts(mobs, keys);
                if (autoSpawnManager.alive(sp.mobId()) >= sp.maxAlive()) return;
                boolean success = mobs.spawnCustomMob(
                        sp.mobId(),
                        sp.location(world)
                );
                if (!success) return;
                autoSpawnManager.incrementAlive(sp.mobId());
                autoSpawnManager.updateLastSpawn(sp.mobId());
            }
        };
        task.runTaskTimer(plugin, 0L, sp.intervalSeconds() * 20L);
        tasks.put(sp.spawnId(), task);
    }
// CLEANUP
    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof LivingEntity le)) return;
        String id = mobs.mobIdOf(le);
        if (id == null) return;
        autoSpawnManager.decrementAlive(id);
    }
// CONFIG
    private void loadFromConfig() {
        FileConfiguration cfg = plugin.configs().autoSpawn();
        for (Map<?, ?> raw : cfg.getMapList("spawns")) {
            SpawnPoint sp = SpawnPoint.fromMap(raw);
            if (sp != null) registry.put(sp.spawnId(), sp);
        }
    }
    private void saveToConfig() {
        FileConfiguration cfg = plugin.configs().autoSpawn();
        List<Map<String, Object>> out = new ArrayList<>();
        for (SpawnPoint sp : registry.values()) {
            out.add(sp.toMap());
        }
        cfg.set("spawns", out);
        plugin.configs().saveAutoSpawn();
    }
}
