package org.plugin.theMob.api.dungeon;

import java.util.*;
import org.bukkit.Material;

/** Stable provider ID and display data, never commands or serialized teleport coordinates. */
public record DungeonTravelTarget(String id, String name, Material icon, List<String> lore) {
  public DungeonTravelTarget {
    Objects.requireNonNull(id);
    Objects.requireNonNull(name);
    Objects.requireNonNull(icon);
    if (id.isBlank()
        || name.isBlank()
        || icon == Material.AIR
        || icon == Material.CAVE_AIR
        || icon == Material.VOID_AIR) throw new IllegalArgumentException("Invalid travel target");
    lore = List.copyOf(lore);
  }
}
