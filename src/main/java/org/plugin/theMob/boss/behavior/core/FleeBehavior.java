package org.plugin.theMob.boss.behavior.core;

import org.bukkit.Location;
import org.bukkit.World;
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

public final class FleeBehavior implements BossBehavior {

    // =========================
    // TUNING
    // =========================
    private static final double FEAR_RADIUS = 12.0;
    private static final double MOVE_SPEED = 0.29;
    private static final double BASE_Y = 0.09;
    private static final double JUMP_Y = 0.46;

    private static final int STUCK_TICKS = 5;
    private static final double MIN_PROGRESS_SQ = 0.01;

    private static final int LOCAL_TP_RADIUS = 2; // 5x5x5
    private static final int TP_ATTEMPTS = 25;

    // =========================
    // STATE (PER BOSS UUID!)
    // =========================
    private final Map<UUID, Integer> stuckTicks = new HashMap<>();
    private final Map<UUID, Location> lastPos = new HashMap<>();

    @Override
    public String id() {
        return "flee";
    }

    // =====================================================
    // ENTER
    // =====================================================
    @Override
    public void onEnter(LivingEntity boss, BossPhase phase) {
        if (!(boss instanceof Mob mob)) return;

        UUID id = boss.getUniqueId();

        mob.setTarget(null);
        mob.setAI(true);
        mob.setAware(true);
        mob.setSilent(true);

        stuckTicks.put(id, 0);
        lastPos.put(id, boss.getLocation().clone());
    }

    // =====================================================
    // EXIT (HARD RESET)
    // =====================================================
    @Override
    public void onExit(LivingEntity boss, BossPhase phase) {
        if (!(boss instanceof Mob mob)) return;

        UUID id = boss.getUniqueId();

        mob.setTarget(null);
        mob.setAI(true);
        mob.setAware(true);
        mob.setSilent(false);

        stuckTicks.remove(id);
        lastPos.remove(id);
    }

    // =====================================================
    // TICK
    // =====================================================
    @Override
    public void tick(LivingEntity boss, BossPhase phase) {
        if (!(boss instanceof Mob mob)) return;

        UUID id = boss.getUniqueId();

        Player threat = findNearestPlayer(boss, FEAR_RADIUS);
        if (threat == null) return;

        Location loc = boss.getLocation();

        Vector fleeDir = loc.toVector()
                .subtract(threat.getLocation().toVector());

        if (fleeDir.lengthSquared() < 0.0001) return;
        fleeDir.normalize();

        Vector velocity = fleeDir.multiply(MOVE_SPEED);

        // =========================
        // STEP / JUMP (1–2 Blöcke)
        // =========================
        if (shouldStepUp(loc, fleeDir, resolveStepHeight(boss))) {
            velocity.setY(JUMP_Y);
        } else {
            velocity.setY(BASE_Y);
        }

        boss.setVelocity(velocity);

        // =========================
        // ROTATION – IMMER WEG SCHAUEN
        // =========================
        boss.setRotation(yawFrom(fleeDir), loc.getPitch());

        // =========================
        // STUCK DETECTION
        // =========================
        Location last = lastPos.get(id);
        int stuck = stuckTicks.getOrDefault(id, 0);

        if (last != null && loc.distanceSquared(last) < MIN_PROGRESS_SQ) {
            stuck++;
        } else {
            stuck = 0;
        }

        stuckTicks.put(id, stuck);
        lastPos.put(id, loc.clone());

        // =========================
        // LOCAL TELEPORT FALLBACK
        // =========================
        if (stuck >= STUCK_TICKS) {
            Location safe = findLocalSafeSpot(boss);
            if (safe != null) {
                boss.teleport(safe);
            }
            stuckTicks.put(id, 0);
        }
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private Player findNearestPlayer(LivingEntity boss, double radius) {
        double best = radius * radius;
        Player nearest = null;

        for (Player p : boss.getWorld().getPlayers()) {
            double d = p.getLocation().distanceSquared(boss.getLocation());
            if (d < best) {
                best = d;
                nearest = p;
            }
        }
        return nearest;
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

    private Location findLocalSafeSpot(LivingEntity boss) {
        World world = boss.getWorld();
        Location center = boss.getLocation();

        for (int i = 0; i < TP_ATTEMPTS; i++) {
            int dx = rand(-LOCAL_TP_RADIUS, LOCAL_TP_RADIUS);
            int dy = rand(-LOCAL_TP_RADIUS, LOCAL_TP_RADIUS);
            int dz = rand(-LOCAL_TP_RADIUS, LOCAL_TP_RADIUS);

            Location l = center.clone().add(dx, dy, dz);

            if (l.clone().add(0, -1, 0).getBlock().getType().isSolid()
                    && l.getBlock().getType().isAir()
                    && l.clone().add(0, 1, 0).getBlock().getType().isAir()) {
                return l;
            }
        }
        return null;
    }

    private int resolveStepHeight(LivingEntity boss) {
        AttributeInstance scale = boss.getAttribute(Attribute.SCALE);
        double s = scale != null ? scale.getValue() : 1.0;
        return s <= 1.3 ? 1 : 2;
    }

    private float yawFrom(Vector v) {
        return (float) Math.toDegrees(Math.atan2(-v.getX(), v.getZ()));
    }

    private int rand(int min, int max) {
        return min + (int) (Math.random() * (max - min + 1));
    }
}
