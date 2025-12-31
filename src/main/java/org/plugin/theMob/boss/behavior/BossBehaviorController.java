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

    private final Map<String, BossBehavior> registry = new HashMap<>();

    public BossBehaviorController(
            TheMob plugin,
            MobManager mobs,
            BossPhaseController phases
    ) {

        register(new org.plugin.theMob.boss.behavior.core.AggressiveBehavior());
        register(new org.plugin.theMob.boss.behavior.core.PassiveBehavior());
        register(new org.plugin.theMob.boss.behavior.core.FleeBehavior());

        new BukkitRunnable() {
            @Override
            public void run() {
                for (LivingEntity boss : phases.activeBosses()) {
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
        }.runTaskTimer(plugin, 1L, 10L);
    }

    public void register(BossBehavior behavior) {
        registry.put(behavior.id().toLowerCase(), behavior);
    }
}
