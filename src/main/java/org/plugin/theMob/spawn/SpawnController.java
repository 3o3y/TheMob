package org.plugin.theMob.spawn;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.core.ConfigService;
import org.plugin.theMob.core.KeyRegistry;
import org.plugin.theMob.mob.MobManager;
import org.plugin.theMob.mob.spawn.AutoSpawnManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class SpawnController implements Listener {

    private static final int DEFAULT_ARENA_RADIUS_CHUNKS = 2; // 5x5

    private final TheMob plugin;
    private final MobManager mobs;
    private final AutoSpawnManager auto;
    private final ConfigService configs;
    private final Map<String, SpawnPoint> registry = new ConcurrentHashMap<>();
    private final Map<UUID, Location> lastLocation = new ConcurrentHashMap<>();


    public SpawnController(
            TheMob plugin,
            MobManager mobs,
            AutoSpawnManager auto,
            KeyRegistry keys
    ) {
        this.plugin = plugin;
        this.mobs = mobs;
        this.auto = auto;
        this.configs = plugin.configs();
    }
    // =====================================================
    // LIFECYCLE
    // =====================================================

    public void start() {
        loadFromConfig();
        auto.start();
    }

    public void stop() {
        for (SpawnPoint sp : registry.values()) {
            auto.unregister(sp.spawnId());
        }
        registry.clear();
        auto.stop();
    }
    // =====================================================
    // CREATE
    // =====================================================

    public boolean startAutoSpawn(String mobId, Location loc, int intervalSeconds, int maxSpawns) {
        if (mobId == null || mobId.isBlank()) return false;
        if (loc == null || loc.getWorld() == null) return false;
        if (!mobs.mobExists(mobId)) return false;

        SpawnPoint sp = new SpawnPoint(
                mobId,
                loc.getWorld().getName(),
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ(),
                intervalSeconds,
                maxSpawns,
                DEFAULT_ARENA_RADIUS_CHUNKS,
                true
        );

        registry.put(sp.spawnId(), sp);
        auto.register(sp);
        saveToConfig();

        return true;
    }
    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent e) {
        lastLocation.put(e.getPlayer().getUniqueId(), e.getFrom());

        if (e.getFrom().distanceSquared(e.getTo()) < 0.01) return;
        handleArenaTransition(e.getFrom(), e.getTo());
    }
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Location last = lastLocation.remove(e.getPlayer().getUniqueId());
        if (last != null) {
            handleArenaExit(last);
        }
    }
    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        lastLocation.put(e.getPlayer().getUniqueId(), e.getFrom());
        handleArenaTransition(e.getFrom(), e.getTo());
    }
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Location last = lastLocation.remove(e.getPlayer().getUniqueId());
        if (last != null) {
            handleArenaExit(last);
        }
    }
    private void handleArenaTransition(Location from, Location to) {
        for (SpawnPoint sp : registry.values()) {
            boolean wasIn = isInside(sp, from);
            boolean isIn  = isInside(sp, to);

            if (wasIn && !isIn) {
                auto.forceCleanupIfEmpty(sp);
            }
        }
    }
    private void handleArenaExit(Location loc) {
        for (SpawnPoint sp : registry.values()) {
            if (isInside(sp, loc)) {
                auto.forceCleanupIfEmpty(sp);
            }
        }
    }
    private boolean isInside(SpawnPoint sp, Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(sp.worldName())) return false;

        int cx = loc.getBlockX() >> 4;
        int cz = loc.getBlockZ() >> 4;

        return Math.abs(cx - sp.baseChunkX()) <= sp.arenaRadiusChunks()
                && Math.abs(cz - sp.baseChunkZ()) <= sp.arenaRadiusChunks();
    }
    // =====================================================
    // DELETE
    // =====================================================

    public boolean deleteAutoSpawnByMobId(String mobId) {
        boolean removed = false;

        for (Iterator<Map.Entry<String, SpawnPoint>> it = registry.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, SpawnPoint> e = it.next();
            if (!e.getValue().mobId().equalsIgnoreCase(mobId)) continue;

            auto.unregister(e.getKey());
            it.remove();
            removed = true;
        }

        if (removed) {
            saveToConfig();
        }
        return removed;
    }
    // =====================================================
    // CONFIG LOAD / SAVE
    // =====================================================

    @SuppressWarnings("unchecked")
    private void loadFromConfig() {
        registry.clear(); // 👈 safety
        FileConfiguration cfg = configs.autoSpawn();
        List<Map<String, Object>> list = (List<Map<String, Object>>) cfg.getList("spawns");

        if (list == null || list.isEmpty()) return;

        for (Map<String, Object> raw : list) {
            try {
                String mobId = (String) raw.get("mobId");
                String world = (String) raw.get("world");

                int x = (int) raw.get("x");
                int y = (int) raw.get("y");
                int z = (int) raw.get("z");

                int interval = (int) raw.get("intervalSeconds");
                int maxSpawns = (int) raw.get("maxSpawns");
                int radius = (int) raw.getOrDefault("arenaRadiusChunks", DEFAULT_ARENA_RADIUS_CHUNKS);
                boolean enabled = (boolean) raw.getOrDefault("enabled", true);
                if (!enabled) continue;

                if (!mobs.mobExists(mobId)) continue;

                SpawnPoint sp = new SpawnPoint(
                        mobId,
                        world,
                        x, y, z,
                        interval,
                        maxSpawns,
                        radius,
                        enabled
                );

                registry.put(sp.spawnId(), sp);
                auto.register(sp);

            } catch (Exception ex) {
                plugin.getLogger().warning("[AutoSpawn] Invalid entry skipped");
            }
        }
    }

    private void saveToConfig() {
        FileConfiguration cfg = configs.autoSpawn();
        List<Map<String, Object>> out = new ArrayList<>();

        for (SpawnPoint sp : registry.values()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("mobId", sp.mobId());
            map.put("world", sp.worldName());
            map.put("x", sp.baseLocation().getBlockX());
            map.put("y", sp.baseLocation().getBlockY());
            map.put("z", sp.baseLocation().getBlockZ());
            map.put("intervalSeconds", sp.intervalSeconds());
            map.put("maxSpawns", sp.maxSpawns());
            map.put("arenaRadiusChunks", sp.arenaRadiusChunks());
            map.put("enabled", true);
            out.add(map);
        }

        cfg.set("spawns", out);
        configs.saveAutoSpawn();
    }
    // =====================================================
    // DEATH EVENTS
    // =====================================================

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof LivingEntity le)) return;
        mobs.onMobDeath(le, e);
        auto.onEntityDeath(le);
    }
}
