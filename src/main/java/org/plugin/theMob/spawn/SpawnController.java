package org.plugin.theMob.spawn;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.core.ConfigService;
import org.plugin.theMob.mob.MobManager;
import org.plugin.theMob.mob.spawn.AutoSpawnManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class SpawnController implements Listener {

    private static final int DEFAULT_ARENA_RADIUS_CHUNKS = 2;

    private final TheMob plugin;
    private final MobManager mobs;
    private final AutoSpawnManager auto;
    private final ConfigService configs;

    private final Map<String, SpawnPoint> registry = new ConcurrentHashMap<>();
    private final Map<UUID, Location> lastLocation = new ConcurrentHashMap<>();

    public SpawnController(
            TheMob plugin,
            MobManager mobs,
            AutoSpawnManager auto
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
        registry.values().forEach(sp -> auto.unregister(sp.spawnId()));
        registry.clear();
        auto.stop();
    }

    // =====================================================
    // COMMAND API
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

    public boolean deleteAutoSpawnByMobId(String mobId) {
        boolean removed = false;

        for (Iterator<Map.Entry<String, SpawnPoint>> it = registry.entrySet().iterator(); it.hasNext();) {
            SpawnPoint sp = it.next().getValue();
            if (!sp.mobId().equalsIgnoreCase(mobId)) continue;

            auto.unregister(sp.spawnId());
            it.remove();
            removed = true;
        }

        if (removed) saveToConfig();
        return removed;
    }

    public List<AutoSpawnInfo> listAutoSpawns() {
        List<AutoSpawnInfo> list = new ArrayList<>();

        for (SpawnPoint sp : registry.values()) {
            Location l = sp.baseLocation();
            list.add(new AutoSpawnInfo(
                    sp.mobId(),
                    sp.worldName(),
                    l.getBlockX(),
                    l.getBlockY(),
                    l.getBlockZ(),
                    sp.intervalSeconds(),
                    sp.maxSpawns()
            ));
        }
        return list;
    }

    // =====================================================
    // PLAYER TRACKING
    // =====================================================

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        lastLocation.put(e.getPlayer().getUniqueId(), e.getFrom());
        if (e.getFrom().distanceSquared(e.getTo()) < 0.01) return;
        handleTransition(e.getFrom(), e.getTo());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Location last = lastLocation.remove(e.getPlayer().getUniqueId());
        if (last != null) notifyExit(last);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        lastLocation.put(e.getPlayer().getUniqueId(), e.getFrom());
        handleTransition(e.getFrom(), e.getTo());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Location last = lastLocation.remove(e.getPlayer().getUniqueId());
        if (last != null) notifyExit(last);
    }

    private void handleTransition(Location from, Location to) {
        for (SpawnPoint sp : registry.values()) {
            if (isInside(sp, from) && !isInside(sp, to)) {
                auto.markPlayerLeft(sp);
            }
        }
    }

    private void notifyExit(Location loc) {
        for (SpawnPoint sp : registry.values()) {
            if (isInside(sp, loc)) {
                auto.markPlayerLeft(sp);
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
    // CONFIG LOAD / SAVE
    // =====================================================

    @SuppressWarnings("unchecked")
    private void loadFromConfig() {
        registry.clear();

        FileConfiguration cfg = configs.autoSpawn();
        List<Map<String, Object>> list =
                (List<Map<String, Object>>) cfg.getList("spawns");

        if (list == null) return;

        for (Map<String, Object> raw : list) {
            try {
                String mobId = (String) raw.get("mobId");
                String world = (String) raw.get("world");

                int x = (int) raw.get("x");
                int y = (int) raw.get("y");
                int z = (int) raw.get("z");

                int interval = (int) raw.get("intervalSeconds");
                int maxSpawns = (int) raw.get("maxSpawns");
                int radius = (int) raw.getOrDefault(
                        "arenaRadiusChunks",
                        DEFAULT_ARENA_RADIUS_CHUNKS
                );

                boolean enabled = (boolean) raw.getOrDefault("enabled", true);
                if (!enabled || !mobs.mobExists(mobId)) continue;

                SpawnPoint sp = new SpawnPoint(
                        mobId,
                        world,
                        x, y, z,
                        interval,
                        maxSpawns,
                        radius,
                        true
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
    // DTO
    // =====================================================

    public record AutoSpawnInfo(
            String mobId,
            String world,
            int x,
            int y,
            int z,
            int intervalSeconds,
            int maxSpawns
    ) {}
}
