package org.plugin.theMob.boss.behavior.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.scoreboard.Team.Option;
import org.bukkit.scoreboard.Team.OptionStatus;
import org.bukkit.util.Vector;
import org.plugin.theMob.boss.BossPhase;
import org.plugin.theMob.boss.behavior.BossBehavior;

public final class AggressiveBehavior implements BossBehavior {
  private static final double AGGRO_RADIUS = 18.0;
  private static final double MOVE_SPEED = 0.32;
  private static final double BASE_Y = 0.08;
  private static final double JUMP_Y = 0.48;
  private static final int STUCK_TICKS = 6;
  private static final double MIN_PROGRESS_SQ = 0.015;
  private static final int LOCAL_TP_RADIUS = 2;
  private static final int TP_ATTEMPTS = 20;
  private static final double DASH_DISTANCE = 3.2;
  private static final double DASH_FORCE = 0.65;
  private static final String AGGRO_TEAM = "theMob_aggressive";
  private final Map<UUID, Integer> stuckTicks = new HashMap<>();
  private final Map<UUID, Location> lastPos = new HashMap<>();
  private final Map<UUID, Double> originalAttackSpeed = new HashMap<>();
  private final Map<UUID, Boolean> glowing = new HashMap<>();
  private final Map<UUID, Long> lastDash = new HashMap<>();

  @Override
  public String id() {
    return "aggressive";
  }

  @Override
  public void onEnter(LivingEntity boss, BossPhase phase) {
    if (boss instanceof Mob mob) {
      UUID id = boss.getUniqueId();
      mob.setAI(true);
      mob.setAware(true);
      mob.setSilent(false);
      this.applyRedGlow(boss);
      this.boostAttackSpeed(boss);
      this.stuckTicks.put(id, 0);
      this.lastPos.put(id, boss.getLocation().clone());
      this.glowing.put(id, true);
    }
  }

  @Override
  public void onExit(LivingEntity boss, BossPhase phase) {
    if (boss instanceof Mob mob) {
      UUID id = boss.getUniqueId();
      mob.setTarget(null);
      this.removeRedGlow(boss);
      this.restoreAttackSpeed(boss);
      this.stuckTicks.remove(id);
      this.lastPos.remove(id);
      this.glowing.remove(id);
      this.lastDash.remove(id);
    }
  }

  @Override
  public void tick(LivingEntity boss, BossPhase phase) {
    if (boss instanceof Mob mob) {
      UUID id = boss.getUniqueId();
      LivingEntity target = this.findNearestTarget(boss);
      if (target != null) {
        mob.setTarget(target);
        Location loc = boss.getLocation();
        Vector dir = target.getLocation().toVector().subtract(loc.toVector());
        double distSq = dir.lengthSquared();
        if (!(distSq < 1.0E-4)) {
          dir.normalize();
          // Native AI handles ordinary navigation; velocity is only used for a cooldown-limited
          // dash.
          boss.setRotation(this.yawFrom(dir), loc.getPitch());
          long now = System.nanoTime();
          if (distSq <= 10.24
              && now - lastDash.getOrDefault(id, Long.MIN_VALUE / 2) >= 2_000_000_000L) {
            Vector dash = dir.clone().multiply(0.65);
            dash.setY(0.15);
            boss.setVelocity(dash);
            lastDash.put(id, now);
          }

          Location last = this.lastPos.get(id);
          int stuck = this.stuckTicks.getOrDefault(id, 0);
          if (last != null && loc.distanceSquared(last) < 0.015) {
            stuck++;
          } else {
            stuck = 0;
          }

          this.stuckTicks.put(id, stuck);
          this.lastPos.put(id, loc.clone());
          if (stuck >= 6) {
            Location safe = this.findLocalSafeSpot(boss);
            if (safe != null) {
              boss.teleport(safe);
            }

            this.stuckTicks.put(id, 0);
          }
        }
      }
    }
  }

  private void applyRedGlow(LivingEntity boss) {
    Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
    Team team = sb.getTeam("theMob_aggressive");
    if (team == null) {
      team = sb.registerNewTeam("theMob_aggressive");
      team.setColor(ChatColor.RED);
      team.setOption(Option.NAME_TAG_VISIBILITY, OptionStatus.NEVER);
      team.setOption(Option.COLLISION_RULE, OptionStatus.NEVER);
    }

    String entry = boss.getUniqueId().toString();
    team.addEntry(entry);
    boss.setGlowing(true);
  }

  private void removeRedGlow(LivingEntity boss) {
    Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
    Team team = sb.getTeam("theMob_aggressive");
    if (team != null) {
      team.removeEntry(boss.getUniqueId().toString());
    }

    boss.setGlowing(false);
  }

  private void boostAttackSpeed(LivingEntity boss) {
    AttributeInstance attr = org.plugin.theMob.compat.BukkitTypes.instance(boss, "ATTACK_SPEED");
    if (attr != null) {
      this.originalAttackSpeed.putIfAbsent(boss.getUniqueId(), attr.getBaseValue());
      attr.setBaseValue(this.originalAttackSpeed.get(boss.getUniqueId()) * 2.0);
    }
  }

  private void restoreAttackSpeed(LivingEntity boss) {
    AttributeInstance attr = org.plugin.theMob.compat.BukkitTypes.instance(boss, "ATTACK_SPEED");
    if (attr != null) {
      Double original = this.originalAttackSpeed.remove(boss.getUniqueId());
      if (original != null) {
        attr.setBaseValue(original);
      }
    }
  }

  private LivingEntity findNearestTarget(LivingEntity boss) {
    return org.plugin.theMob.boss.behavior.BossTargets.combatNearest(boss, 18);
  }

  private boolean shouldStepUp(Location from, Vector dir, int maxStep) {
    for (int y = 1; y <= maxStep; y++) {
      Location base = from.clone().add(dir).add(0.0, y, 0.0);
      if (base.clone().add(0.0, -1.0, 0.0).getBlock().getType().isSolid()
          && base.getBlock().getType().isAir()
          && base.clone().add(0.0, 1.0, 0.0).getBlock().getType().isAir()) {
        return true;
      }
    }

    return false;
  }

  private Location findLocalSafeSpot(LivingEntity boss) {
    Location center = boss.getLocation();

    for (int i = 0; i < 20; i++) {
      int dx = (int) (Math.random() * 5.0) - 2;
      int dy = (int) (Math.random() * 5.0) - 2;
      int dz = (int) (Math.random() * 5.0) - 2;
      Location l = center.clone().add(dx, dy, dz);
      if (!l.getWorld().isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4)) continue;
      if (l.clone().add(0.0, -1.0, 0.0).getBlock().getType().isSolid()
          && l.getBlock().getType().isAir()
          && l.clone().add(0.0, 1.0, 0.0).getBlock().getType().isAir()) {
        return l;
      }
    }

    return null;
  }

  private int resolveStepHeight(LivingEntity boss) {
    AttributeInstance scale = org.plugin.theMob.compat.BukkitTypes.instance(boss, "SCALE");
    return scale != null && scale.getValue() > 1.3 ? 2 : 1;
  }

  private float yawFrom(Vector v) {
    return (float) Math.toDegrees(Math.atan2(-v.getX(), v.getZ()));
  }
}
