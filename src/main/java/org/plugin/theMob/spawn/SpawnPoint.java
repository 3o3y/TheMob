package org.plugin.theMob.spawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

public final class SpawnPoint {

    private final String mobId;
    private final String worldName;
    private final int x, y, z;
    private final int intervalSeconds;
    private final int maxSpawns;
    private final int arenaRadiusChunks;
    private final boolean enabled;

    private volatile long lastPlayerSeenTick = -1;

    public SpawnPoint(
            String mobId,
            String worldName,
            int x, int y, int z,
            int intervalSeconds,
            int maxSpawns,
            int arenaRadiusChunks,
            boolean enabled
    ) {
        this.mobId = Objects.requireNonNull(mobId).toLowerCase();
        this.worldName = Objects.requireNonNull(worldName);
        this.x = x;
        this.y = y;
        this.z = z;
        this.intervalSeconds = Math.max(1, intervalSeconds);
        this.maxSpawns = Math.max(1, maxSpawns);
        this.arenaRadiusChunks = Math.max(0, arenaRadiusChunks);
        this.enabled = enabled;
    }

    public String spawnId() {
        return mobId + "@" + worldName + ":" + x + "," + y + "," + z;
    }

    public String mobId() { return mobId; }
    public String worldName() { return worldName; }
    public int baseChunkX() { return x >> 4; }
    public int baseChunkZ() { return z >> 4; }
    public int intervalSeconds() { return intervalSeconds; }
    public int maxSpawns() { return maxSpawns; }
    public int arenaRadiusChunks() { return arenaRadiusChunks; }
    public boolean enabled() { return enabled; }

    public Location baseLocation() {
        World w = Bukkit.getWorld(worldName);
        return w == null ? null : new Location(w, x + 0.5, y, z + 0.5);
    }

    public void markPlayerSeen(long tick) {
        lastPlayerSeenTick = tick;
    }

    public boolean inactiveFor(long ticks, long now) {
        return lastPlayerSeenTick > 0 && (now - lastPlayerSeenTick) >= ticks;
    }
}
