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

                    String mode = phase.cfg()
                            .getString("behavior.mode", "aggressive")
                            .toLowerCase(Locale.ROOT);

                    BossBehavior next = registry.get(mode);
                    UUID id = boss.getUniqueId();
                    BossBehavior previous = activeBehavior.get(id);

                    BossPhase currentPhase = phase;
                    BossPhase previousPhase = lastPhase.get(id);

                    if (next != previous) {
                        if (previous != null) previous.onExit(boss, previousPhase);
                        if (next != null) next.onEnter(boss, currentPhase);
                        activeBehavior.put(id, next);
                    }

                    lastPhase.put(id, currentPhase);

                    if (next != null) next.tick(boss, currentPhase);
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
                e.getValue().onExit(boss, lastPhase.get(e.getKey()));
            }
        }

        activeBehavior.clear();
        lastPhase.clear();
        registry.clear();
    }

    public void onBossSpawn(LivingEntity boss) {
        if (boss == null) return;
        UUID id = boss.getUniqueId();

        // ✅ absoluter Reset, egal was vorher war
        activeBehavior.remove(id);
        lastPhase.remove(id);
    }

    public void onBossDeath(LivingEntity boss) {
        if (boss == null) return;
        UUID id = boss.getUniqueId();

        // ✅ EXIT erzwingen, sonst bleiben Flags/States hängen
        BossBehavior b = activeBehavior.remove(id);
        BossPhase p = lastPhase.remove(id);

        if (b != null) {
            try {
                b.onExit(boss, p);
            } catch (Throwable ignored) {}
        }
    }

    public void register(BossBehavior behavior) {
        if (behavior == null) return;
        String id = behavior.id();
        if (id == null || id.isBlank()) return;
        registry.put(id.toLowerCase(Locale.ROOT), behavior);
    }
}
