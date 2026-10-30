package org.plugin.theMob.api.dungeon;

import java.util.*;
import org.bukkit.Location;
import org.plugin.theMob.api.DungeonRegion;

/** Bukkit ServicesManager service. Main thread only; compileOnly, never shade this API. */
public interface TheMobDungeonApi {
  int API_VERSION = 1;

  int apiVersion();

  Optional<DungeonRegion> at(Location location);

  Collection<DungeonRegion> regions();

  Collection<DungeonRegion> intersecting(
      UUID world, double minX, double minZ, double maxX, double maxZ);

  /** True for any reserved footprint touched by a circular claim, including unfinished builds. */
  boolean blocksClaim(Location center, double radius);

  /**
   * Trusted plugin completion. Online participants must be alive and inside this ready dungeon. The
   * most recent 256 run IDs per dungeon are durably deduplicated.
   */
  boolean complete(String dungeonId, UUID runId, Collection<UUID> participants);

  /** Additive exit-altar capability. Older implementations keep these safe defaults. */
  default boolean supportsExitAltars() {
    return false;
  }

  /** Online, alive, permission, ready dungeon, physical altar and maximum eight-block distance. */
  default boolean atExitAltar(org.bukkit.entity.Player player, String dungeonId) {
    return false;
  }
}
