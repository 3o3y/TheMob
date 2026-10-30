package org.plugin.theMob.mob.ability;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Effect;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public final class StuckDefensePath {
  private static final int CHECK_EVERY_TICKS = 20;
  private static final int STUCK_AFTER_TICKS = 60;
  private static final double MIN_PROGRESS = 0.6;
  private static final int SEARCH_RADIUS = 2;
  private static final int MAX_TRIES = 5;
  private static final int BREAK_COOLDOWN_TICKS = 20;
  private final Map<UUID, StuckDefensePath.State> states = new ConcurrentHashMap<>();
  private long globalTick = 0L;

  public void tick(LivingEntity mob) {
    // This grounded boss deliberately stands still for warnings/recovery. Do not teleport it or
    // break dungeon doors while its own controller handles movement and collisions.
    if (mob != null
        && mob.hasMetadata(org.plugin.theMob.boss.behavior.core.ZombieKingBehavior.METADATA))
      return;
    if (mob != null && mob.isValid() && !mob.isDead()) {
      this.globalTick++;
      if (this.globalTick % 20L == 0L) {
        Location now = mob.getLocation();
        UUID id = mob.getUniqueId();
        StuckDefensePath.State s =
            this.states.computeIfAbsent(id, k -> new StuckDefensePath.State(now));
        double moved = now.distance(s.lastLocation);
        if (moved >= 0.6) {
          s.lastLocation = now;
          s.stuckTicks = 0;
        } else {
          s.stuckTicks += 20;
          if (s.stuckTicks >= 60) {
            s.stuckTicks = 0;
            if (this.globalTick >= s.nextBreakTick && this.tryClearSoftObstacle(mob)) {
              s.nextBreakTick = this.globalTick + 20L;
            } else {
              this.attemptTeleport(mob, now);
              s.lastLocation = mob.getLocation();
            }
          }
        }
      }
    } else {
      if (mob != null) {
        this.states.remove(mob.getUniqueId());
      }
    }
  }

  public void remove(LivingEntity mob) {
    if (mob != null) {
      this.states.remove(mob.getUniqueId());
    }
  }

  public void clear() {
    this.states.clear();
  }

  private boolean tryClearSoftObstacle(LivingEntity mob) {
    if (mob instanceof Mob m) {
      World world = mob.getWorld();
      Boolean griefing = (Boolean) world.getGameRuleValue(GameRule.MOB_GRIEFING);
      if (griefing != null && griefing) {
        Location eye = mob.getLocation().add(0.0, 1.0, 0.0);
        Vector dir = eye.getDirection().normalize();

        for (int i = 1; i <= 2; i++) {
          Block b = eye.clone().add(dir.clone().multiply(i)).getBlock();
          if (this.isSoftObstacle(b.getType())) {
            this.breakBlockInstant(b);
            return true;
          }
        }

        return false;
      } else {
        return false;
      }
    } else {
      return false;
    }
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
    return n.startsWith("IRON_")
        ? false
        : n.endsWith("_LEAVES")
            || n.endsWith("_LOG")
            || n.endsWith("_WOOD")
            || n.endsWith("_DOOR")
            || n.endsWith("_TRAPDOOR")
            || mat == Material.VINE;
  }

  private void attemptTeleport(LivingEntity mob, Location from) {
    World world = from.getWorld();
    if (world != null) {
      Player target = null;
      if (mob instanceof Mob m && m.getTarget() instanceof Player p && mob.hasLineOfSight(p)) {
        target = p;
      }

      Vector baseDir =
          target != null
              ? target.getLocation().toVector().subtract(from.toVector()).normalize()
              : this.randomDir();
      Location base = from.clone().add(baseDir.multiply(2));

      for (int i = 0; i < 5; i++) {
        Location test = base.clone().add(this.rand(-2, 2), this.rand(-1, 1), this.rand(-2, 2));
        if (this.isSafe(test)) {
          this.spawnParticles(from);
          mob.teleport(test);
          this.spawnParticles(test);
          return;
        }
      }
    }
  }

  private Vector randomDir() {
    double x = Math.random() - 0.5;
    double z = Math.random() - 0.5;
    return new Vector(x, 0.0, z).normalize();
  }

  private int rand(int min, int max) {
    return min + (int) (Math.random() * (max - min + 1));
  }

  private boolean isSafe(Location loc) {
    Location feet = loc.clone();
    Location head = loc.clone().add(0.0, 1.0, 0.0);
    Location ground = loc.clone().add(0.0, -1.0, 0.0);
    return ground.getBlock().getType().isSolid()
        && feet.getBlock().getType().isAir()
        && head.getBlock().getType().isAir();
  }

  private void spawnParticles(Location loc) {
    World w = loc.getWorld();
    if (w != null) {
      w.spawnParticle(
          org.plugin.theMob.compat.BukkitTypes.particle("CLOUD"),
          loc.clone().add(0.0, 1.0, 0.0),
          30,
          0.4,
          0.6,
          0.4,
          0.01);
    }
  }

  private static final class State {
    Location lastLocation;
    int stuckTicks;
    long nextBreakTick;

    State(Location loc) {
      this.lastLocation = loc;
    }
  }
}
