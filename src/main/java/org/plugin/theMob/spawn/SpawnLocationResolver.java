package org.plugin.theMob.spawn;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.util.Vector;

public final class SpawnLocationResolver {
  private SpawnLocationResolver() {}

  public static Location resolveSafe(
      FileConfiguration mainCfg, Location base, Vector additionalOffset) {
    if (base != null && base.getWorld() != null) {
      World w = base.getWorld();
      double cfgX = get(mainCfg, "spawn.offset.x", 0.0);
      double cfgY = get(mainCfg, "spawn.offset.y", 0.6);
      double cfgZ = get(mainCfg, "spawn.offset.z", 0.0);
      boolean snap = getBool(mainCfg, "spawn.snap-to-ground", true);
      int scanDown = (int) get(mainCfg, "spawn.scan-down", 8.0);
      int scanUp = (int) get(mainCfg, "spawn.scan-up", 6.0);
      Vector off = new Vector(cfgX, cfgY, cfgZ);
      if (additionalOffset != null) {
        off.add(additionalOffset);
      }

      Location loc = base.clone().add(off);
      loc = nudgeUpToAir(loc, scanUp);
      if (snap) {
        Location snapped = snapToGround(loc, scanDown, scanUp);
        if (snapped != null) {
          loc = snapped;
        }
      }

      return ensureTwoHighAir(loc, scanUp);
    } else {
      return base;
    }
  }

  private static Location snapToGround(Location start, int scanDown, int scanUp) {
    World w = start.getWorld();
    if (w == null) {
      return null;
    } else {
      int x = start.getBlockX();
      int z = start.getBlockZ();
      int y0 = start.getBlockY();

      for (int dy = 0; dy <= scanDown; dy++) {
        int y = y0 - dy;
        if (y <= w.getMinHeight() + 1) {
          break;
        }

        Block floor = w.getBlockAt(x, y - 1, z);
        Block feet = w.getBlockAt(x, y, z);
        Block head = w.getBlockAt(x, y + 1, z);
        if (isSolidFloor(floor) && isPassable(feet) && isPassable(head)) {
          return new Location(w, x + 0.5, y + 0.01, z + 0.5, start.getYaw(), start.getPitch());
        }
      }

      Location up = start.clone();

      for (int i = 0; i <= scanUp; i++) {
        Block feet = w.getBlockAt(up.getBlockX(), up.getBlockY(), up.getBlockZ());
        Block head = w.getBlockAt(up.getBlockX(), up.getBlockY() + 1, up.getBlockZ());
        Block floor = w.getBlockAt(up.getBlockX(), up.getBlockY() - 1, up.getBlockZ());
        if (isSolidFloor(floor) && isPassable(feet) && isPassable(head)) {
          return new Location(
              w,
              up.getBlockX() + 0.5,
              up.getBlockY() + 0.01,
              up.getBlockZ() + 0.5,
              start.getYaw(),
              start.getPitch());
        }

        up.add(0.0, 1.0, 0.0);
      }

      return null;
    }
  }

  private static Location nudgeUpToAir(Location loc, int scanUp) {
    World w = loc.getWorld();
    if (w == null) {
      return loc;
    } else {
      Location t = loc.clone();

      for (int i = 0; i <= scanUp; i++) {
        Block feet = w.getBlockAt(t.getBlockX(), t.getBlockY(), t.getBlockZ());
        Block head = w.getBlockAt(t.getBlockX(), t.getBlockY() + 1, t.getBlockZ());
        if (isPassable(feet) && isPassable(head)) {
          return t;
        }

        t.add(0.0, 1.0, 0.0);
      }

      return loc;
    }
  }

  private static Location ensureTwoHighAir(Location loc, int scanUp) {
    World w = loc.getWorld();
    if (w == null) {
      return loc;
    } else {
      Location t = loc.clone();

      for (int i = 0; i <= scanUp; i++) {
        Block feet = w.getBlockAt(t.getBlockX(), t.getBlockY(), t.getBlockZ());
        Block head = w.getBlockAt(t.getBlockX(), t.getBlockY() + 1, t.getBlockZ());
        if (isPassable(feet) && isPassable(head)) {
          return t;
        }

        t.add(0.0, 1.0, 0.0);
      }

      return loc;
    }
  }

  private static boolean isPassable(Block b) {
    if (b == null) {
      return true;
    } else {
      Material m = b.getType();
      return m.isAir() || b.isPassable();
    }
  }

  private static boolean isSolidFloor(Block b) {
    if (b == null) {
      return false;
    } else {
      Material m = b.getType();
      if (m.isAir()) {
        return false;
      } else {
        return m != Material.WATER && m != Material.LAVA ? m.isSolid() : false;
      }
    }
  }

  private static double get(FileConfiguration cfg, String path, double def) {
    return cfg == null ? def : cfg.getDouble(path, def);
  }

  private static boolean getBool(FileConfiguration cfg, String path, boolean def) {
    return cfg == null ? def : cfg.getBoolean(path, def);
  }
}
