package org.plugin.theMob.mob.ai;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SmartRepathService {

    private static final int CHECK_EVERY = 20;        // 1s
    private static final int STUCK_AFTER = 60;        // 3s
    private static final double MIN_PROGRESS = 0.6;

    private static final int BREAK_COOLDOWN = 20;     // 1s

    private static final int TELEPORT_RADIUS = 2;
    private static final int TELEPORT_TRIES = 24;

    private static final int PARTICLE_COUNT = 128;
    private static final Particle.DustOptions SKY_BLUE =
            new Particle.DustOptions(Color.fromRGB(120, 180, 255), 1.6f);

    private final Map<UUID, State> states = new ConcurrentHashMap<>();
    private final Random rnd = new Random();
    private long tick;

    // =====================================================
    // MAIN TICK
    // =====================================================

    public void tick(Mob mob) {
        if (mob == null || !mob.isValid() || mob.isDead()) {
            if (mob != null) states.remove(mob.getUniqueId());
            return;
        }

        tick++;
        if (tick % CHECK_EVERY != 0) return;

        State s = states.computeIfAbsent(
                mob.getUniqueId(),
                id -> new State(mob.getLocation())
        );

        Location now = mob.getLocation();
        double progress = now.distance(s.lastProgress);

        if (progress < MIN_PROGRESS) {
            s.stuckTicks += CHECK_EVERY;
        } else {
            s.stuckTicks = 0;
            s.lastProgress = now;
        }

        if (s.stuckTicks >= STUCK_AFTER) {
            s.stuckTicks = 0;
            handleStuck(mob, s);
        }
    }

    // =====================================================
    // STUCK HANDLING
    // =====================================================

    private void handleStuck(Mob mob, State s) {

        // 1️⃣ Block abbauen (nur wenn mobGriefing erlaubt)
        if (tick >= s.nextBreakTick) {
            if (tryClearSoftObstacle(mob)) {
                s.nextBreakTick = tick + BREAK_COOLDOWN;
                return;
            }
        }

        // 2️⃣ Teleport fallback
        if (teleportNearTarget(mob)) return;

        // 3️⃣ Soft repath
        softRepath(mob);
    }

    // =====================================================
    // BLOCK BREAKING (RESPECTS MOBGRIEFING)
    // =====================================================

    private boolean tryClearSoftObstacle(Mob mob) {
        World world = mob.getWorld();

        Boolean griefing = world.getGameRuleValue(GameRule.MOB_GRIEFING);
        if (griefing == null || !griefing) {
            return false; // exakt wie Vanilla / WorldEdit
        }

        Location eye = mob.getLocation().clone().add(0, 1.0, 0);
        Vector dir = eye.getDirection().normalize();

        for (int i = 1; i <= 2; i++) {
            Block b = eye.clone().add(dir.clone().multiply(i)).getBlock();
            if (isSoftObstacle(b.getType())) {
                breakBlockInstant(b);
                return true;
            }
        }
        return false;
    }

    private void breakBlockInstant(Block block) {
        Material mat = block.getType();
        Location loc = block.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        // Break animation
        world.playEffect(loc, Effect.STEP_SOUND, mat);

        // Drops
        for (ItemStack drop : block.getDrops()) {
            world.dropItemNaturally(loc, drop);
        }

        // Remove block
        block.setType(Material.AIR, false);
    }

    private boolean isSoftObstacle(Material mat) {
        String n = mat.name();
        if (n.startsWith("IRON_")) return false;

        return n.endsWith("_LEAVES")
                || n.endsWith("_LOG")
                || n.endsWith("_WOOD")
                || n.endsWith("_DOOR")
                || n.endsWith("_TRAPDOOR")
                || mat == Material.VINE;
    }

    // =====================================================
    // SOFT REPATH
    // =====================================================

    private boolean softRepath(Mob mob) {
        try {
            Object pf = mob.getClass().getMethod("getPathfinder").invoke(mob);
            if (pf == null) return false;

            Location goal = computeGoalNearTarget(mob);
            if (goal == null) return false;

            pf.getClass()
                    .getMethod("moveTo", Location.class)
                    .invoke(pf, goal);

            return true;

        } catch (Throwable ignored) {}
        return false;
    }

    private Location computeGoalNearTarget(Mob mob) {
        if (mob.getTarget() == null) return null;

        Location from = mob.getLocation();
        Vector dir = mob.getTarget().getLocation()
                .toVector()
                .subtract(from.toVector())
                .normalize();

        Location base = from.clone().add(dir.multiply(2.0));
        base.setY(from.getY());

        return isPassableColumn(base) ? base : null;
    }

    private boolean isPassableColumn(Location loc) {
        return loc.getBlock().isPassable()
                && loc.clone().add(0,1,0).getBlock().isPassable()
                && loc.clone().add(0,2,0).getBlock().isPassable();
    }

    // =====================================================
    // TELEPORT
    // =====================================================

    private boolean teleportNearTarget(Mob mob) {
        if (!(mob.getTarget() instanceof Player target)) return false;
        if (!mob.hasLineOfSight(target)) return false;

        Location from = mob.getLocation();
        Location to = findTeleportSpot(from, target);
        if (to == null) return false;

        spawnParticles(from);
        mob.teleport(to);
        spawnParticles(to);
        return true;
    }

    private Location findTeleportSpot(Location from, Player target) {
        Vector dir = target.getLocation().toVector()
                .subtract(from.toVector())
                .normalize();

        Location base = from.clone().add(dir.multiply(2.0));

        for (int i = 0; i < TELEPORT_TRIES; i++) {
            Location test = base.clone().add(
                    rnd.nextInt(5) - 2,
                    rnd.nextInt(3) - 1,
                    rnd.nextInt(5) - 2
            );
            if (isPassableColumn(test)) return test;
        }
        return null;
    }

    // =====================================================
    // PARTICLES
    // =====================================================

    private void spawnParticles(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;

        world.spawnParticle(
                Particle.DUST,
                loc.clone().add(0,1,0),
                PARTICLE_COUNT,
                0.6, 0.9, 0.6,
                0.0,
                SKY_BLUE
        );
    }

    // =====================================================
    // STATE
    // =====================================================

    private static final class State {
        Location lastProgress;
        int stuckTicks;
        long nextBreakTick;

        State(Location start) {
            this.lastProgress = start;
        }
    }
}
