package org.plugin.theMob.spawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.plugin.theMob.spawn.type.SpawnMode;
import org.plugin.theMob.spawn.type.SpawnType;

import java.util.Locale;
import java.util.Objects;

public final class SpawnPoint {

    private static final int DEFAULT_ARENA_RADIUS_CHUNKS = 2;

    private final SpawnType type;

    private final String mobId;
    private final int intervalSeconds;
    private final int maxSpawns;
    private final boolean enabled;

    private final String worldName;
    private final int x, y, z;

    private final int arenaRadiusChunks;

    private final int minRadius;
    private final int maxRadius;

    private final String playerName;
    private final SpawnMode mode;
    private final int minDistance;
    private final int maxDistance;
    private final String message;

    private final int messageTimerSeconds;

    private final Integer lastX, lastY, lastZ;

    // =========================================================
    // CTOR
    // =========================================================
    public SpawnPoint(
            SpawnType type,
            String mobId,

            String worldName,
            int x, int y, int z,

            int intervalSeconds,
            int maxSpawns,
            boolean enabled,

            Integer arenaRadiusChunks,

            Integer minRadius,
            Integer maxRadius,

            String playerName,
            SpawnMode mode,
            Integer minDistance,
            Integer maxDistance,
            String message,

            Integer messageTimerSeconds,

            Integer lastX, Integer lastY, Integer lastZ
    ) {
        this.type = Objects.requireNonNull(type, "type");
        this.mobId = Objects.requireNonNull(mobId, "mobId").toLowerCase(Locale.ROOT);

        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;

        this.intervalSeconds = Math.max(1, intervalSeconds);
        this.maxSpawns = Math.max(1, maxSpawns);
        this.enabled = enabled;

        this.arenaRadiusChunks = Math.max(0,
                arenaRadiusChunks == null ? DEFAULT_ARENA_RADIUS_CHUNKS : arenaRadiusChunks
        );

        this.minRadius = Math.max(0, minRadius == null ? 0 : minRadius);
        this.maxRadius = Math.max(0, maxRadius == null ? 0 : maxRadius);

        this.playerName = playerName;
        this.mode = mode == null ? SpawnMode.ENDLESS : mode;

        this.minDistance = Math.max(0, minDistance == null ? 6 : minDistance);
        this.maxDistance = Math.max(this.minDistance, maxDistance == null ? 18 : maxDistance);

        this.message = message == null ? "" : message;
        this.messageTimerSeconds = Math.max(1, messageTimerSeconds == null ? 60 : messageTimerSeconds);

        this.lastX = lastX;
        this.lastY = lastY;
        this.lastZ = lastZ;
    }

    // =========================================================
    // IDS
    // =========================================================
    public String spawnId() {
        return switch (type) {
            case FOLLOW_PLAYER -> "follow@" + safe(playerName) + ":" + mobId;
            case RANDOM_WORLD -> "randomworld@" + safe(worldName) + ":" + mobId;
            case RANDOM_RADIUS -> mobId + "@radius@" + safe(worldName) + ":" + x + "," + y + "," + z;
            case FIXED_POINT -> mobId + "@fixed@" + safe(worldName) + ":" + x + "," + y + "," + z;
        };
    }

    private String safe(String s) {
        return s == null ? "null" : s;
    }

    // =========================================================
    // GETTERS
    // =========================================================
    public SpawnType type() { return type; }

    public String mobId() { return mobId; }
    public int intervalSeconds() { return intervalSeconds; }
    public int maxSpawns() { return maxSpawns; }
    public boolean enabled() { return enabled; }

    public String worldName() { return worldName; }
    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }

    public int arenaRadiusChunks() { return arenaRadiusChunks; }

    public int minRadius() { return minRadius; }
    public int maxRadius() { return maxRadius; }

    public String playerName() { return playerName; }
    public SpawnMode mode() { return mode; }
    public int minDistance() { return minDistance; }
    public int maxDistance() { return maxDistance; }
    public String message() { return message; }

    public int messageTimerSeconds() { return messageTimerSeconds; }

    public Integer lastX() { return lastX; }
    public Integer lastY() { return lastY; }
    public Integer lastZ() { return lastZ; }

    // =========================================================
    // LOC HELPERS
    // =========================================================
    public Location baseLocation() {
        if (type == SpawnType.RANDOM_WORLD) {
            return null;
        }
        if (worldName == null) return null;
        World w = Bukkit.getWorld(worldName);
        return w == null ? null : new Location(w, x + 0.5, y, z + 0.5);
    }


    public Location lastLocationFallbackBase() {
        if (type != SpawnType.RANDOM_WORLD) {
            return baseLocation();
        }

        if (worldName == null) return null;
        World w = Bukkit.getWorld(worldName);
        if (w == null) return null;

        if (lastX != null && lastY != null && lastZ != null) {
            return new Location(w, lastX + 0.5, lastY, lastZ + 0.5);
        }

        return w.getSpawnLocation();
    }


    public boolean isInsideArena(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (worldName == null) return false;
        if (!loc.getWorld().getName().equals(worldName)) return false;

        int cx = loc.getBlockX() >> 4;
        int cz = loc.getBlockZ() >> 4;

        int baseCx = x >> 4;
        int baseCz = z >> 4;

        return Math.abs(cx - baseCx) <= arenaRadiusChunks
                && Math.abs(cz - baseCz) <= arenaRadiusChunks;
    }
}
