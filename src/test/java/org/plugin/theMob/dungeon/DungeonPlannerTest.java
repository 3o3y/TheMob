package org.plugin.theMob.dungeon;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DungeonPlannerTest {
  @ParameterizedTest
  @EnumSource(DungeonStyle.class)
  void allRoomsAndLevelsAreReachableFromTheLargeEntrance(DungeonStyle style) {
    DungeonPlan plan = DungeonPlanner.generate(style, 81420373L);
    assertTrue(plan.pathLength() >= 500);
    assertTrue(plan.blockCount() > 40_000);
    if (style == DungeonStyle.GOD_TOWER) assertEquals(10, plan.levels());
    Set<Integer> reachable = reachable(plan);
    assertTrue(
        reachable.containsAll(plan.pathPositions()), "All counted path blocks must be reachable");
    for (var room : plan.rooms())
      assertTrue(
          reachable.contains(key(room.x(), room.y(), room.z())), style + " unreachable: " + room);
    for (int x = 52; x <= 60; x++)
      for (int y = 9; y <= 17; y++)
        assertEquals("minecraft:air", plan.block(x, y, 110), "Entrance must remain wide and high");
  }

  @ParameterizedTest
  @EnumSource(DungeonStyle.class)
  void everySeedHasAtLeastFiveHundredBlocksOfPathsAndAUniqueLayout(DungeonStyle style) {
    Set<Integer> signatures = new HashSet<>();
    for (long seed = 0; seed < 8; seed++) {
      var plan = DungeonPlanner.generate(style, seed);
      assertTrue(plan.pathLength() >= 500, style + " seed " + seed);
      int signature = Objects.hash(plan.palette(), plan.rooms(), plan.blockCount());
      for (var chunk : plan.chunks())
        signature = 31 * signature + Arrays.hashCode(chunk.positions());
      assertTrue(signatures.add(signature), "Identical generated layout");
    }
    var a = DungeonPlanner.generate(style, 1234);
    var b = DungeonPlanner.generate(style, 1234);
    assertEquals(a.palette(), b.palette());
    assertEquals(a.rooms(), b.rooms());
    assertEquals(a.blockCount(), b.blockCount());
  }

  @Test
  void chunksContainEverySolidVoxelExactlyOnceAndRespectPlotBounds() {
    var p = DungeonPlanner.generate(DungeonStyle.GOD_TOWER, 123);
    Set<Integer> positions = new HashSet<>();
    int count = 0;
    for (var chunk : p.chunks())
      for (int pos : chunk.positions()) {
        assertTrue(positions.add(pos));
        assertEquals(chunk.x(), DungeonPlan.x(pos) >> 4);
        assertEquals(chunk.z(), DungeonPlan.z(pos) >> 4);
        assertTrue(p.paletteIndex(pos) > 0);
        count++;
      }
    assertEquals(p.blockCount(), count);
    for (int y = 0; y < p.height(); y++)
      for (int z = 0; z < DungeonPlan.DEPTH; z++)
        for (int x = 0; x < DungeonPlan.WIDTH; x++)
          if (!p.block(x, y, z).equals("minecraft:air"))
            assertTrue(positions.contains(key(x, y, z)));
  }

  private static int key(int x, int y, int z) {
    return (y * DungeonPlan.DEPTH + z) * DungeonPlan.WIDTH + x;
  }

  private static boolean air(DungeonPlan p, int x, int y, int z) {
    return p.block(x, y, z).equals("minecraft:air");
  }

  private static boolean stand(DungeonPlan p, int x, int y, int z) {
    return x >= 0
        && x < DungeonPlan.WIDTH
        && z >= 0
        && z < DungeonPlan.DEPTH
        && y > 0
        && y < p.height() - 1
        && air(p, x, y, z)
        && air(p, x, y + 1, z)
        && !air(p, x, y - 1, z)
        && !p.block(x, y - 1, z).contains("_wall")
        && !p.block(x, y - 1, z).contains("iron_bars");
  }

  private static Set<Integer> reachable(DungeonPlan p) {
    Set<Integer> seen = new HashSet<>();
    var queue = new ArrayDeque<int[]>();
    queue.add(new int[] {56, 9, 123});
    seen.add(key(56, 9, 123));
    while (!queue.isEmpty()) {
      int[] a = queue.remove();
      for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
        for (int dy : new int[] {0, 1, -1}) {
          int x = a[0] + d[0], y = a[1] + dy, z = a[2] + d[1];
          // A rising step needs head clearance above the previous position as well.
          if (dy == 1 && !air(p, a[0], a[1] + 2, a[2])) continue;
          if (stand(p, x, y, z) && seen.add(key(x, y, z))) queue.add(new int[] {x, y, z});
        }
    }
    return seen;
  }
}
