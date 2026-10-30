package org.plugin.theMob.dungeon;

import java.util.*;

/** Small shared voxel layout for generation and upgrading an existing boss room. */
public final class AltarLayout {
  public static final int VERSION = 1;

  public record Pos(int x, int y, int z) {}

  private AltarLayout() {}

  static Optional<Pos> choose(List<DungeonPlan.Room> rooms) {
    var room =
        rooms.stream()
            .filter(r -> r.level() > 0 && !r.name().equals("Haupthalle"))
            .max(
                Comparator.comparingInt(
                        (DungeonPlan.Room r) -> r.name().equals("Bosskammer") ? 1 : 0)
                    .thenComparingInt(DungeonPlan.Room::level)
                    .thenComparingInt(r -> -r.z())
                    .thenComparingInt(r -> -r.x()));
    return room.map(r -> new Pos(r.x() + 3, r.y(), r.z() + 3));
  }

  static Map<Pos, String> blocks(Pos center) {
    Map<Pos, String> blocks = new LinkedHashMap<>();
    for (int z = -2; z <= 2; z++)
      for (int x = -2; x <= 2; x++) {
        blocks.put(
            new Pos(center.x + x, center.y - 1, center.z + z),
            Math.abs(x) == 2 || Math.abs(z) == 2
                ? "minecraft:chiseled_polished_blackstone"
                : "minecraft:polished_blackstone_bricks");
        for (int y = 0; y < 4; y++)
          blocks.put(new Pos(center.x + x, center.y + y, center.z + z), "minecraft:air");
      }
    for (int x : new int[] {-2, 2})
      for (int z : new int[] {-2, 2}) {
        blocks.put(new Pos(center.x + x, center.y, center.z + z), "minecraft:crying_obsidian");
        blocks.put(new Pos(center.x + x, center.y + 1, center.z + z), "minecraft:soul_lantern");
      }
    blocks.put(center, "minecraft:enchanting_table");
    return Collections.unmodifiableMap(blocks);
  }
}
