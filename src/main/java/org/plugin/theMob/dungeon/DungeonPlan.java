package org.plugin.theMob.dungeon;

import java.util.*;

/** Pure, bounded voxel plan. No world access: planning runs off the server thread. */
public final class DungeonPlan {
  public static final int WIDTH = 113, DEPTH = 133, BASE_Y = 64, LEVEL_HEIGHT = 12;

  public record Room(int level, int x, int y, int z, String name) {}

  public record Chunk(int x, int z, int[] positions) {}

  private final DungeonStyle style;
  private final long seed;
  private final int levels, height;
  private final byte[] blocks;
  private final List<String> palette = new ArrayList<>(List.of("minecraft:air"));
  private final Map<String, Byte> ids = new HashMap<>();
  private final List<Room> rooms = new ArrayList<>();
  private List<Chunk> chunks;
  private int blockCount;
  private final Set<Integer> paths = new HashSet<>();
  private AltarLayout.Pos altar;

  public AltarLayout.Pos altar() {
    return altar;
  }

  void addAltar() {
    altar = AltarLayout.choose(rooms).orElseThrow();
    AltarLayout.blocks(altar).forEach((pos, block) -> put(pos.x(), pos.y(), pos.z(), id(block)));
  }

  DungeonPlan(DungeonStyle style, long seed, int levels) {
    this.style = style;
    this.seed = seed;
    this.levels = levels;
    height = 8 + levels * LEVEL_HEIGHT + 10;
    blocks = new byte[WIDTH * DEPTH * height];
    ids.put(palette.get(0), (byte) 0);
  }

  public DungeonStyle style() {
    return style;
  }

  public long seed() {
    return seed;
  }

  public int levels() {
    return levels;
  }

  public int height() {
    return height;
  }

  public int blockCount() {
    return blockCount;
  }

  public int pathLength() {
    return paths.size();
  }

  public Set<Integer> pathPositions() {
    return Set.copyOf(paths);
  }

  public List<Room> rooms() {
    return List.copyOf(rooms);
  }

  public List<String> palette() {
    return List.copyOf(palette);
  }

  public List<Chunk> chunks() {
    return chunks;
  }

  public static int floor(int level) {
    return 8 + level * LEVEL_HEIGHT;
  }

  public static int x(int position) {
    return position % WIDTH;
  }

  public static int z(int position) {
    return position / WIDTH % DEPTH;
  }

  public static int y(int position) {
    return position / (WIDTH * DEPTH);
  }

  private int index(int x, int y, int z) {
    return (y * DEPTH + z) * WIDTH + x;
  }

  byte id(String block) {
    return ids.computeIfAbsent(
        block,
        key -> {
          if (palette.size() >= 255) throw new IllegalStateException("Dungeon palette too large");
          byte value = (byte) palette.size();
          palette.add(key);
          return value;
        });
  }

  public String block(int x, int y, int z) {
    if (x < 0 || x >= WIDTH || z < 0 || z >= DEPTH || y < 0 || y >= height) return "minecraft:air";
    return palette.get(Byte.toUnsignedInt(blocks[index(x, y, z)]));
  }

  public int paletteIndex(int position) {
    return Byte.toUnsignedInt(blocks[position]);
  }

  void put(int x, int y, int z, byte block) {
    if (x < 0 || x >= WIDTH || z < 0 || z >= DEPTH || y < 0 || y >= height)
      throw new IllegalArgumentException("Dungeon block outside reserved bounds");
    blocks[index(x, y, z)] = block;
  }

  void box(int x1, int y1, int z1, int x2, int y2, int z2, byte block) {
    for (int y = y1; y <= y2; y++)
      for (int z = z1; z <= z2; z++) for (int x = x1; x <= x2; x++) put(x, y, z, block);
  }

  void room(Room room) {
    rooms.add(room);
  }

  void path(int x, int y, int z) {
    paths.add(index(x, y, z));
  }

  void finish() {
    // Count actual walkable centerline blocks, excluding stair shafts and overwritten cells.
    paths.removeIf(
        pos ->
            y(pos) + 1 >= height
                || blocks[pos] != 0
                || blocks[pos + WIDTH * DEPTH] != 0
                || y(pos) == 0
                || blocks[pos - WIDTH * DEPTH] == 0);
    if (pathLength() < 500)
      throw new IllegalStateException("Dungeon paths shorter than 500 blocks");
    List<Chunk> result = new ArrayList<>();
    for (int cz = 0; cz <= (DEPTH - 1) / 16; cz++) {
      for (int cx = 0; cx <= (WIDTH - 1) / 16; cx++) {
        int[] positions = new int[16 * 16 * height];
        int count = 0;
        for (int y = 0; y < height; y++)
          for (int z = cz * 16; z < Math.min(DEPTH, (cz + 1) * 16); z++)
            for (int x = cx * 16; x < Math.min(WIDTH, (cx + 1) * 16); x++) {
              int position = index(x, y, z);
              if (blocks[position] != 0) positions[count++] = position;
            }
        if (count > 0) result.add(new Chunk(cx, cz, Arrays.copyOf(positions, count)));
        blockCount += count;
      }
    }
    chunks = List.copyOf(result);
  }
}
