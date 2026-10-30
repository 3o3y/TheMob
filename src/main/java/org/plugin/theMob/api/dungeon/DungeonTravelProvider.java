package org.plugin.theMob.api.dungeon;

import java.util.List;
import org.bukkit.entity.Player;

/** Optional Bukkit service, supplied by PK or another travel plugin. Main server thread only. */
public interface DungeonTravelProvider {
  List<DungeonTravelTarget> destinations(Player player, String dungeonId);

  /**
   * Revalidate altar, permission, current target and safe landing, then return teleport success.
   */
  boolean travel(Player player, String dungeonId, String targetId);
}
