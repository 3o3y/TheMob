package org.plugin.theMob.boss.behavior.core;

import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.plugin.theMob.boss.BossPhase;
import org.plugin.theMob.boss.behavior.BossBehavior;

public final class FleeBehavior implements BossBehavior {

    // =========================
    // CONFIG (später aus cfg)
    // =========================
    private static final double FEAR_RADIUS = 10.0;
    private static final double RUN_SPEED = 0.30;

    private static final double STEP_HEIGHT = 2.5; // 🔥 wie in config
    private static final double BASE_Y = 0.08;
    private static final double JUMP_Y = 0.42;

    @Override
    public String id() {
        return "flee";
    }

    @Override
    public void onEnter(LivingEntity boss, BossPhase phase) {
        if (!(boss instanceof Mob mob)) return;

        // ✅ AI MUSS AN SEIN
        mob.setAI(true);
        mob.setAware(false);
        mob.setTarget(null);

        // sofort stoppen, nächste Tick läuft los
        boss.setVelocity(new Vector(0, 0, 0));
    }

    @Override
    public void tick(LivingEntity boss, BossPhase phase) {
        if (!(boss instanceof Mob mob)) return;

        mob.setTarget(null);

        Player player = nearestPlayer(boss);
        if (player == null) return;

        Location loc = boss.getLocation();

        // Abstand check
        if (loc.distanceSquared(player.getLocation()) >= FEAR_RADIUS * FEAR_RADIUS) {
            boss.setVelocity(new Vector(0, 0, 0));
            return;
        }

        // 🔥 DIREKT VOM SPIELER WEG
        Vector fleeDir = loc.toVector()
                .subtract(player.getLocation().toVector())
                .normalize();

        Vector velocity = fleeDir.multiply(RUN_SPEED);

        // Step / Jump Logik
        velocity.setY(
                shouldStepUp(loc, fleeDir, STEP_HEIGHT)
                        ? JUMP_Y
                        : BASE_Y
        );

        boss.setVelocity(velocity);

        // 🔄 sofort umdrehen
        boss.setRotation(
                (float) Math.toDegrees(Math.atan2(-fleeDir.getX(), fleeDir.getZ())),
                loc.getPitch()
        );
    }

    // =========================
    // HELPERS
    // =========================

    private Player nearestPlayer(LivingEntity boss) {
        Player best = null;
        double bestD = FEAR_RADIUS * FEAR_RADIUS;

        for (Player p : boss.getWorld().getPlayers()) {
            double d = p.getLocation().distanceSquared(boss.getLocation());
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    /**
     * Step-Logic mit FLOAT step-height (z. B. 2.5)
     */
    private boolean shouldStepUp(Location from, Vector dir, double maxStep) {
        int steps = (int) Math.ceil(maxStep * 2); // 0.5 Block Schritte

        for (int i = 1; i <= steps; i++) {
            double y = i * 0.5;

            Location base = from.clone().add(dir).add(0, y, 0);

            if (base.clone().add(0, -0.5, 0).getBlock().getType().isSolid()
                    && base.getBlock().getType().isAir()
                    && base.clone().add(0, 1, 0).getBlock().getType().isAir()) {
                return true;
            }
        }
        return false;
    }
}
