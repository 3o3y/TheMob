package org.plugin.theMob.hud;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.plugin.theMob.mob.MobManager;

public final class MobRadarResolver {

    public static final double RADIUS = 20.0;
    private static final double RADIUS_SQ = RADIUS * RADIUS;
    private final MobManager mobs;
    public MobRadarResolver(MobManager mobs) {
        this.mobs = mobs;
    }
// RADAR TYPES (PRIORITY ORDER)
    public enum RadarType {
        PLUGIN(1),
        AGGRESSIVE(2),
        NEUTRAL(3),
        PASSIVE(4);
        public final int prio;
        RadarType(int prio) {
            this.prio = prio;
        }
    }
// RADAR RESULT
    public record RadarTarget(
            LivingEntity entity,
            RadarType type,
            double distance
    ) {}
// MAIN SCAN
    public RadarTarget find(Player p) {

        World w = p.getWorld();
        Location pl = p.getLocation();
        int cx = pl.getBlockX() >> 4;
        int cz = pl.getBlockZ() >> 4;
        RadarTarget best = null;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Chunk c = w.getChunkAt(cx + dx, cz + dz);
                if (!c.isLoaded()) continue;
                for (Entity e : c.getEntities()) {
                    if (!(e instanceof LivingEntity le)) continue;
                    double dSq = le.getLocation().distanceSquared(pl);
                    if (dSq > RADIUS_SQ) continue;
                    RadarTarget rt = classify(le, Math.sqrt(dSq));
                    if (rt == null) continue;
                    if (best == null
                            || rt.type().prio < best.type().prio
                            || (rt.type() == best.type() && rt.distance() < best.distance())) {
                        best = rt;
                    }
                }
            }
        }
        return best;
    }
// CLASSIFICATION
    private RadarTarget classify(LivingEntity le, double dist) {
        if (mobs.isCustomMob(le)) {
            return new RadarTarget(le, RadarType.PLUGIN, dist);
        }
        if (!(le instanceof Mob mob)) return null;
        if (mob instanceof Monster
                || mob instanceof Slime
                || mob instanceof Phantom) {
            return new RadarTarget(le, RadarType.AGGRESSIVE, dist);
        }
        if (mob instanceof Enderman
                || mob instanceof Bee
                || mob instanceof IronGolem
                || mob instanceof Bat) {
            return new RadarTarget(le, RadarType.NEUTRAL, dist);
        }
        if (mob instanceof Animals) {
            return new RadarTarget(le, RadarType.PASSIVE, dist);
        }

        return null;
    }
}
