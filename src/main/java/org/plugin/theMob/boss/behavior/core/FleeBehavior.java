package org.plugin.theMob.boss.behavior.core;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.plugin.theMob.boss.BossPhase;
import org.plugin.theMob.boss.behavior.BossBehavior;

public final class FleeBehavior implements BossBehavior {
  private static final double FEAR_RADIUS = 10.0;
  private static final double RUN_SPEED = 0.3;
  private static final double STEP_HEIGHT = 2.5;
  private static final double BASE_Y = 0.08;
  private static final double JUMP_Y = 0.42;

  @Override
  public String id() {
    return "flee";
  }

  @Override
  public void onEnter(LivingEntity boss, BossPhase phase) {
    if (boss instanceof Mob mob) {
      mob.setAI(true);
      mob.setAware(false);
      mob.setTarget(null);
      boss.setVelocity(new Vector(0, 0, 0));
    }
  }

  @Override
  public void tick(LivingEntity boss, BossPhase phase) {
    if (boss instanceof Mob mob) {
      mob.setTarget(null);
      Player player = this.nearestPlayer(boss);
      if (player != null) {
        Location loc = boss.getLocation();
        if (loc.distanceSquared(player.getLocation()) >= 100.0) {
          boss.setVelocity(new Vector(0, 0, 0));
        } else {
          Vector fleeDir = loc.toVector().subtract(player.getLocation().toVector());
          if (fleeDir.lengthSquared() < .001) return;
          fleeDir.normalize();
          Vector velocity = fleeDir.multiply(0.3);
          velocity.setY(this.shouldStepUp(loc, fleeDir, 2.5) ? 0.42 : 0.08);
          boss.setVelocity(velocity);
          boss.setRotation(
              (float) Math.toDegrees(Math.atan2(-fleeDir.getX(), fleeDir.getZ())), loc.getPitch());
        }
      }
    }
  }

  private Player nearestPlayer(LivingEntity boss) {
    return org.plugin.theMob.boss.behavior.BossTargets.nearest(boss, 10);
  }

  private boolean shouldStepUp(Location from, Vector dir, double maxStep) {
    int steps = (int) Math.ceil(maxStep * 2.0);

    for (int i = 1; i <= steps; i++) {
      double y = i * 0.5;
      Location base = from.clone().add(dir).add(0.0, y, 0.0);
      if (!base.getWorld().isChunkLoaded(base.getBlockX() >> 4, base.getBlockZ() >> 4)) continue;
      if (base.clone().add(0.0, -0.5, 0.0).getBlock().getType().isSolid()
          && base.getBlock().getType().isAir()
          && base.clone().add(0.0, 1.0, 0.0).getBlock().getType().isAir()) {
        return true;
      }
    }

    return false;
  }
}
