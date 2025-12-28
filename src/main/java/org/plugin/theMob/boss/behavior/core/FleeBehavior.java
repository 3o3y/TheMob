package org.plugin.theMob.boss.behavior.core;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.plugin.theMob.boss.BossPhase;
import org.plugin.theMob.boss.behavior.BossBehavior;

public final class FleeBehavior implements BossBehavior {

    @Override
    public String id() {
        return "flee";
    }

    @Override
    public void tick(LivingEntity boss, BossPhase phase) {
        Player nearest = null;
        double best = 20 * 20;

        for (Player p : boss.getWorld().getPlayers()) {
            double d = p.getLocation().distanceSquared(boss.getLocation());
            if (d < best) {
                best = d;
                nearest = p;
            }
        }

        if (nearest == null) return;

        boss.setVelocity(
                boss.getLocation().toVector()
                        .subtract(nearest.getLocation().toVector())
                        .normalize()
                        .multiply(0.4)
        );
    }
}
