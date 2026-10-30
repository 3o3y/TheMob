package org.plugin.theMob.spawn;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

public final class SpawnUtil {
  private SpawnUtil() {}

  public static Location resolveSafeSpawn(Location base) {
    return resolveSafeSpawn(base, 0.0, 0.6, 0.0);
  }

  public static Location resolveSafeSpawn(Location base, double ox, double oy, double oz) {
    if (base != null && base.getWorld() != null) {
      Location loc = base.clone().add(ox, oy, oz);
      loc = pushUpToAir(loc, 6);
      Location snapped = snapToGround(loc, 8);
      if (snapped != null) {
        loc = snapped;
      }

      return ensureTwoHighAir(loc, 6);
    } else {
      return base;
    }
  }

  private static Location snapToGround(Location start, int maxDown) {
    World w = start.getWorld();
    int x = start.getBlockX();
    int z = start.getBlockZ();
    int y = start.getBlockY();

    for (int i = 0; i <= maxDown; i++) {
      int cy = y - i;
      if (cy <= w.getMinHeight() + 1) {
        break;
      }

      Block floor = w.getBlockAt(x, cy - 1, z);
      Block feet = w.getBlockAt(x, cy, z);
      Block head = w.getBlockAt(x, cy + 1, z);
      if (isSolid(floor) && isPassable(feet) && isPassable(head)) {
        return new Location(w, x + 0.5, cy + 0.01, z + 0.5, start.getYaw(), start.getPitch());
      }
    }

    return null;
  }

  private static Location pushUpToAir(Location loc, int maxUp) {
    World w = loc.getWorld();
    Location t = loc.clone();

    for (int i = 0; i <= maxUp; i++) {
      Block feet = w.getBlockAt(t.getBlockX(), t.getBlockY(), t.getBlockZ());
      Block head = w.getBlockAt(t.getBlockX(), t.getBlockY() + 1, t.getBlockZ());
      if (isPassable(feet) && isPassable(head)) {
        return t;
      }

      t.add(0.0, 1.0, 0.0);
    }

    return loc;
  }

  private static Location ensureTwoHighAir(Location loc, int maxUp) {
    World w = loc.getWorld();
    Location t = loc.clone();

    for (int i = 0; i <= maxUp; i++) {
      if (isPassable(w.getBlockAt(t.getBlockX(), t.getBlockY(), t.getBlockZ()))
          && isPassable(w.getBlockAt(t.getBlockX(), t.getBlockY() + 1, t.getBlockZ()))) {
        return t;
      }

      t.add(0.0, 1.0, 0.0);
    }

    return loc;
  }

  private static boolean isPassable(Block b) {
    if (b == null) {
      return true;
    } else {
      Material m = b.getType();
      return m.isAir() || b.isPassable();
    }
  }

  private static boolean isSolid(Block b) {
    if (b == null) {
      return false;
    } else {
      Material m = b.getType();
      return m.isSolid() && m != Material.WATER && m != Material.LAVA;
    }
  }
}
