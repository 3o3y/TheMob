package org.plugin.theMob.spawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

public final class SpawnPoint {

    private static final int DEFAULT_ARENA_RADIUS_CHUNKS = 2; // 5x5 Chunks

    private final String mobId;
    private final String worldName;
    private final int x, y, z;
    private final int intervalSeconds;
    private final int maxSpawns;
    private final boolean enabled;
    private final int arenaRadiusChunks;

    public SpawnPoint(
            String mobId,
            String worldName,
            int x, int y, int z,
            int intervalSeconds,
            int maxSpawns,
            boolean enabled
    ) {
        this.mobId = Objects.requireNonNull(mobId).toLowerCase();
        this.worldName = Objects.requireNonNull(worldName);
        this.x = x;
        this.y = y;
        this.z = z;
        this.intervalSeconds = Math.max(1, intervalSeconds);
        this.maxSpawns = Math.max(1, maxSpawns);
        this.enabled = enabled;
        this.arenaRadiusChunks = DEFAULT_ARENA_RADIUS_CHUNKS;
    }

    // =====================================================
    // BASIC DATA
    // =====================================================

    public String spawnId() {
        return mobId + "@" + worldName + ":" + x + "," + y + "," + z;
    }

    public String mobId() { return mobId; }
    public String worldName() { return worldName; }
    public int intervalSeconds() { return intervalSeconds; }
    public int maxSpawns() { return maxSpawns; }
    public boolean enabled() { return enabled; }
    public int arenaRadiusChunks() { return arenaRadiusChunks; }

    // =====================================================
    // LOCATION
    // =====================================================

    public Location baseLocation() {
        World w = Bukkit.getWorld(worldName);
        return w == null ? null : new Location(w, x + 0.5, y, z + 0.5);
    }

    // =====================================================
    // ARENA CHECK (🔥 WICHTIG!)
    // =====================================================

    public boolean isInsideArena(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(worldName)) return false;

        int cx = loc.getBlockX() >> 4;
        int cz = loc.getBlockZ() >> 4;

        int baseCx = x >> 4;
        int baseCz = z >> 4;

        return Math.abs(cx - baseCx) <= arenaRadiusChunks
                && Math.abs(cz - baseCz) <= arenaRadiusChunks;
    }
}
