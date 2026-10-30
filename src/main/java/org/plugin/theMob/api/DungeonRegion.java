package org.plugin.theMob.api;

import java.util.Objects;
import java.util.UUID;
import org.bukkit.Location;

/**
 * Immutable reserved footprint at every height. Maximum X/Z are exclusive. No world/chunk loading.
 */
public record DungeonRegion(
    String id, UUID worldId, int minX, int minZ, int maxX, int maxZ, boolean ready) {
  public DungeonRegion {
    Objects.requireNonNull(id);
    Objects.requireNonNull(worldId);
    if (id.isBlank() || minX >= maxX || minZ >= maxZ)
      throw new IllegalArgumentException("Invalid dungeon footprint");
  }

  public boolean contains(Location at) {
    return at != null
        && at.getWorld() != null
        && worldId.equals(at.getWorld().getUID())
        && Double.isFinite(at.getX())
        && Double.isFinite(at.getZ())
        && at.getX() >= minX
        && at.getX() < maxX
        && at.getZ() >= minZ
        && at.getZ() < maxZ;
  }
}
