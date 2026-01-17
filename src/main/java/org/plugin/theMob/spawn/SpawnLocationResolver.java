package org.plugin.theMob.spawn;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.util.Vector;

public final class SpawnLocationResolver {

    private SpawnLocationResolver() {}

    /**
     * Resolves a safe spawn location around a base location.
     *
     * Supports config:
     *  spawn:
     *    offset:
     *      x: 0.0
     *      y: 0.6
     *      z: 0.0
     *    snap-to-ground: true
     *    scan-down: 8
     *    scan-up: 6
     *
     * If config is missing, safe defaults apply.
     */
    public static Location resolveSafe(FileConfiguration mainCfg, Location base, Vector additionalOffset) {
        if (base == null || base.getWorld() == null) return base;

        final World w = base.getWorld();
        final FileConfiguration cfg = mainCfg;

        double cfgX = get(cfg, "spawn.offset.x", 0.0);
        double cfgY = get(cfg, "spawn.offset.y", 0.6);
        double cfgZ = get(cfg, "spawn.offset.z", 0.0);

        boolean snap = getBool(cfg, "spawn.snap-to-ground", true);
        int scanDown = (int) get(cfg, "spawn.scan-down", 8);
        int scanUp = (int) get(cfg, "spawn.scan-up", 6);

        Vector off = new Vector(cfgX, cfgY, cfgZ);
        if (additionalOffset != null) off.add(additionalOffset);

        Location loc = base.clone().add(off);

        // --- If we are inside blocks, go up until free (bounded)
        loc = nudgeUpToAir(loc, scanUp);

        // --- Snap to ground (recommended for large mobs)
        if (snap) {
            Location snapped = snapToGround(loc, scanDown, scanUp);
            if (snapped != null) loc = snapped;
        }

        // Final: still ensure 2-high air space (safe for most mobs)
        loc = ensureTwoHighAir(loc, scanUp);

        return loc;
    }

    private static Location snapToGround(Location start, int scanDown, int scanUp) {
        World w = start.getWorld();
        if (w == null) return null;

        int x = start.getBlockX();
        int z = start.getBlockZ();
        int y0 = start.getBlockY();

        // Search down for a solid floor with enough headroom above
        for (int dy = 0; dy <= scanDown; dy++) {
            int y = y0 - dy;
            if (y <= w.getMinHeight() + 1) break;

            Block floor = w.getBlockAt(x, y - 1, z);
            Block feet = w.getBlockAt(x, y, z);
            Block head = w.getBlockAt(x, y + 1, z);

            if (!isSolidFloor(floor)) continue;
            if (!isPassable(feet) || !isPassable(head)) continue;

            return new Location(w, x + 0.5, y + 0.01, z + 0.5, start.getYaw(), start.getPitch());
        }

        // If we didn't find ground below, try going up a bit (for caves/slabs weirdness)
        Location up = start.clone();
        for (int i = 0; i <= scanUp; i++) {
            Block feet = w.getBlockAt(up.getBlockX(), up.getBlockY(), up.getBlockZ());
            Block head = w.getBlockAt(up.getBlockX(), up.getBlockY() + 1, up.getBlockZ());
            Block floor = w.getBlockAt(up.getBlockX(), up.getBlockY() - 1, up.getBlockZ());

            if (isSolidFloor(floor) && isPassable(feet) && isPassable(head)) {
                return new Location(w, up.getBlockX() + 0.5, up.getBlockY() + 0.01, up.getBlockZ() + 0.5, start.getYaw(), start.getPitch());
            }
            up.add(0, 1, 0);
        }

        return null;
    }

    private static Location nudgeUpToAir(Location loc, int scanUp) {
        World w = loc.getWorld();
        if (w == null) return loc;

        Location t = loc.clone();
        for (int i = 0; i <= scanUp; i++) {
            Block feet = w.getBlockAt(t.getBlockX(), t.getBlockY(), t.getBlockZ());
            Block head = w.getBlockAt(t.getBlockX(), t.getBlockY() + 1, t.getBlockZ());
            if (isPassable(feet) && isPassable(head)) return t;
            t.add(0, 1, 0);
        }
        return loc;
    }

    private static Location ensureTwoHighAir(Location loc, int scanUp) {
        World w = loc.getWorld();
        if (w == null) return loc;

        Location t = loc.clone();
        for (int i = 0; i <= scanUp; i++) {
            Block feet = w.getBlockAt(t.getBlockX(), t.getBlockY(), t.getBlockZ());
            Block head = w.getBlockAt(t.getBlockX(), t.getBlockY() + 1, t.getBlockZ());
            if (isPassable(feet) && isPassable(head)) return t;
            t.add(0, 1, 0);
        }
        return loc;
    }

    private static boolean isPassable(Block b) {
        if (b == null) return true;
        Material m = b.getType();
        return m.isAir() || m.isTransparent() || b.isPassable();
    }

    private static boolean isSolidFloor(Block b) {
        if (b == null) return false;
        Material m = b.getType();
        // Solid, not air, not liquids
        if (m.isAir()) return false;
        if (m == Material.WATER || m == Material.LAVA) return false;
        return m.isSolid();
    }

    private static double get(FileConfiguration cfg, String path, double def) {
        if (cfg == null) return def;
        return cfg.getDouble(path, def);
    }

    private static boolean getBool(FileConfiguration cfg, String path, boolean def) {
        if (cfg == null) return def;
        return cfg.getBoolean(path, def);
    }
}
