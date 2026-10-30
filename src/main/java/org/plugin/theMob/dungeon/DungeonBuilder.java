package org.plugin.theMob.dungeon;

import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;

/** Clears and writes one real chunk at a time, without physics or item drops. */
final class DungeonBuilder implements AutoCloseable {
  private final Plugin plugin;
  private final World world;
  private final DungeonRecord record;
  private final DungeonPlan plan;
  private final BlockData[] palette;
  private final int minCX, maxCX, minCZ, maxCZ, minY, maxY, volume;
  private final Location waiting;
  private int cx, cz, cursor, scanned;
  private Chunk held;
  private java.util.List<LivingEntity> evacuation = java.util.List.of();
  private int evacuationCursor;

  DungeonBuilder(
      Plugin plugin, World world, DungeonRecord record, DungeonPlan plan, Location waiting) {
    this.plugin = plugin;
    this.world = world;
    this.record = record;
    this.plan = plan;
    this.waiting = waiting;
    minY = record.baseY + 6;
    maxY = world.getMaxHeight();
    if (minY < world.getMinHeight() || record.baseY + plan.height() > maxY)
      throw new IllegalArgumentException(
          "Am Standort ist nicht genug Welthoehe fuer diesen Dungeon.");
    minCX = record.x() >> 4;
    maxCX = (record.x() + DungeonPlan.WIDTH - 1) >> 4;
    minCZ = record.z() >> 4;
    maxCZ = (record.z() + DungeonPlan.DEPTH - 1) >> 4;
    cx = minCX;
    cz = minCZ;
    volume = (maxCX - minCX + 1) * (maxCZ - minCZ + 1) * 256 * (maxY - minY);
    palette =
        plan.palette().stream()
            .map(org.plugin.theMob.compat.Blocks::data)
            .toArray(BlockData[]::new);
  }

  boolean tick(int maxChanges, long maxNanos) {
    if (cz > maxCZ) return true;
    long start = System.nanoTime();
    if (held == null) {
      held = world.getChunkAt(cx, cz);
      held.addPluginChunkTicket(plugin);
      evacuation =
          java.util.Arrays.stream(held.getEntities())
              .filter(LivingEntity.class::isInstance)
              .map(LivingEntity.class::cast)
              .toList();
      evacuationCursor = 0;
    }
    int evacuated = 0;
    // Relocations share the time budget and cannot turn a large mob farm into one long tick.
    while (evacuationCursor < evacuation.size()
        && evacuated < 16
        && (evacuated == 0 || System.nanoTime() - start < maxNanos)) {
      LivingEntity entity = evacuation.get(evacuationCursor++);
      evacuated++;
      if (entity.isValid()
          && record.contains(entity.getLocation().getBlockX(), entity.getLocation().getBlockZ())
          && entity.getLocation().getY() >= minY
          && !entity.teleport(waiting))
        throw new IllegalStateException(
            "Eine andere Erweiterung blockiert die Raeumung eines Wesens.");
    }
    if (evacuationCursor < evacuation.size()) return false;
    int changes = 0, visits = 0, chunkVolume = 256 * (maxY - minY);
    while (cursor < chunkVolume
        && changes < maxChanges
        && visits < maxChanges * 8
        && (visits == 0 || System.nanoTime() - start < maxNanos)) {
      int pos = cursor++, lx = pos & 15, lz = (pos >> 4) & 15, y = maxY - 1 - (pos >> 8);
      scanned++;
      visits++;
      int x = (cx << 4) + lx, z = (cz << 4) + lz;
      if (!record.contains(x, z)) continue;
      String state = plan.block(x - record.x(), y - record.baseY, z - record.z());
      var block = held.getBlock(lx, y, lz);
      if (state.equals("minecraft:air") && block.getType().isAir()) continue;
      BlockData data =
          state.equals("minecraft:air")
              ? palette[0]
              : palette[
                  plan.paletteIndex(
                      ((y - record.baseY) * DungeonPlan.DEPTH + z - record.z()) * DungeonPlan.WIDTH
                          + x
                          - record.x())];
      block.setBlockData(data, false);
      changes++;
    }
    if (cursor == chunkVolume) {
      close();
      cursor = 0;
      if (++cx > maxCX) {
        cx = minCX;
        cz++;
      }
    }
    return cz > maxCZ;
  }

  int progress() {
    return (int) (100L * scanned / Math.max(1, volume));
  }

  @Override
  public void close() {
    if (held != null) {
      held.removePluginChunkTicket(plugin);
      held = null;
      evacuation = java.util.List.of();
      evacuationCursor = 0;
    }
  }
}
