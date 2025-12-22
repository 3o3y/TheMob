package org.plugin.theMob.spawn;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;

public record SpawnPoint(
        String mobId,
        String world,
        double x,
        double y,
        double z,
        int intervalSeconds,
        int maxAlive,
        double playerRange,
        boolean requiresPlayer,
        boolean enabled
) {
// CREATE FROM PLAYER POSITION
    public static SpawnPoint fromPlayerBlock(
            String mobId,
            Location playerLoc,
            int intervalSeconds,
            int maxAlive
    ) {
        Location b = playerLoc.getBlock().getLocation().add(0.5, 0, 0.5);
        return new SpawnPoint(
                mobId.toLowerCase(),
                b.getWorld().getName(),
                b.getX(),
                b.getY(),
                b.getZ(),
                intervalSeconds,
                maxAlive,
                20.0,
                false,
                true
        );
    }
// UNIQUE ID (FOR REGISTRY / MAP KEYS)
    public String spawnId() {
        return mobId + "@" + world + ":" + (int) x + ":" + (int) y + ":" + (int) z;
    }
    public int chunkX() {
        return ((int) Math.floor(x)) >> 4;
    }
    public int chunkZ() {
        return ((int) Math.floor(z)) >> 4;
    }
    public Location location(World w) {
        return new Location(w, x, y, z);
    }
// SERIALIZE
    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("mobId", mobId);
        m.put("world", world);
        m.put("x", x);
        m.put("y", y);
        m.put("z", z);
        m.put("intervalSeconds", intervalSeconds);
        m.put("maxAlive", maxAlive);
        m.put("playerRange", playerRange);
        m.put("requiresPlayer", requiresPlayer);
        m.put("enabled", enabled);
        return m;
    }
// DESERIALIZE
    public static SpawnPoint fromMap(Map<?, ?> raw) {
        if (raw == null) return null;
        try {
            String mobId = String.valueOf(raw.get("mobId")).toLowerCase();
            String world = String.valueOf(raw.get("world"));
            double x = Double.parseDouble(String.valueOf(raw.get("x")));
            double y = Double.parseDouble(String.valueOf(raw.get("y")));
            double z = Double.parseDouble(String.valueOf(raw.get("z")));
            int intervalSeconds = raw.containsKey("intervalSeconds")
                    ? Integer.parseInt(String.valueOf(raw.get("intervalSeconds")))
                    : 300;
            int maxAlive = raw.containsKey("maxAlive")
                    ? Integer.parseInt(String.valueOf(raw.get("maxAlive")))
                    : 1;
            double playerRange = raw.containsKey("playerRange")
                    ? Double.parseDouble(String.valueOf(raw.get("playerRange")))
                    : 20.0;
            boolean requiresPlayer = raw.containsKey("requiresPlayer")
                    && Boolean.parseBoolean(String.valueOf(raw.get("requiresPlayer")));
            boolean enabled = !raw.containsKey("enabled")
                    || Boolean.parseBoolean(String.valueOf(raw.get("enabled")));
            return new SpawnPoint(
                    mobId,
                    world,
                    x,
                    y,
                    z,
                    intervalSeconds,
                    maxAlive,
                    playerRange,
                    requiresPlayer,
                    enabled
            );
        } catch (Exception e) {
            return null;
        }
    }
}
