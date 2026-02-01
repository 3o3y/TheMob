package org.plugin.theMob.boss.behavior;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.boss.BossPhase;
import org.plugin.theMob.boss.phase.BossPhaseController;
import org.plugin.theMob.mob.MobManager;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class BossBehaviorController {

    private final Map<String, BossBehavior> registry = new HashMap<>();
    private final Map<UUID, BossBehavior> activeBehavior = new HashMap<>();
    private final Map<UUID, BossPhase> lastPhase = new HashMap<>();

    private BukkitRunnable task;

    public BossBehaviorController(
            TheMob plugin,
            MobManager mobs,
            BossPhaseController phases
    ) {
        register(new org.plugin.theMob.boss.behavior.core.AggressiveBehavior());
        register(new org.plugin.theMob.boss.behavior.core.PassiveBehavior());
        register(new org.plugin.theMob.boss.behavior.core.FleeBehavior());

        task = new BukkitRunnable() {
            @Override
            public void run() {
                for (LivingEntity boss : phases.activeBosses()) {
                    if (boss == null || !boss.isValid() || boss.isDead()) continue;
                    if (!mobs.isBoss(boss)) continue;

                    BossPhase phase = phases.currentPhase(boss);
                    if (phase == null || phase.cfg() == null) continue;

                    UUID id = boss.getUniqueId();

                    BossBehavior previous = activeBehavior.get(id);
                    BossPhase previousPhase = lastPhase.get(id);

                    boolean phaseChanged =
                            previousPhase == null
                                    || previousPhase.id() == null
                                    || !previousPhase.id().equals(phase.id());

                    // -------- OVERRIDE --------
                    if (previous != null) {
                        String override = previous.requestNextBehavior(boss, phase);
                        if (override != null) {
                            BossBehavior forced = registry.get(override.toLowerCase(Locale.ROOT));
                            if (forced != null && forced != previous) {
                                safeExit(previous, boss, previousPhase);
                                safeEnter(forced, boss, phase);
                                activeBehavior.put(id, forced);
                                lastPhase.put(id, phase);
                                safeTick(forced, boss, phase);
                                continue;
                            }
                        }
                    }

                    // -------- NORMAL --------
                    String mode = phase.cfg()
                            .getString("behavior.mode", "aggressive")
                            .toLowerCase(Locale.ROOT);

                    BossBehavior next = registry.get(mode);

                    if (next != previous || phaseChanged) {
                        if (previous != null) safeExit(previous, boss, previousPhase);
                        if (next != null) safeEnter(next, boss, phase);
                        activeBehavior.put(id, next);
                    }

                    lastPhase.put(id, phase);
                    if (next != null) safeTick(next, boss, phase);
                }
            }
        };

        task.runTaskTimer(plugin, 1L, 10L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        for (Map.Entry<UUID, BossBehavior> e : activeBehavior.entrySet()) {
            LivingEntity boss = (LivingEntity) Bukkit.getEntity(e.getKey());
            if (boss != null && boss.isValid()) {
                BossPhase p = lastPhase.get(e.getKey());
                try { e.getValue().onExit(boss, p); } catch (Throwable ignored) {}
            }
        }

        activeBehavior.clear();
        lastPhase.clear();
        registry.clear();
    }

    // =========================
    // SPAWN / DEATH
    // =========================

    public void onBossSpawn(LivingEntity boss, BossPhase phase) {
        if (boss == null || phase == null || phase.cfg() == null) return;

        UUID id = boss.getUniqueId();

        BossBehavior prev = activeBehavior.remove(id);
        BossPhase prevPhase = lastPhase.remove(id);
        if (prev != null) safeExit(prev, boss, prevPhase);

        String mode = phase.cfg().getString("behavior.mode", "aggressive").toLowerCase(Locale.ROOT);
        BossBehavior next = registry.get(mode);

        if (next != null) {
            safeEnter(next, boss, phase);
            activeBehavior.put(id, next);
            lastPhase.put(id, phase);
            safeTick(next, boss, phase);
        }
    }

    public void onBossDeath(LivingEntity boss) {
        if (boss == null) return;
        UUID id = boss.getUniqueId();

        BossBehavior b = activeBehavior.remove(id);
        BossPhase p = lastPhase.remove(id);
        if (b != null) safeExit(b, boss, p);
    }

    // =========================
    // UTILS
    // =========================

    private void safeEnter(BossBehavior b, LivingEntity e, BossPhase p) {
        try { b.onEnter(e, p); } catch (Throwable ignored) {}
    }

    private void safeExit(BossBehavior b, LivingEntity e, BossPhase p) {
        try { b.onExit(e, p); } catch (Throwable ignored) {}
    }

    private void safeTick(BossBehavior b, LivingEntity e, BossPhase p) {
        try { b.tick(e, p); } catch (Throwable ignored) {}
    }

    public void register(BossBehavior behavior) {
        if (behavior == null || behavior.id() == null) return;
        registry.put(behavior.id().toLowerCase(Locale.ROOT), behavior);
    }
}
