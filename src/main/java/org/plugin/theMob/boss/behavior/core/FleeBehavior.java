package org.plugin.theMob.boss.behavior.core;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.plugin.theMob.boss.BossPhase;
import org.plugin.theMob.boss.behavior.BossBehavior;

public final class FleeBehavior implements BossBehavior {

    private static final double RANGE_SQ = 20.0 * 20.0;
    private static final double SPEED = 0.4;

    @Override
    public String id() {
        return "flee";
    }

    @Override
    public void tick(LivingEntity boss, BossPhase phase) {
        if (boss == null || phase == null || !boss.isValid() || boss.isDead()) return;

        Player nearest = null;
        double best = RANGE_SQ;

        var bossLoc = boss.getLocation();

        for (Player p : boss.getWorld().getPlayers()) {
            if (!p.isOnline() || p.isDead()) continue;

            double d = p.getLocation().distanceSquared(bossLoc);
            if (d < best) {
                best = d;
                nearest = p;
            }
        }

        if (nearest == null) return;

        Vector flee = bossLoc.toVector()
                .subtract(nearest.getLocation().toVector());

        if (flee.lengthSquared() < 0.001) return;

        boss.setVelocity(
                flee.normalize().multiply(SPEED)
        );
    }
}
