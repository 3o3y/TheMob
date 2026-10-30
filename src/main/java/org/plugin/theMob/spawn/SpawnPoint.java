package org.plugin.theMob.spawn;

import java.util.Locale;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.plugin.theMob.spawn.type.SpawnMode;
import org.plugin.theMob.spawn.type.SpawnType;

public final class SpawnPoint {
  private static final int DEFAULT_ARENA_RADIUS_CHUNKS = 2;
  private final SpawnType type;
  private final String mobId;
  private final int intervalSeconds;
  private final int maxSpawns;
  private final boolean enabled;
  private final String worldName;
  private final int x;
  private final int y;
  private final int z;
  private final int arenaRadiusChunks;
  private final int minRadius;
  private final int maxRadius;
  private final String playerName;
  private final SpawnMode mode;
  private final int minDistance;
  private final int maxDistance;
  private final String message;
  private final int messageTimerSeconds;
  private final Integer lastX;
  private final Integer lastY;
  private final Integer lastZ;

  public SpawnPoint(
      SpawnType type,
      String mobId,
      String worldName,
      int x,
      int y,
      int z,
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
      Integer lastX,
      Integer lastY,
      Integer lastZ) {
    this.type = Objects.requireNonNull(type, "type");
    this.mobId = Objects.requireNonNull(mobId, "mobId").toLowerCase(Locale.ROOT);
    this.worldName = worldName;
    this.x = x;
    this.y = y;
    this.z = z;
    this.intervalSeconds = Math.max(1, intervalSeconds);
    this.maxSpawns = Math.max(1, maxSpawns);
    this.enabled = enabled;
    this.arenaRadiusChunks = Math.max(0, arenaRadiusChunks == null ? 2 : arenaRadiusChunks);
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

  public String spawnId() {
    return switch (this.type) {
      case FOLLOW_PLAYER -> "follow@" + this.safe(this.playerName) + ":" + this.mobId;
      case RANDOM_WORLD -> "randomworld@" + this.safe(this.worldName) + ":" + this.mobId;
      case RANDOM_RADIUS ->
          this.mobId
              + "@radius@"
              + this.safe(this.worldName)
              + ":"
              + this.x
              + ","
              + this.y
              + ","
              + this.z;
      case FIXED_POINT ->
          this.mobId
              + "@fixed@"
              + this.safe(this.worldName)
              + ":"
              + this.x
              + ","
              + this.y
              + ","
              + this.z;
    };
  }

  private String safe(String s) {
    return s == null ? "null" : s;
  }

  public SpawnType type() {
    return this.type;
  }

  public String mobId() {
    return this.mobId;
  }

  public int intervalSeconds() {
    return this.intervalSeconds;
  }

  public int maxSpawns() {
    return this.maxSpawns;
  }

  public boolean enabled() {
    return this.enabled;
  }

  public String worldName() {
    return this.worldName;
  }

  public int x() {
    return this.x;
  }

  public int y() {
    return this.y;
  }

  public int z() {
    return this.z;
  }

  public int arenaRadiusChunks() {
    return this.arenaRadiusChunks;
  }

  public int minRadius() {
    return this.minRadius;
  }

  public int maxRadius() {
    return this.maxRadius;
  }

  public String playerName() {
    return this.playerName;
  }

  public SpawnMode mode() {
    return this.mode;
  }

  public int minDistance() {
    return this.minDistance;
  }

  public int maxDistance() {
    return this.maxDistance;
  }

  public String message() {
    return this.message;
  }

  public int messageTimerSeconds() {
    return this.messageTimerSeconds;
  }

  public Integer lastX() {
    return this.lastX;
  }

  public Integer lastY() {
    return this.lastY;
  }

  public Integer lastZ() {
    return this.lastZ;
  }

  public Location baseLocation() {
    if (this.type == SpawnType.RANDOM_WORLD) {
      return null;
    } else if (this.worldName == null) {
      return null;
    } else {
      World w = Bukkit.getWorld(this.worldName);
      return w == null ? null : new Location(w, this.x + 0.5, this.y, this.z + 0.5);
    }
  }

  public Location lastLocationFallbackBase() {
    if (this.type != SpawnType.RANDOM_WORLD) {
      return this.baseLocation();
    } else if (this.worldName == null) {
      return null;
    } else {
      World w = Bukkit.getWorld(this.worldName);
      if (w == null) {
        return null;
      } else {
        return this.lastX != null && this.lastY != null && this.lastZ != null
            ? new Location(
                w, this.lastX.intValue() + 0.5, this.lastY.intValue(), this.lastZ.intValue() + 0.5)
            : w.getSpawnLocation();
      }
    }
  }

  public boolean isInsideArena(Location loc) {
    if (loc == null || loc.getWorld() == null) {
      return false;
    } else if (this.worldName == null) {
      return false;
    } else if (!loc.getWorld().getName().equals(this.worldName)) {
      return false;
    } else {
      int cx = loc.getBlockX() >> 4;
      int cz = loc.getBlockZ() >> 4;
      int baseCx = this.x >> 4;
      int baseCz = this.z >> 4;
      return Math.abs(cx - baseCx) <= this.arenaRadiusChunks
          && Math.abs(cz - baseCz) <= this.arenaRadiusChunks;
    }
  }
}
