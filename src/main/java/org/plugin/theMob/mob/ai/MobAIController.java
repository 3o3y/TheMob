package org.plugin.theMob.mob.ai;

import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

public final class MobAIController {

    private final Mob mob;
    private final MobAIProfile profile;

    private BehaviorState state = BehaviorState.IDLE;
    private Player currentTarget;
    private long lastSwitchTick;
    private long fleeUntil;

    public MobAIController(Mob mob, MobAIProfile profile) {
        this.mob = mob;
        this.profile = profile;
    }

    public void tick(long tick) {
        if (!mob.isValid() || mob.isDead()) return;

        // -----------------------------
        // FLEE LOGIC
        // -----------------------------
        if (profile.fleeEnabled()
                && mob.getHealth() / mob.getMaxHealth() <= profile.fleeThreshold()) {

            state = BehaviorState.FLEE;
            fleeUntil = tick + profile.regroupTicks();
            mob.setTarget(null);
            return;
        }

        if (state == BehaviorState.FLEE) {
            if (tick < fleeUntil) {
                mob.setTarget(null);
                return;
            }
            state = BehaviorState.REGROUP;
        }

        // -----------------------------
        // TARGETING
        // -----------------------------
        Player target = resolveTarget(tick);
        if (target == null) {
            state = BehaviorState.IDLE;
            mob.setTarget(null);
            return;
        }

        double dist = target.getLocation().distance(mob.getLocation());

        if (dist <= profile.engageDistance()) {
            state = BehaviorState.ENGAGE;
            mob.setTarget(target);
        } else if (dist >= profile.disengageDistance()) {
            state = BehaviorState.IDLE;
            mob.setTarget(null);
        }
    }

    private Player resolveTarget(long tick) {
        if (currentTarget != null && tick - lastSwitchTick < profile.switchCooldown()) {
            if (currentTarget.isOnline() && !currentTarget.isDead()) {
                return currentTarget;
            }
        }

        Player p = profile.targeting().findTarget(mob);
        if (p != null) {
            currentTarget = p;
            lastSwitchTick = tick;
        }
        return p;
    }

    public int tickRate() {
        return profile.tickRate();
    }
}
