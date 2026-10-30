package org.plugin.theMob.dungeon;

import java.util.*;

/** Three large architectural families, randomized rooms, routes, palettes and decorations. */
public final class DungeonPlanner {
  private final Random random;
  private final DungeonPlan p;
  private final byte stone, floor, trim, wood, light, bars, air = 0;

  private record Passage(int ax, int az, int bx, int bz, int y, int headroom) {}

  private final List<Passage> passages = new ArrayList<>();

  private DungeonPlanner(DungeonStyle style, long seed) {
    random = new Random(seed);
    int levels =
        switch (style) {
          case GOD_TOWER -> 10;
          case MAZE -> 1;
          case TOWER, WORLD_TREE -> 5 + random.nextInt(3);
          default -> 4 + random.nextInt(3);
        };
    p = new DungeonPlan(style, seed, levels);
    String[] walls =
        switch (style) {
          case CRYPT ->
              new String[] {"stone_bricks", "deepslate_bricks", "polished_blackstone_bricks"};
          case TOWER, GOD_TOWER -> new String[] {"stone_bricks", "tuff_bricks", "deepslate_tiles"};
          case MINE -> new String[] {"stone", "deepslate", "tuff"};
          case WORLD_TREE -> new String[] {"oak_log", "dark_oak_log", "spruce_log"};
          case MAZE -> new String[] {"stone_bricks", "mossy_stone_bricks", "deepslate_bricks"};
        };
    stone = block(walls[random.nextInt(walls.length)]);
    floor = block(style == DungeonStyle.MINE ? "polished_andesite" : "polished_deepslate");
    trim = block(style == DungeonStyle.MINE ? "stripped_dark_oak_log" : "chiseled_stone_bricks");
    wood = block("dark_oak_planks");
    light = block("sea_lantern");
    bars = block("iron_bars");
  }

  public static DungeonPlan generate(DungeonStyle style, long seed) {
    var generator = new DungeonPlanner(style, seed);
    generator.build();
    return generator.p;
  }

  private byte block(String name) {
    return p.id("minecraft:" + name);
  }

  private void build() {
    // Every plot has a continuous foundation and an enclosed forecourt, never a void landing.
    p.box(2, 6, 2, 110, 7, 130, stone);
    p.box(2, 8, 2, 110, 8, 130, floor);
    for (int y = 9; y <= 10; y++) {
      p.box(2, y, 2, 2, y, 130, trim);
      p.box(110, y, 2, 110, y, 130, trim);
      p.box(2, y, 130, 110, y, 130, trim);
    }
    if (p.style() == DungeonStyle.MINE) mountain();
    for (int level = 0; level < p.levels(); level++) {
      if (Thread.currentThread().isInterrupted())
        throw new java.util.concurrent.CancellationException();
      int y = DungeonPlan.floor(level);
      switch (p.style()) {
        case TOWER, GOD_TOWER -> towerLevel(level, y);
        case WORLD_TREE -> treeLevel(level, y);
        case MAZE -> mazeLevel(level, y);
        default -> gridLevel(level, y);
      }
      // A 13-block central hall leaves three clear blocks beside the stair stringers.
      if (p.style() != DungeonStyle.MAZE) corridor(56, 12, 56, 104, y, 6, 10);
      if (p.style() != DungeonStyle.MAZE)
        p.room(new DungeonPlan.Room(level + 1, 56, y + 1, 65, "Haupthalle"));
    }
    entrance();
    for (var c : passages)
      if (p.style() != DungeonStyle.MAZE)
        p.box(
            c.ax - 1,
            c.y,
            c.az - 1,
            c.bx + 1,
            c.y + c.headroom + 1,
            c.bz + 1,
            p.style() == DungeonStyle.WORLD_TREE ? wood : stone);
    for (var c : passages) {
      p.box(c.ax, c.y + 1, c.az, c.bx, c.y + c.headroom, c.bz, air);
      p.box(c.ax, c.y, c.az, c.bx, c.y, c.bz, floor);
    }
    for (int level = 0; level < p.levels(); level++)
      if (p.style() != DungeonStyle.MAZE) lamps(DungeonPlan.floor(level));
    p.box(50, 9, 110, 62, 19, 129, air);
    // Clear all shafts first, then place flights so later carving cannot erase earlier steps.
    for (int level = 0; level < p.levels() - 1; level++) {
      int y = DungeonPlan.floor(level);
      p.box(53, y + 1, 42, 59, y + 15, 53, air);
    }
    for (int level = 0; level < p.levels() - 1; level++) stairs(DungeonPlan.floor(level));
    p.addAltar();
    p.finish();
  }

  private void mountain() {
    int top = DungeonPlan.floor(p.levels() - 1) + 13;
    for (int y = 9; y <= top; y++) {
      int taper = Math.max(0, y - top + 10);
      for (int z = 4 + taper; z <= 108 - taper; z++)
        for (int x = 4 + taper; x <= 108 - taper; x++) {
          int dx = x - 56, dz = z - 56;
          if (dx * dx + dz * dz <= (54 - taper) * (54 - taper)) p.put(x, y, z, stone);
        }
    }
  }

  private void gridLevel(int level, int y) {
    if (p.style() == DungeonStyle.CRYPT) {
      p.box(4, y, 4, 108, y, 108, floor);
      p.box(4, y + 1, 4, 108, y + 11, 108, stone);
      p.box(5, y + 1, 5, 107, y + 10, 107, air);
      p.box(4, y + 11, 4, 108, y + 11, 108, floor);
    }
    int[] centers = {24, 56, 88};
    for (int rz = 0; rz < 3; rz++)
      for (int rx = 0; rx < 3; rx++) {
        int x = centers[rx], z = centers[rz];
        int hx = 10 + random.nextInt(4), hz = 10 + random.nextInt(4);
        room(x, z, hx, hz, y, p.style() == DungeonStyle.MINE);
        String name =
            level == p.levels() - 1 && rx == 1 && rz == 0
                ? "Bosskammer"
                : p.style() == DungeonStyle.MINE ? "Stollenhalle" : "Kammer";
        p.room(new DungeonPlan.Room(level + 1, x, y + 1, z, name));
      }
    // Random spanning tree plus optional loops: every chamber is accessible on every seed.
    List<int[]> edges = new ArrayList<>();
    for (int z = 0; z < 3; z++)
      for (int x = 0; x < 3; x++) {
        int a = z * 3 + x;
        if (x < 2) edges.add(new int[] {a, a + 1});
        if (z < 2) edges.add(new int[] {a, a + 3});
      }
    Collections.shuffle(edges, random);
    int[] parent = {0, 1, 2, 3, 4, 5, 6, 7, 8};
    for (int[] edge : edges) {
      int a = root(parent, edge[0]), b = root(parent, edge[1]);
      if (a != b || random.nextBoolean()) {
        parent[a] = b;
        corridor(
            centers[edge[0] % 3],
            centers[edge[0] / 3],
            centers[edge[1] % 3],
            centers[edge[1] / 3],
            y,
            3,
            7);
      }
    }
    if (p.style() == DungeonStyle.CRYPT) {
      for (int x : new int[] {4, 108})
        for (int z : new int[] {4, 108}) p.box(x - 1, y, z - 1, x + 1, y + 11, z + 1, trim);
    }
  }

  private static int root(int[] parent, int i) {
    while (parent[i] != i) i = parent[i];
    return i;
  }

  private void room(int x, int z, int hx, int hz, int y, boolean mine) {
    p.box(x - hx, y, z - hz, x + hx, y + 10, z + hz, stone);
    p.box(x - hx + 1, y + 1, z - hz + 1, x + hx - 1, y + 9, z + hz - 1, air);
    p.box(x - hx, y, z - hz, x + hx, y, z + hz, floor);
    if (mine) {
      for (int bx : new int[] {x - hx + 1, x + hx - 1})
        for (int bz : new int[] {z - hz + 1, z + hz - 1}) p.box(bx, y + 1, bz, bx, y + 8, bz, trim);
      for (int bz = z - hz + 3; bz < z + hz; bz += 6)
        p.box(x - hx + 1, y + 9, bz, x + hx - 1, y + 9, bz, wood);
    } else {
      for (int bx : new int[] {x - hx + 1, x + hx - 1})
        for (int bz : new int[] {z - hz + 1, z + hz - 1}) p.box(bx, y + 1, bz, bx, y + 8, bz, trim);
      p.box(x - 2, y, z - 2, x + 2, y, z + 2, trim);
    }
    p.put(x - hx + 2, y + 8, z, light);
    p.put(x + hx - 2, y + 8, z, light);
  }

  private void towerLevel(int level, int y) {
    for (int z = 5; z <= 107; z++)
      for (int x = 5; x <= 107; x++) {
        int d = (x - 56) * (x - 56) + (z - 56) * (z - 56);
        if (d <= 51 * 51) {
          p.put(x, y, z, floor);
          p.put(x, y + 11, z, floor);
          if (d >= 49 * 49) for (int h = 1; h <= 10; h++) p.put(x, y + h, z, stone);
        }
      }
    for (int x : new int[] {29, 83})
      for (int z : new int[] {29, 83}) {
        room(x, z, 10 + random.nextInt(3), 10 + random.nextInt(3), y, false);
        corridor(x, z, 56, z, y, 3, 7);
        p.room(
            new DungeonPlan.Room(
                level + 1,
                x,
                y + 1,
                z,
                level == p.levels() - 1 && z == 29
                    ? (x == 29 ? "Bosskammer" : "Obere Wachhalle")
                    : "Turmkammer"));
      }
    // Barred windows and exterior buttresses frame the vertical fortress.
    for (int x = 51; x <= 61; x++)
      for (int z : new int[] {6, 106}) p.box(x, y + 4, z, x, y + 7, z, bars);
    for (int x : new int[] {6, 106}) p.box(x, y + 4, 51, x, y + 7, 61, bars);
    if (level == p.levels() - 1) {
      for (int z = 5; z <= 107; z++)
        for (int x = 5; x <= 107; x++) {
          int d = (x - 56) * (x - 56) + (z - 56) * (z - 56);
          if (d >= 49 * 49 && d <= 51 * 51)
            p.box(x, y + 12, z, x, y + 13 + ((x + z) % 3 == 0 ? 1 : 0), z, trim);
        }
    }
  }

  private void corridor(int x1, int z1, int x2, int z2, int y, int half, int headroom) {
    int ax = Math.min(x1, x2), bx = Math.max(x1, x2), az = Math.min(z1, z2), bz = Math.max(z1, z2);
    if (x1 == x2) {
      ax -= half;
      bx += half;
    } else {
      az -= half;
      bz += half;
    }
    passages.add(new Passage(ax, az, bx, bz, y, headroom));
    for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
      for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) p.path(x, y + 1, z);
  }

  private void treeLevel(int level, int y) {
    byte leaves = block("oak_leaves[persistent=true,distance=1,waterlogged=false]");
    // Hollow trunk, four walkable branches, leaf-covered chambers and sprawling roots.
    for (int z = 37; z <= 75; z++)
      for (int x = 37; x <= 75; x++) {
        int d = (x - 56) * (x - 56) + (z - 56) * (z - 56);
        if (d <= 19 * 19) p.box(x, y, z, x, y + 11, z, stone);
        if (d < 16 * 16) p.box(x, y + 1, z, x, y + 10, z, air);
        if (d <= 19 * 19) p.put(x, y, z, wood);
      }
    for (int[] center : new int[][] {{14, 56}, {98, 56}, {56, 14}, {56, 98}}) {
      int cx = center[0], cz = center[1];
      if (cx == 14) cx += random.nextInt(5);
      if (cx == 98) cx -= random.nextInt(5);
      if (cz == 14) cz += random.nextInt(5);
      if (cz == 98) cz -= random.nextInt(5);
      int radius = 10 + random.nextInt(3);
      for (int z = cz - 12; z <= cz + 12; z++)
        for (int x = cx - 12; x <= cx + 12; x++) {
          int d = (x - cx) * (x - cx) + (z - cz) * (z - cz);
          if (d <= radius * radius) {
            p.put(x, y, z, wood);
            p.put(x, y + 9, z, leaves);
          }
          if (d >= (radius - 2) * (radius - 2) && d <= radius * radius)
            p.box(x, y + 1, z, x, y + 8, z, leaves);
        }
      corridor(cx, cz, 56, 56, y, 3, 7);
      p.room(
          new DungeonPlan.Room(
              level + 1,
              cx,
              y + 1,
              cz,
              level == p.levels() - 1 && center[1] == 14 ? "Bosskammer" : "Asthalle"));
      p.put(cx - 5, y + 8, cz, light);
      p.put(cx + 5, y + 8, cz, light);
    }
    if (level == 0)
      for (int d = 0; d < 38; d++) {
        int h = Math.max(1, 8 - d / 5);
        p.box(55 - d, 9, 54, 57 - d, 9 + h, 58, stone);
        p.box(55 + d, 9, 54, 57 + d, 9 + h, 58, stone);
      }
  }

  private void mazeLevel(int level, int y) {
    // Randomized depth-first spanning tree: 169 cells, all connected, no sealed chambers.
    p.box(4, y, 4, 108, y + 8, 108, stone);
    p.box(4, y, 4, 108, y, 108, floor);
    boolean[] visited = new boolean[169];
    var stack = new ArrayDeque<Integer>();
    int start = 12 * 13 + 6;
    stack.push(start);
    visited[start] = true;
    while (!stack.isEmpty()) {
      int cell = stack.peek(), cx = cell % 13, cz = cell / 13;
      int x = 8 + cx * 8, z = 8 + cz * 8;
      p.box(x - 2, y + 1, z - 2, x + 2, y + 6, z + 2, air);
      p.put(x, y + 7, z, light);
      List<Integer> next = new ArrayList<>();
      if (cx > 0 && !visited[cell - 1]) next.add(cell - 1);
      if (cx < 12 && !visited[cell + 1]) next.add(cell + 1);
      if (cz > 0 && !visited[cell - 13]) next.add(cell - 13);
      if (cz < 12 && !visited[cell + 13]) next.add(cell + 13);
      if (next.isEmpty()) {
        stack.pop();
        continue;
      }
      int to = next.get(random.nextInt(next.size()));
      visited[to] = true;
      stack.push(to);
      int tx = 8 + (to % 13) * 8, tz = 8 + (to / 13) * 8;
      int ax = Math.min(x, tx), bx = Math.max(x, tx), az = Math.min(z, tz), bz = Math.max(z, tz);
      p.box(ax - 2, y + 1, az - 2, bx + 2, y + 6, bz + 2, air);
      for (int px = ax; px <= bx; px++) for (int pz = az; pz <= bz; pz++) p.path(px, y + 1, pz);
    }
    corridor(56, 104, 56, 108, y, 2, 6);
    p.room(new DungeonPlan.Room(1, 8, y + 1, 8, "Bosskammer"));
  }

  private void lamps(int y) {
    for (int z = 16; z <= 100; z += 12) {
      p.put(51, y + 8, z, light);
      p.put(61, y + 8, z, light);
    }
  }

  private void stairs(int y) {
    byte steps =
        block("stone_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]");
    byte fence = block("stone_brick_wall");
    for (int step = 0; step < 12; step++) {
      int z = 42 + step, top = y + step + 1;
      p.box(53, top, z, 59, top, z, stone);
      p.box(54, top, z, 58, top, z, steps);
      p.put(53, top + 1, z, fence);
      p.put(59, top + 1, z, fence);
    }
    // The upper landing meets the top tread at the next floor, with no jump or ladder.
    p.box(53, y + 12, 54, 59, y + 12, 55, floor);
    p.box(54, y + 13, 54, 58, y + 15, 55, air);
  }

  private void entrance() {
    int y = DungeonPlan.floor(0);
    // Approach, gatehouse and a clear 13-wide, 11-high opening for groups.
    corridor(56, 90, 56, 115, y, 6, 11);
    p.box(35, y, 109, 77, y, 129, trim);
    p.box(49, y, 109, 63, y, 129, floor);
    for (int x : new int[] {44, 68}) {
      p.box(x - 3, y + 1, 105, x + 3, y + 19, 113, stone);
      p.box(x - 4, y + 17, 104, x + 4, y + 18, 114, trim);
      for (int xx = x - 3; xx <= x + 3; xx += 2) p.box(xx, y + 20, 105, xx, y + 21, 113, trim);
      p.put(x, y + 15, 114, light);
    }
    p.box(48, y + 12, 106, 64, y + 15, 112, stone);
    p.box(50, y + 12, 106, 62, y + 12, 112, trim);
    p.box(51, y + 1, 105, 61, y + 11, 114, air);
    for (int x : new int[] {38, 74}) {
      p.box(x, y + 1, 124, x, y + 4, 124, trim);
      p.put(x, y + 5, 124, light);
    }
    p.room(new DungeonPlan.Room(0, 56, y + 1, 123, "Eingang"));
  }
}
