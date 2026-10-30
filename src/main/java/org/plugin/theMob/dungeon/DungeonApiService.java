package org.plugin.theMob.dungeon;

import java.util.*;
import org.bukkit.*;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.api.DungeonRegion;
import org.plugin.theMob.api.dungeon.*;

/**
 * Stable facade, including across /mob reload. No references to mutable dungeon services escape.
 */
public final class DungeonApiService implements TheMobDungeonApi {
  private final TheMob plugin;

  public DungeonApiService(TheMob plugin) {
    this.plugin = plugin;
  }

  private DungeonService service() {
    if (!Bukkit.isPrimaryThread())
      throw new IllegalStateException("Dungeon API requires the server thread");
    if (!plugin.isEnabled() || plugin.dungeons() == null)
      throw new IllegalStateException("TheMob dungeons unavailable");
    return plugin.dungeons();
  }

  @Override
  public int apiVersion() {
    return API_VERSION;
  }

  @Override
  public Optional<DungeonRegion> at(Location l) {
    return service().regionAt(l);
  }

  @Override
  public Collection<DungeonRegion> regions() {
    return service().regions();
  }

  @Override
  public Collection<DungeonRegion> intersecting(
      UUID world, double x, double z, double bx, double bz) {
    if (world == null
        || !Double.isFinite(x)
        || !Double.isFinite(z)
        || !Double.isFinite(bx)
        || !Double.isFinite(bz)
        || x > bx
        || z > bz) throw new IllegalArgumentException("Invalid query bounds");
    return service().intersecting(world, x, z, bx, bz);
  }

  @Override
  public boolean blocksClaim(Location center, double radius) {
    if (center == null
        || center.getWorld() == null
        || !Double.isFinite(center.getX())
        || !Double.isFinite(center.getZ())
        || !Double.isFinite(radius)
        || radius < 0) throw new IllegalArgumentException("Invalid claim");
    for (var r :
        intersecting(
            center.getWorld().getUID(),
            center.getX() - radius,
            center.getZ() - radius,
            center.getX() + radius,
            center.getZ() + radius)) {
      double dx = Math.max(r.minX() - center.getX(), Math.max(0, center.getX() - r.maxX()));
      double dz = Math.max(r.minZ() - center.getZ(), Math.max(0, center.getZ() - r.maxZ()));
      if (dx * dx + dz * dz <= radius * radius) return true;
    }
    return false;
  }

  @Override
  public boolean complete(String id, UUID run, Collection<UUID> participants) {
    return service().complete(id, run, participants, "api");
  }

  @Override
  public boolean supportsExitAltars() {
    service();
    return true;
  }

  @Override
  public boolean atExitAltar(org.bukkit.entity.Player player, String id) {
    return service().atExitAltar(player, id);
  }
}
