package org.plugin.theMob.boss.behavior.core;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.util.Vector;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.boss.BossPhase;
import org.plugin.theMob.boss.behavior.*;

/**
 * Central-tick combat with wind-up, leash and per-instance cooldown; no repeating per-entity tasks.
 */
public final class TacticalBehavior implements BossBehavior {
  private static final class State {
    Location anchor;
    UUID target;
    long due, release;
    Location attackAt;
    boolean ai, aware;
    final Map<Arrow, Long> arrows = new HashMap<>();
  }

  private final String mode;
  private final TheMob plugin;
  private final Map<UUID, State> states = new HashMap<>();

  public TacticalBehavior(TheMob plugin, String mode) {
    this.plugin = plugin;
    this.mode = mode;
  }

  public String id() {
    return mode;
  }

  public void onEnter(LivingEntity boss, BossPhase phase) {
    State state = new State();
    state.anchor = boss.getLocation().clone();
    var data = boss.getPersistentDataContainer();
    for (String axis : List.of("x", "y", "z")) {
      var key = new NamespacedKey(plugin, "boss_home_" + axis);
      Double value = data.get(key, org.bukkit.persistence.PersistentDataType.DOUBLE);
      double coordinate =
          switch (axis) {
            case "x" -> state.anchor.getX();
            case "y" -> state.anchor.getY();
            default -> state.anchor.getZ();
          };
      if (value == null)
        data.set(key, org.bukkit.persistence.PersistentDataType.DOUBLE, coordinate);
      else
        switch (axis) {
          case "x" -> state.anchor.setX(value);
          case "y" -> state.anchor.setY(value);
          default -> state.anchor.setZ(value);
        }
    }
    state.ai = boss.hasAI();
    state.aware = !(boss instanceof Mob mob) || mob.isAware();
    state.due = plugin.automation().tps().currentTick() + 40;
    states.put(boss.getUniqueId(), state);
    boss.setAI(true);
    if (boss instanceof Mob mob) mob.setAware(true);
  }

  public void onExit(LivingEntity boss, BossPhase phase) {
    State state = states.remove(boss.getUniqueId());
    if (state == null) return;
    state.arrows.keySet().forEach(Arrow::remove);
    if (boss instanceof Mob mob) {
      mob.setTarget(null);
      mob.setAware(state.aware);
    }
    boss.setAI(state.ai);
  }

  public void onSuspend(LivingEntity boss, BossPhase phase) {
    State state = states.get(boss.getUniqueId());
    if (state != null) {
      state.arrows.keySet().forEach(Arrow::remove);
      state.arrows.clear();
      state.release = 0;
      state.due = plugin.automation().tps().currentTick() + 40;
    }
  }

  public void tick(LivingEntity boss, BossPhase phase) {
    State state = states.get(boss.getUniqueId());
    if (state == null || phase == null || phase.cfg() == null) return;
    long now = plugin.automation().tps().currentTick();
    state
        .arrows
        .entrySet()
        .removeIf(
            entry -> {
              if (!entry.getKey().isValid() || now >= entry.getValue()) {
                entry.getKey().remove();
                return true;
              }
              return false;
            });
    var cfg = phase.cfg();
    Location at = boss.getLocation();
    if (mode.equals("guardian")
        && at.getWorld().equals(state.anchor.getWorld())
        && at.distanceSquared(state.anchor) > 144) {
      move(boss, state.anchor.toVector().subtract(at.toVector()), .25);
      if (boss instanceof Mob mob) mob.setTarget(null);
      state.release = 0;
      return;
    }
    double leash =
        org.plugin.theMob.compat.Numbers.clamp(cfg.getDouble("behavior.leash-radius", 48), 8, 128);
    if (!at.getWorld().equals(state.anchor.getWorld())
        || at.distanceSquared(state.anchor) > leash * leash) {
      state.release = 0;
      state.target = null;
      if (boss instanceof Mob mob) mob.setTarget(null);
      if (at.getWorld().equals(state.anchor.getWorld()))
        move(boss, state.anchor.toVector().subtract(at.toVector()), .25);
      return;
    }
    LivingEntity target = BossTargets.combatResolve(state.target);
    double radius =
        org.plugin.theMob.compat.Numbers.clamp(cfg.getDouble("behavior.range", 24), 4, 64);
    if (!BossTargets.combatValid(target)
        || target.getWorld() != at.getWorld()
        || target.getLocation().distanceSquared(at) > radius * radius)
      target = BossTargets.combatNearest(boss, radius);
    state.target = target == null ? null : target.getUniqueId();
    if (target == null) {
      state.release = 0;
      if (boss instanceof Mob mob) mob.setTarget(null);
      return;
    }
    if (boss instanceof Mob mob && mob.getTarget() != target) mob.setTarget(target);
    Vector direction = target.getLocation().toVector().subtract(at.toVector());
    double distance = direction.lengthSquared();
    if (mode.equals("ranged") && distance < 64) move(boss, direction.clone().multiply(-1), .22);
    else if (mode.equals("berserker") && distance > 9 && distance < 225 && now % 40 < 10)
      move(boss, direction, .45);
    if (state.release > 0) {
      if (now >= state.release) {
        if (!plugin.activity().active(boss)) return;
        double damage =
            org.plugin.theMob.compat.Numbers.clamp(
                cfg.getDouble("behavior.attack-damage", 4), 0, 40);
        if (mode.equals("ranged")) {
          if (boss.hasLineOfSight(target)) {
            Vector velocity =
                target.getEyeLocation().toVector().subtract(boss.getEyeLocation().toVector());
            if (velocity.lengthSquared() > .001) {
              Arrow arrow = boss.launchProjectile(Arrow.class, velocity.normalize().multiply(1.5));
              arrow.setDamage(damage);
              arrow
                  .getPersistentDataContainer()
                  .set(
                      new NamespacedKey(plugin, "catalog_skill"),
                      org.bukkit.persistence.PersistentDataType.BYTE,
                      (byte) 1);
              arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
              arrow.setPersistent(false);
              state.arrows.put(arrow, now + 120);
            }
          }
        } else {
          for (LivingEntity player : BossTargets.combatNearby(plugin, state.attackAt, 4)) {
            if (!BossTargets.combatValid(player)
                || player.getLocation().distanceSquared(state.attackAt) > 16
                || !boss.hasLineOfSight(player)) continue;
            player.damage(damage, boss);
            Vector knockback = player.getLocation().toVector().subtract(state.attackAt.toVector());
            if (knockback.lengthSquared() > .001)
              player.setVelocity(
                  knockback.normalize().multiply(mode.equals("guardian") ? .8 : .45).setY(.25));
          }
        }
        state.release = 0;
        state.due =
            now
                + org.plugin.theMob.compat.Numbers.clamp(
                    cfg.getLong("behavior.cooldown-ticks", 120), 40, 1200);
      }
      return;
    }
    if (now >= state.due && (mode.equals("ranged") || distance <= 36)) {
      state.attackAt = boss.getLocation().clone();
      state.release =
          now
              + org.plugin.theMob.compat.Numbers.clamp(
                  cfg.getLong("behavior.windup-ticks", 20), 10, 100);
      boss.getWorld()
          .spawnParticle(
              org.plugin.theMob.compat.BukkitTypes.particle("SWEEP_ATTACK"),
              state.attackAt.clone().add(0, 1, 0),
              6,
              1.5,
              .1,
              1.5,
              0);
      boss.getWorld().playSound(at, Sound.ENTITY_IRON_GOLEM_ATTACK, .6f, .7f);
    }
  }

  private void move(LivingEntity boss, Vector direction, double speed) {
    direction.setY(0);
    if (direction.lengthSquared() < .001) return;
    Location at = boss.getLocation();
    Vector velocity = direction.normalize().multiply(speed);
    Location next = at.clone().add(velocity.clone().multiply(2));
    World world = next.getWorld();
    if (!world.isChunkLoaded(next.getBlockX() >> 4, next.getBlockZ() >> 4)) return;
    if (!next.getBlock().isPassable() || !next.clone().add(0, 1, 0).getBlock().isPassable()) return;
    if (next.clone().subtract(0, 1, 0).getBlock().isPassable()) return;
    velocity.setY(Math.min(.3, boss.getVelocity().getY()));
    boss.setVelocity(velocity);
  }
}
