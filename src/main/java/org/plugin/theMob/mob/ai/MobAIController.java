package org.plugin.theMob.mob.ai;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

public final class MobAIController {

    private final Mob mob;
    private final MobAIProfile profile;

    private BehaviorState state = BehaviorState.IDLE;
    private Player currentTarget;

    private long lastSwitchTick;
    private long fleeUntil;
    private long disengageUntil;
    private long disengageGraceUntil;

    public MobAIController(Mob mob, MobAIProfile profile) {
        this.mob = mob;
        this.profile = profile;
    }

    public Mob mob() {
        return mob;
    }

    public MobAIProfile profile() {
        return profile;
    }

    // =====================================================
    // MAIN AI TICK
    // =====================================================
    public void tick(long tick) {
        if (!mob.isValid() || mob.isDead()) return;

        // ---------- FLEE ----------
        if (profile.fleeEnabled()
                && mob.getHealth() / mob.getMaxHealth() <= profile.fleeThreshold()) {

            if (state != BehaviorState.FLEE) {
                state = BehaviorState.FLEE;
                fleeUntil = tick + profile.regroupTicks();
                mob.setTarget(null);
            }
            return;
        }

        if (state == BehaviorState.FLEE) {
            if (tick < fleeUntil) return;
            state = BehaviorState.REGROUP;
        }

        Player target = resolveTarget(tick);
        if (target == null) {
            if (state != BehaviorState.IDLE) {
                state = BehaviorState.IDLE;
                mob.setTarget(null);
            }
            return;
        }

        double distSq = mob.getLocation().distanceSquared(target.getLocation());
        double disengageSq = profile.disengageDistance() * profile.disengageDistance();

        // ---------- DISENGAGE (GRACE) ----------
        if (distSq >= disengageSq) {
            if (disengageGraceUntil == 0) {
                disengageGraceUntil = tick + 40; // 2s
            }
            if (tick < disengageGraceUntil) return;

            disengageGraceUntil = 0;
            state = BehaviorState.IDLE;
            currentTarget = null;
            disengageUntil = tick + profile.switchCooldown();
            mob.setTarget(null);
            return;
        }

        disengageGraceUntil = 0;

        // ---------- ENGAGE ----------
        if (state != BehaviorState.ENGAGE) {
            state = BehaviorState.ENGAGE;
            mob.setTarget(target);

            // Rotation nur beim Wechsel
            Location loc = mob.getLocation();
            loc.setDirection(
                    target.getLocation().toVector().subtract(loc.toVector())
            );
            mob.teleport(loc);
        }
    }

    // =====================================================
    // TARGET RESOLUTION
    // =====================================================
    private Player resolveTarget(long tick) {

        if (tick < disengageUntil) return null;

        if (currentTarget != null
                && tick - lastSwitchTick < profile.switchCooldown()
                && currentTarget.isOnline()
                && !currentTarget.isDead()) {
            return currentTarget;
        }

        Player p = profile.targeting().findTarget(mob);
        if (p == null) return null;

        if (p.getGameMode() == GameMode.CREATIVE
                || p.getGameMode() == GameMode.SPECTATOR) {
            return null;
        }

        currentTarget = p;
        lastSwitchTick = tick;
        return p;
    }

    public int tickRate() {
        return profile.tickRate();
    }
}
