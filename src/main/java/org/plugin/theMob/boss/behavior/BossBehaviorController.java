package org.plugin.theMob.boss.behavior;

import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;
import org.plugin.theMob.TheMob;
import org.plugin.theMob.boss.BossPhase;
import org.plugin.theMob.boss.phase.BossPhaseController;
import org.plugin.theMob.mob.MobManager;

import java.util.HashMap;
import java.util.Map;

public final class BossBehaviorController {

    // MAIN THREAD ONLY
    private final Map<String, BossBehavior> registry = new HashMap<>();
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
                    if (phase == null) continue;

                    String mode = phase.cfg()
                            .getString("behavior.mode", "aggressive")
                            .toLowerCase();

                    BossBehavior behavior = registry.get(mode);
                    if (behavior != null) {
                        behavior.tick(boss, phase);
                    }
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
        registry.clear();
    }

    public void register(BossBehavior behavior) {
        if (behavior == null) return;
        String id = behavior.id();
        if (id == null || id.isBlank()) return;

        registry.put(id.toLowerCase(), behavior);
    }
}
