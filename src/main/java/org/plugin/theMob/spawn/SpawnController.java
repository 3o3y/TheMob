package org.plugin.theMob.spawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Listener;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.core.ConfigService;
import org.plugin.theMob.mob.MobManager;
import org.plugin.theMob.mob.spawn.AutoSpawnManager;
import org.plugin.theMob.spawn.type.SpawnMode;
import org.plugin.theMob.spawn.type.SpawnType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class SpawnController implements Listener {

    private final TheMob plugin;
    private final MobManager mobs;
    private final AutoSpawnManager auto;
    private final ConfigService configs;

    private final Map<String, SpawnPoint> registry = new ConcurrentHashMap<>();

    public SpawnController(TheMob plugin, MobManager mobs, AutoSpawnManager auto) {
        this.plugin = plugin;
        this.mobs = mobs;
        this.auto = auto;
        this.configs = plugin.configs();
    }

    // =====================================================
    // LIFECYCLE
    // =====================================================
    public void start() {
        auto.bindController(this);
        loadFromConfig();
        auto.start();
    }

    public void stop() {
        registry.values().forEach(sp -> auto.unregister(sp.spawnId()));
        registry.clear();
        auto.stop();
    }

    // =====================================================
    // CREATE
    // =====================================================
    public boolean setAutoSpawnFixedPoint(String mobId, Location loc, int intervalSeconds, int maxSpawns) {
        return upsertFixedOrRadius(SpawnType.FIXED_POINT, mobId, loc, intervalSeconds, maxSpawns, 0, 0);
    }

    public boolean setRandomRadius(String mobId, Location loc, int intervalSeconds, int maxSpawns, int minRadius, int maxRadius) {
        return upsertFixedOrRadius(SpawnType.RANDOM_RADIUS, mobId, loc, intervalSeconds, maxSpawns, minRadius, maxRadius);
    }

    private boolean upsertFixedOrRadius(
            SpawnType type,
            String mobId,
            Location loc,
            int intervalSeconds,
            int maxSpawns,
            int minRadius,
            int maxRadius
    ) {
        if (!mobs.mobExists(mobId)) return false;
        if (loc == null || loc.getWorld() == null) return false;

        SpawnPoint sp = new SpawnPoint(
                type,
                mobId,
                loc.getWorld().getName(),
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ(),
                intervalSeconds,
                maxSpawns,
                true,
                2,
                minRadius,
                maxRadius,
                null,
                SpawnMode.ENDLESS,
                null,
                null,
                "",
                60,
                null, null, null
        );

        registry.put(sp.spawnId(), sp);
        auto.register(sp);
        saveToConfig();
        return true;
    }

    public boolean setFollowPlayer(
            String playerName,
            String mobId,
            int intervalSeconds,
            int maxSpawns,
            SpawnMode mode,
            int minDistance,
            int maxDistance,
            String message
    ) {
        if (!mobs.mobExists(mobId)) return false;

        SpawnPoint sp = new SpawnPoint(
                SpawnType.FOLLOW_PLAYER,
                mobId,
                null, 0, 0, 0,
                intervalSeconds,
                maxSpawns,
                true,
                0,
                0, 0,
                playerName,
                mode,
                minDistance,
                maxDistance,
                message,
                60,
                null, null, null
        );

        registry.put(sp.spawnId(), sp);
        auto.register(sp);

        if (mode == SpawnMode.ENDLESS) {
            saveToConfig();
        }
        return true;
    }

    public boolean setRandomWorld(
            String worldName,
            String mobId,
            int intervalSeconds,
            int maxSpawns,
            SpawnMode mode,
            int messageTimerSeconds,
            String message
    ) {
        if (!mobs.mobExists(mobId)) return false;

        World w = Bukkit.getWorld(worldName);
        if (w == null) return false;

        Location base = w.getSpawnLocation();

        SpawnPoint sp = new SpawnPoint(
                SpawnType.RANDOM_WORLD,
                mobId,
                worldName,
                base.getBlockX(),
                base.getBlockY(),
                base.getBlockZ(),
                intervalSeconds,
                maxSpawns,
                true,
                0,
                0, 0,
                null,
                mode,
                null,
                null,
                message,
                messageTimerSeconds,
                null, null, null
        );

        registry.put(sp.spawnId(), sp);
        auto.register(sp);

        if (mode == SpawnMode.ENDLESS) {
            saveToConfig();
        }
        return true;
    }

    // =====================================================
    // DELETE (API FÜR MobCommand)
    // =====================================================
    public boolean deleteFollowPlayer(String player, String mobId) {
        return deleteBySpawnId("follow@" + player + ":" + mobId.toLowerCase());
    }

    public boolean deleteRandomWorld(String world, String mobId) {
        return deleteBySpawnId("randomworld@" + world + ":" + mobId.toLowerCase());
    }

    public boolean deleteRandomRadiusAt(String mobId, Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        String sid = mobId.toLowerCase() + "@radius@" +
                loc.getWorld().getName() + ":" +
                loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
        return deleteBySpawnId(sid);
    }

    public boolean deleteBySpawnId(String spawnId) {
        SpawnPoint sp = registry.remove(spawnId);
        if (sp == null) return false;

        auto.unregister(sp.spawnId());

        if (sp.mode() == SpawnMode.ENDLESS) {
            saveToConfig();
        }
        return true;
    }

    public boolean deleteAutoSpawnByMobId(String mobId) {
        boolean removed = false;
        for (Iterator<SpawnPoint> it = registry.values().iterator(); it.hasNext();) {
            SpawnPoint sp = it.next();
            if (sp.type() == SpawnType.FIXED_POINT && sp.mobId().equalsIgnoreCase(mobId)) {
                auto.unregister(sp.spawnId());
                it.remove();
                removed = true;
            }
        }
        if (removed) saveToConfig();
        return removed;
    }

    // =====================================================
    // LIST (API FÜR MobCommand)
    // =====================================================
    public List<AutoSpawnInfo> listAutoSpawns() {
        List<AutoSpawnInfo> out = new ArrayList<>();
        for (SpawnPoint sp : registry.values()) {
            if (sp.type() != SpawnType.FIXED_POINT) continue;
            Location l = sp.baseLocation();
            if (l == null) continue;
            out.add(new AutoSpawnInfo(
                    sp.mobId(),
                    l.getWorld().getName(),
                    l.getBlockX(),
                    l.getBlockY(),
                    l.getBlockZ(),
                    sp.intervalSeconds(),
                    sp.maxSpawns(),
                    sp.arenaRadiusChunks()
            ));
        }
        return out;
    }

    public List<String> listAllLines() {
        List<String> out = new ArrayList<>();
        for (SpawnPoint sp : registry.values()) {
            Location l = sp.lastLocationFallbackBase();
            if (l == null) continue;
            out.add(l.getWorld().getName() + " " + sp.mobId() +
                    " " + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ() +
                    " " + sp.type().name());
        }
        return out;
    }

    // =====================================================
    // CONFIG LOAD
    // =====================================================
    @SuppressWarnings("unchecked")
    private void loadFromConfig() {
        registry.clear();

        FileConfiguration cfg = configs.autoSpawn();
        List<Map<String, Object>> list = (List<Map<String, Object>>) cfg.getList("spawns");
        if (list == null) return;

        for (Map<String, Object> raw : list) {
            try {
                SpawnMode mode = SpawnMode.fromString((String) raw.get("mode"));

                if (mode == SpawnMode.ONETIME) continue;

                SpawnType type = SpawnType.valueOf(
                        String.valueOf(raw.get("type")).toUpperCase(Locale.ROOT)
                );

                String mobId = String.valueOf(raw.get("mobId"));
                if (!mobs.mobExists(mobId)) continue;

                SpawnPoint sp = new SpawnPoint(
                        type,
                        mobId,
                        (String) raw.get("world"),
                        num(raw.get("x")),
                        num(raw.get("y")),
                        num(raw.get("z")),
                        num(raw.get("intervalSeconds")),
                        num(raw.get("maxSpawns")),
                        true,
                        raw.containsKey("arenaRadiusChunks") ? num(raw.get("arenaRadiusChunks")) : null,
                        raw.containsKey("minRadius") ? num(raw.get("minRadius")) : null,
                        raw.containsKey("maxRadius") ? num(raw.get("maxRadius")) : null,
                        (String) raw.get("player"),
                        mode,
                        raw.containsKey("minDistance") ? num(raw.get("minDistance")) : null,
                        raw.containsKey("maxDistance") ? num(raw.get("maxDistance")) : null,
                        (String) raw.getOrDefault("message", ""),
                        raw.containsKey("messageTimerSeconds") ? num(raw.get("messageTimerSeconds")) : null,
                        raw.containsKey("lastX") ? num(raw.get("lastX")) : null,
                        raw.containsKey("lastY") ? num(raw.get("lastY")) : null,
                        raw.containsKey("lastZ") ? num(raw.get("lastZ")) : null
                );

                registry.put(sp.spawnId(), sp);
                auto.register(sp);

            } catch (Exception ignored) {}
        }
    }


    // =====================================================
    // CONFIG SAVE
    // =====================================================
    private void saveToConfig() {
        FileConfiguration cfg = configs.autoSpawn();
        List<Map<String, Object>> out = new ArrayList<>();

        for (SpawnPoint sp : registry.values()) {

            if (sp.mode() == SpawnMode.ONETIME) continue;

            Map<String, Object> m = new LinkedHashMap<>();

            m.put("type", sp.type().name());
            m.put("mobId", sp.mobId());
            m.put("intervalSeconds", sp.intervalSeconds());
            m.put("maxSpawns", sp.maxSpawns());
            m.put("mode", sp.mode().name());

            if (sp.worldName() != null) {
                m.put("world", sp.worldName());
                m.put("x", sp.x());
                m.put("y", sp.y());
                m.put("z", sp.z());
            }

            if (sp.type() == SpawnType.FOLLOW_PLAYER) {
                m.put("player", sp.playerName());
                m.put("minDistance", sp.minDistance());
                m.put("maxDistance", sp.maxDistance());
                m.put("message", sp.message());
            }

            if (sp.type() == SpawnType.RANDOM_WORLD) {
                m.put("messageTimerSeconds", sp.messageTimerSeconds());
                m.put("message", sp.message());
            }

            if (sp.lastX() != null) m.put("lastX", sp.lastX());
            if (sp.lastY() != null) m.put("lastY", sp.lastY());
            if (sp.lastZ() != null) m.put("lastZ", sp.lastZ());

            out.add(m);
        }

        cfg.set("spawns", out);
        configs.saveAutoSpawn();
    }

    private static int num(Object o) {
        if (o instanceof Number n) return n.intValue();
        return Integer.parseInt(String.valueOf(o));
    }

    public AutoSpawnManager getAutoSpawnManager() {
        return auto;
    }


    // =====================================================
    // RECORD
    // =====================================================
    public record AutoSpawnInfo(
            String mobId,
            String world,
            int x,
            int y,
            int z,
            int intervalSeconds,
            int maxSpawns,
            int arenaRadiusChunks
    ) {}
    public void updateRuntimeLocation(String spawnId, int x, int y, int z) {
        SpawnPoint sp = registry.get(spawnId);
        if (sp == null) return;

        SpawnPoint updated = new SpawnPoint(
                sp.type(),
                sp.mobId(),
                sp.worldName(),
                sp.x(), sp.y(), sp.z(),
                sp.intervalSeconds(),
                sp.maxSpawns(),
                sp.enabled(),
                sp.arenaRadiusChunks(),
                sp.minRadius(),
                sp.maxRadius(),
                sp.playerName(),
                sp.mode(),
                sp.minDistance(),
                sp.maxDistance(),
                sp.message(),
                sp.messageTimerSeconds(),
                x, y, z
        );

        registry.put(spawnId, updated);
    }
    public void onRuntimeSpawnRemoved(String spawnId) {
        registry.remove(spawnId);
    }
    public boolean deleteAllByMobId(String mobId) {
        boolean removed = false;

        for (Iterator<SpawnPoint> it = registry.values().iterator(); it.hasNext();) {
            SpawnPoint sp = it.next();

            if (!sp.mobId().equalsIgnoreCase(mobId)) continue;

            auto.unregister(sp.spawnId());
            it.remove();
            removed = true;
        }

        if (removed) {
            saveToConfig(); // speichert nur ENDLESS
        }

        return removed;
    }

}
