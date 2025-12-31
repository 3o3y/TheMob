package org.plugin.theMob.hud;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.plugin.theMob.mob.MobManager;

public final class MobRadarResolver {

    private final MobManager mobs;
    private final boolean enabled;

    private final double radius;
    private final double radiusSq;

    private final boolean ignoreBats;
    private final boolean ignoreVillagers;
    private final boolean ignoreNamed;

    public MobRadarResolver(
            MobManager mobs,
            boolean enabled,
            double radius,
            boolean ignoreBats,
            boolean ignoreVillagers,
            boolean ignoreNamed
    ) {
        this.mobs = mobs;
        this.enabled = enabled;
        this.radius = radius;
        this.radiusSq = radius * radius;
        this.ignoreBats = ignoreBats;
        this.ignoreVillagers = ignoreVillagers;
        this.ignoreNamed = ignoreNamed;
    }

    public double getRadius() {
        return radius;
    }

    public enum RadarType {
        PLUGIN(1),
        AGGRESSIVE(2),
        NEUTRAL(3),
        PASSIVE(4);

        public final int prio;
        RadarType(int prio) { this.prio = prio; }
    }

    public record RadarTarget(
            LivingEntity entity,
            RadarType type,
            double distance
    ) {}

    public RadarTarget find(Player p) {

        if (!enabled) return null;

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

                    if (ignoreNamed && le.getCustomName() != null) continue;
                    if (ignoreVillagers && le instanceof Villager) continue;
                    if (ignoreBats && le instanceof Bat) continue;

                    double dSq = le.getLocation().distanceSquared(pl);
                    if (dSq > radiusSq) continue;

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

    private RadarTarget classify(LivingEntity le, double dist) {

        if (mobs.isCustomMob(le)) {
            return new RadarTarget(le, RadarType.PLUGIN, dist);
        }

        if (!(le instanceof Mob mob)) return null;

        if (mob instanceof Monster || mob instanceof Slime || mob instanceof Phantom)
            return new RadarTarget(le, RadarType.AGGRESSIVE, dist);

        if (mob instanceof Enderman || mob instanceof Bee || mob instanceof IronGolem)
            return new RadarTarget(le, RadarType.NEUTRAL, dist);

        if (mob instanceof Animals)
            return new RadarTarget(le, RadarType.PASSIVE, dist);

        return null;
    }
}
