package org.plugin.theMob.boss.behavior.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.plugin.theMob.boss.BossPhase;
import org.plugin.theMob.boss.behavior.BossBehavior;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PassiveBehavior implements BossBehavior {

    // =========================
    // TUNING
    // =========================
    private static final double MOVE_SPEED = 0.12;
    private static final double BASE_Y = 0.04;
    private static final double JUMP_Y = 0.30;

    private static final int IDLE_MIN = 40;
    private static final int IDLE_MAX = 120;

    private static final int WALK_MIN = 20;
    private static final int WALK_MAX = 50;

    private static final double LOOK_RADIUS = 12.0;

    // =========================

    private final Map<UUID, Boolean> walking = new HashMap<>();
    private final Map<UUID, Integer> stateTicks = new HashMap<>();
    private final Map<UUID, Vector> walkDir = new HashMap<>();

    @Override
    public String id() {
        return "passive";
    }

    @Override
    public void onEnter(LivingEntity boss, BossPhase phase) {
        if (!(boss instanceof Mob mob)) return;

        UUID id = boss.getUniqueId();

        mob.setAI(false);
        mob.setAware(false);
        mob.setTarget(null);

        walking.put(id, false);
        stateTicks.put(id, rand(IDLE_MIN, IDLE_MAX));
        walkDir.put(id, randomDir());

        boss.setVelocity(new Vector(0, 0, 0));
    }

    @Override
    public void onExit(LivingEntity boss, BossPhase phase) {
        UUID id = boss.getUniqueId();
        walking.remove(id);
        stateTicks.remove(id);
        walkDir.remove(id);
    }

    @Override
    public void tick(LivingEntity boss, BossPhase phase) {
        if (!(boss instanceof Mob mob)) return;

        // 🔒 Absolut kein Target, egal was passiert
        mob.setTarget(null);

        UUID id = boss.getUniqueId();

        // 👀 Spieler anschauen (nur Rotation)
        lookAtNearestPlayer(boss);

        int t = stateTicks.getOrDefault(id, 0) - 1;
        boolean isWalking = walking.getOrDefault(id, false);

        if (t <= 0) {
            isWalking = !isWalking;
            walking.put(id, isWalking);

            stateTicks.put(
                    id,
                    isWalking
                            ? rand(WALK_MIN, WALK_MAX)
                            : rand(IDLE_MIN, IDLE_MAX)
            );

            walkDir.put(id, randomDir());

            if (!isWalking) {
                boss.setVelocity(new Vector(0, 0, 0));
            }
        } else {
            stateTicks.put(id, t);
        }

        if (isWalking) {
            Vector dir = walkDir.get(id);
            Vector vel = dir.clone().multiply(MOVE_SPEED);

            vel.setY(
                    shouldStepUp(boss.getLocation(), dir, resolveStepHeight(boss))
                            ? JUMP_Y
                            : BASE_Y
            );

            boss.setVelocity(vel);
        }
    }

    // =========================
    // LOOK LOGIC
    // =========================

    private void lookAtNearestPlayer(LivingEntity boss) {
        Player nearest = null;
        double best = LOOK_RADIUS * LOOK_RADIUS;

        for (Player p : boss.getWorld().getPlayers()) {
            double d = p.getLocation().distanceSquared(boss.getLocation());
            if (d < best) {
                best = d;
                nearest = p;
            }
        }

        if (nearest == null) return;

        Location loc = boss.getLocation();
        Location target = nearest.getLocation();

        Vector dir = target.toVector().subtract(loc.toVector());
        loc.setYaw((float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ())));
        boss.teleport(loc);
    }

    // =========================
    // HELPERS
    // =========================

    private Vector randomDir() {
        double a = Math.random() * Math.PI * 2;
        return new Vector(Math.cos(a), 0, Math.sin(a)).normalize();
    }

    private boolean shouldStepUp(Location from, Vector dir, int maxStep) {
        for (int y = 1; y <= maxStep; y++) {
            Location base = from.clone().add(dir).add(0, y, 0);
            if (base.clone().add(0, -1, 0).getBlock().getType().isSolid()
                    && base.getBlock().getType().isAir()
                    && base.clone().add(0, 1, 0).getBlock().getType().isAir()) {
                return true;
            }
        }
        return false;
    }

    private int resolveStepHeight(LivingEntity boss) {
        AttributeInstance scale = boss.getAttribute(Attribute.SCALE);
        double s = scale != null ? scale.getValue() : 1.0;
        return s <= 1.3 ? 1 : 2;
    }

    private int rand(int min, int max) {
        return min + (int) (Math.random() * (max - min + 1));
    }
}
