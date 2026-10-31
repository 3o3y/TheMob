package org.plugin.theMob.boss.behavior;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.boss.BossPhase;
import org.plugin.theMob.boss.behavior.core.AggressiveBehavior;
import org.plugin.theMob.boss.behavior.core.FleeBehavior;
import org.plugin.theMob.boss.behavior.core.PassiveBehavior;
import org.plugin.theMob.boss.phase.BossPhaseController;
import org.plugin.theMob.mob.MobManager;

public final class BossBehaviorController {
  private final Map<String, BossBehavior> registry = new HashMap<>();
  private final Map<UUID, BossBehavior> activeBehavior = new HashMap<>();
  private final Map<UUID, BossPhase> lastPhase = new HashMap<>();
  private BukkitRunnable task;
  private BukkitRunnable motionTask;
  private final TheMob plugin;

  public BossBehaviorController(
      TheMob plugin, final MobManager mobs, final BossPhaseController phases) {
    this.plugin = plugin;
    this.register(new AggressiveBehavior());
    this.register(new PassiveBehavior());
    this.register(new FleeBehavior());
    this.register(new org.plugin.theMob.boss.behavior.core.VanillaBehavior());
    var zombie = new org.plugin.theMob.boss.behavior.core.ZombieKingBehavior(plugin);
    this.register(zombie);
    Bukkit.getPluginManager().registerEvents(zombie, plugin);
    this.motionTask =
        new BukkitRunnable() {
          @Override
          public void run() {
            zombie.tickMovement();
          }
        };
    this.motionTask.runTaskTimer(plugin, 2L, 2L);
    for (String mode : java.util.List.of("bruiser", "ranged", "guardian", "berserker"))
      this.register(new org.plugin.theMob.boss.behavior.core.TacticalBehavior(plugin, mode));
    this.task =
        new BukkitRunnable() {
          public void run() {
            for (LivingEntity boss : phases.activeBosses()) {
              if (boss != null && plugin.activity() != null && !plugin.activity().active(boss)) {
                BossBehavior behavior = activeBehavior.get(boss.getUniqueId());
                if (behavior != null) behavior.onSuspend(boss, lastPhase.get(boss.getUniqueId()));
                continue;
              }
              if (boss != null && boss.isValid() && !boss.isDead() && mobs.isBoss(boss)) {
                BossPhase phase = phases.currentPhase(boss);
                if (phase != null && phase.cfg() != null) {
                  UUID id = boss.getUniqueId();
                  BossBehavior previous = BossBehaviorController.this.activeBehavior.get(id);
                  BossPhase previousPhase = BossBehaviorController.this.lastPhase.get(id);
                  boolean phaseChanged =
                      previousPhase == null
                          || previousPhase.id() == null
                          || !previousPhase.id().equals(phase.id());
                  if (previous != null) {
                    String override = previous.requestNextBehavior(boss, phase);
                    if (override != null) {
                      BossBehavior forced =
                          BossBehaviorController.this.registry.get(allowedMode(override));
                      if (forced != null && forced != previous) {
                        BossBehaviorController.this.safeExit(previous, boss, previousPhase);
                        BossBehaviorController.this.safeEnter(forced, boss, phase);
                        BossBehaviorController.this.activeBehavior.put(id, forced);
                        BossBehaviorController.this.lastPhase.put(id, phase);
                        BossBehaviorController.this.safeTick(forced, boss, phase);
                        continue;
                      }
                    }
                  }

                  String mode =
                      phase.cfg().getString("behavior.mode", "aggressive").toLowerCase(Locale.ROOT);
                  BossBehavior next = BossBehaviorController.this.registry.get(allowedMode(mode));
                  if (next != previous || phaseChanged) {
                    if (previous != null) {
                      BossBehaviorController.this.safeExit(previous, boss, previousPhase);
                    }

                    if (next != null) {
                      BossBehaviorController.this.safeEnter(next, boss, phase);
                    }

                    BossBehaviorController.this.activeBehavior.put(id, next);
                  }

                  BossBehaviorController.this.lastPhase.put(id, phase);
                  if (next != null) {
                    BossBehaviorController.this.safeTick(next, boss, phase);
                  }
                }
              }
            }
          }
        };
    this.task.runTaskTimer(plugin, 1L, 10L);
  }

  public void shutdown() {
    if (this.motionTask != null) {
      this.motionTask.cancel();
      this.motionTask = null;
    }
    if (this.task != null) {
      this.task.cancel();
      this.task = null;
    }

    for (Entry<UUID, BossBehavior> e : this.activeBehavior.entrySet()) {
      LivingEntity boss = (LivingEntity) Bukkit.getEntity(e.getKey());
      if (boss != null && boss.isValid()) {
        BossPhase p = this.lastPhase.get(e.getKey());

        try {
          if (e.getValue() != null) e.getValue().onExit(boss, p);
        } catch (Throwable var6) {
        }
      }
    }

    this.activeBehavior.clear();
    this.lastPhase.clear();
    this.registry.clear();
  }

  public void onBossSpawn(LivingEntity boss, BossPhase phase) {
    if (boss != null && phase != null && phase.cfg() != null) {
      UUID id = boss.getUniqueId();
      BossBehavior prev = this.activeBehavior.remove(id);
      BossPhase prevPhase = this.lastPhase.remove(id);
      if (prev != null) {
        this.safeExit(prev, boss, prevPhase);
      }

      String mode = phase.cfg().getString("behavior.mode", "aggressive").toLowerCase(Locale.ROOT);
      BossBehavior next = this.registry.get(allowedMode(mode));
      if (next != null) {
        this.safeEnter(next, boss, phase);
        this.activeBehavior.put(id, next);
        this.lastPhase.put(id, phase);
        this.safeTick(next, boss, phase);
      }
    }
  }

  public void onBossDeath(LivingEntity boss) {
    if (boss != null) {
      UUID id = boss.getUniqueId();
      BossBehavior b = this.activeBehavior.remove(id);
      BossPhase p = this.lastPhase.remove(id);
      if (b != null) {
        this.safeExit(b, boss, p);
      }
    }
  }

  private void safeEnter(BossBehavior b, LivingEntity e, BossPhase p) {
    try {
      b.onEnter(e, p);
    } catch (Throwable var5) {
    }
  }

  private String allowedMode(String mode) {
    mode = mode.toLowerCase(Locale.ROOT);
    return mode.equals("vanilla") || plugin.abilities().enabled("bosses." + mode)
        ? mode
        : "vanilla";
  }

  private void safeExit(BossBehavior b, LivingEntity e, BossPhase p) {
    try {
      b.onExit(e, p);
    } catch (Throwable var5) {
    }
  }

  private void safeTick(BossBehavior b, LivingEntity e, BossPhase p) {
    try {
      b.tick(e, p);
    } catch (Throwable var5) {
    }
  }

  public void register(BossBehavior behavior) {
    if (behavior != null && behavior.id() != null) {
      this.registry.put(behavior.id().toLowerCase(Locale.ROOT), behavior);
    }
  }
}
