package org.plugin.theMob.mob.ability;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class StuckDefensePath {

    // =====================================================
    // TUNING
    // =====================================================

    private static final int CHECK_EVERY_TICKS = 20;     // 1 Sekunde
    private static final int STUCK_AFTER_TICKS = 60;     // 3 Sekunden
    private static final double MIN_PROGRESS = 0.6;

    private static final int SEARCH_RADIUS = 2;          // 5x5x5
    private static final int MAX_TRIES = 5;

    private static final int BREAK_COOLDOWN_TICKS = 20;

    // =====================================================
    // STATE
    // =====================================================

    private final Map<UUID, State> states = new ConcurrentHashMap<>();
    private long globalTick = 0;

    // =====================================================
    // PUBLIC API
    // =====================================================

    public void tick(LivingEntity mob) {
        if (mob == null || !mob.isValid() || mob.isDead()) {
            if (mob != null) states.remove(mob.getUniqueId());
            return;
        }

        globalTick++;
        if (globalTick % CHECK_EVERY_TICKS != 0) return;

        Location now = mob.getLocation();
        UUID id = mob.getUniqueId();

        State s = states.computeIfAbsent(id, k -> new State(now));

        double moved = now.distance(s.lastLocation);

        // =========================
        // Fortschritt
        // =========================
        if (moved >= MIN_PROGRESS) {
            s.lastLocation = now;
            s.stuckTicks = 0;
            return;
        }

        s.stuckTicks += CHECK_EVERY_TICKS;
        if (s.stuckTicks < STUCK_AFTER_TICKS) return;

        // =========================
        // STUCK → Gegenmaßnahmen
        // =========================
        s.stuckTicks = 0;

        // 1️⃣ Soft-Block entfernen
        if (globalTick >= s.nextBreakTick) {
            if (tryClearSoftObstacle(mob)) {
                s.nextBreakTick = globalTick + BREAK_COOLDOWN_TICKS;
                return;
            }
        }

        // 2️⃣ Teleport-Fallback
        attemptTeleport(mob, now);

        s.lastLocation = mob.getLocation();
    }

    public void remove(LivingEntity mob) {
        if (mob != null) states.remove(mob.getUniqueId());
    }

    public void clear() {
        states.clear();
    }

    // =====================================================
    // BLOCK BREAKING (RESPECTS MOBGRIEFING)
    // =====================================================

    private boolean tryClearSoftObstacle(LivingEntity mob) {
        if (!(mob instanceof Mob m)) return false;

        World world = mob.getWorld();
        Boolean griefing = world.getGameRuleValue(GameRule.MOB_GRIEFING);
        if (griefing == null || !griefing) return false;

        Location eye = mob.getLocation().add(0, 1.0, 0);
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
        World w = block.getWorld();
        Location loc = block.getLocation();

        w.playEffect(loc, Effect.STEP_SOUND, mat);

        for (ItemStack drop : block.getDrops()) {
            w.dropItemNaturally(loc, drop);
        }

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
    // TELEPORT LOGIC
    // =====================================================

    private void attemptTeleport(LivingEntity mob, Location from) {
        World world = from.getWorld();
        if (world == null) return;

        Player target = null;
        if (mob instanceof Mob m && m.getTarget() instanceof Player p) {
            if (mob.hasLineOfSight(p)) target = p;
        }

        Vector baseDir = target != null
                ? target.getLocation().toVector().subtract(from.toVector()).normalize()
                : randomDir();

        Location base = from.clone().add(baseDir.multiply(2));

        for (int i = 0; i < MAX_TRIES; i++) {
            Location test = base.clone().add(
                    rand(-SEARCH_RADIUS, SEARCH_RADIUS),
                    rand(-1, 1),
                    rand(-SEARCH_RADIUS, SEARCH_RADIUS)
            );

            if (isSafe(test)) {
                spawnParticles(from);
                mob.teleport(test);
                spawnParticles(test);
                return;
            }
        }
    }

    private Vector randomDir() {
        double x = Math.random() - 0.5;
        double z = Math.random() - 0.5;
        return new Vector(x, 0, z).normalize();
    }

    private int rand(int min, int max) {
        return min + (int) (Math.random() * (max - min + 1));
    }

    // =====================================================
    // SAFETY CHECK
    // =====================================================

    private boolean isSafe(Location loc) {
        Location feet = loc.clone();
        Location head = loc.clone().add(0, 1, 0);
        Location ground = loc.clone().add(0, -1, 0);

        return ground.getBlock().getType().isSolid()
                && feet.getBlock().getType().isAir()
                && head.getBlock().getType().isAir();
    }

    // =====================================================
    // PARTICLES
    // =====================================================

    private void spawnParticles(Location loc) {
        World w = loc.getWorld();
        if (w == null) return;

        w.spawnParticle(
                Particle.CLOUD,
                loc.clone().add(0, 1, 0),
                30,
                0.4, 0.6, 0.4,
                0.01
        );
    }

    // =====================================================
    // STATE
    // =====================================================

    private static final class State {
        Location lastLocation;
        int stuckTicks;
        long nextBreakTick;

        State(Location loc) {
            this.lastLocation = loc;
        }
    }
}
