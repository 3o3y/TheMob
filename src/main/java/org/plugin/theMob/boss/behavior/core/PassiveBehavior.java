package org.plugin.theMob.boss.behavior.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.plugin.theMob.boss.BossPhase;
import org.plugin.theMob.boss.behavior.BossBehavior;

public final class PassiveBehavior implements BossBehavior {
  private static final double MOVE_SPEED = 0.12;
  private static final double BASE_Y = 0.04;
  private static final double JUMP_Y = 0.3;
  private static final int IDLE_MIN = 40;
  private static final int IDLE_MAX = 120;
  private static final int WALK_MIN = 20;
  private static final int WALK_MAX = 50;
  private static final double LOOK_RADIUS = 12.0;
  private final Map<UUID, Boolean> walking = new HashMap<>();
  private final Map<UUID, Integer> stateTicks = new HashMap<>();
  private final Map<UUID, Vector> walkDir = new HashMap<>();

  @Override
  public String id() {
    return "passive";
  }

  @Override
  public void onEnter(LivingEntity boss, BossPhase phase) {
    if (boss instanceof Mob mob) {
      UUID id = boss.getUniqueId();
      mob.setAI(false);
      mob.setAware(false);
      mob.setTarget(null);
      this.walking.put(id, false);
      this.stateTicks.put(id, this.rand(40, 120));
      this.walkDir.put(id, this.randomDir());
      boss.setVelocity(new Vector(0, 0, 0));
    }
  }

  @Override
  public void onExit(LivingEntity boss, BossPhase phase) {
    UUID id = boss.getUniqueId();
    this.walking.remove(id);
    this.stateTicks.remove(id);
    this.walkDir.remove(id);
  }

  @Override
  public void tick(LivingEntity boss, BossPhase phase) {
    if (boss instanceof Mob mob) {
      mob.setTarget(null);
      UUID id = boss.getUniqueId();
      this.lookAtNearestPlayer(boss);
      int t = this.stateTicks.getOrDefault(id, 0) - 1;
      boolean isWalking = this.walking.getOrDefault(id, false);
      if (t <= 0) {
        isWalking = !isWalking;
        this.walking.put(id, isWalking);
        this.stateTicks.put(id, isWalking ? this.rand(20, 50) : this.rand(40, 120));
        this.walkDir.put(id, this.randomDir());
        if (!isWalking) {
          boss.setVelocity(new Vector(0, 0, 0));
        }
      } else {
        this.stateTicks.put(id, t);
      }

      if (isWalking) {
        Vector dir = this.walkDir.get(id);
        Vector vel = dir.clone().multiply(0.12);
        vel.setY(
            this.shouldStepUp(boss.getLocation(), dir, this.resolveStepHeight(boss)) ? 0.3 : 0.04);
        boss.setVelocity(vel);
      }
    }
  }

  private void lookAtNearestPlayer(LivingEntity boss) {
    Player nearest = null;
    double best = 144.0;

    Player candidate = org.plugin.theMob.boss.behavior.BossTargets.nearest(boss, 12);
    for (Player p :
        candidate == null ? java.util.List.<Player>of() : java.util.List.of(candidate)) {
      if (p.hasMetadata("NPC")) continue;
      double d = p.getLocation().distanceSquared(boss.getLocation());
      if (d < best) {
        best = d;
        nearest = p;
      }
    }

    if (nearest != null) {
      Location loc = boss.getLocation();
      Location target = nearest.getLocation();
      Vector dir = target.toVector().subtract(loc.toVector());
      loc.setYaw((float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ())));
      boss.setRotation(loc.getYaw(), loc.getPitch());
    }
  }

  private Vector randomDir() {
    double a = Math.random() * Math.PI * 2.0;
    return new Vector(Math.cos(a), 0.0, Math.sin(a)).normalize();
  }

  private boolean shouldStepUp(Location from, Vector dir, int maxStep) {
    for (int y = 1; y <= maxStep; y++) {
      Location base = from.clone().add(dir).add(0.0, y, 0.0);
      if (!base.getWorld().isChunkLoaded(base.getBlockX() >> 4, base.getBlockZ() >> 4)) continue;
      if (base.clone().add(0.0, -1.0, 0.0).getBlock().getType().isSolid()
          && base.getBlock().getType().isAir()
          && base.clone().add(0.0, 1.0, 0.0).getBlock().getType().isAir()) {
        return true;
      }
    }

    return false;
  }

  private int resolveStepHeight(LivingEntity boss) {
    AttributeInstance scale = org.plugin.theMob.compat.BukkitTypes.instance(boss, "SCALE");
    double s = scale != null ? scale.getValue() : 1.0;
    return s <= 1.3 ? 1 : 2;
  }

  private int rand(int min, int max) {
    return min + (int) (Math.random() * (max - min + 1));
  }
}
