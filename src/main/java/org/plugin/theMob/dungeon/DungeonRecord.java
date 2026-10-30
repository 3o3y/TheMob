package org.plugin.theMob.dungeon;

import java.util.List;

public final class DungeonRecord {
  public enum State {
    PLANNING,
    BUILDING,
    READY,
    CANCELLED,
    INTERRUPTED,
    FAILED
  }

  public final int slot;
  public final String id;
  public final DungeonStyle style;
  public final long seed;
  public State state;
  public int levels, height, blocks, pathLength;
  public List<DungeonPlan.Room> rooms = List.of();
  public String completionBossId = "";
  public final java.util.LinkedHashSet<java.util.UUID> completedRuns =
      new java.util.LinkedHashSet<>();
  public java.util.UUID worldId;
  public String worldName = "";
  public int originX, originZ, baseY = 64;
  public AltarLayout.Pos altar;
  public int altarVersion;

  public void locate(org.bukkit.World world, int x, int y, int z) {
    worldId = world.getUID();
    worldName = world.getName();
    originX = x;
    baseY = y;
    originZ = z;
  }

  public DungeonRecord(int slot, DungeonStyle style, long seed, State state) {
    if (slot < 0 || slot >= 100_000)
      throw new IllegalArgumentException("Dungeon plot limit reached");
    this.slot = slot;
    id = String.format(java.util.Locale.ROOT, "d%05d", slot + 1);
    this.style = style;
    this.seed = seed;
    this.state = state;
  }

  public int x() {
    return originX;
  }

  public int z() {
    return originZ;
  }

  public boolean contains(int x, int z) {
    return x >= x() && x < x() + DungeonPlan.WIDTH && z >= z() && z < z() + DungeonPlan.DEPTH;
  }

  public void apply(DungeonPlan plan) {
    levels = plan.levels();
    height = plan.height();
    blocks = plan.blockCount();
    pathLength = plan.pathLength();
    rooms = plan.rooms();
    altar = plan.altar();
    altarVersion = 0;
  }
}
