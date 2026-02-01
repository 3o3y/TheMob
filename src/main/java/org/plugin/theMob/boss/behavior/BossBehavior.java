package org.plugin.theMob.boss.behavior;

import org.bukkit.entity.LivingEntity;
import org.plugin.theMob.boss.BossPhase;

public interface BossBehavior {

    String id();

    default void onEnter(LivingEntity boss, BossPhase phase) {}
    default void onExit(LivingEntity boss, BossPhase phase) {}
    default void tick(LivingEntity boss, BossPhase phase) {}

    // 🔁 OPTIONAL: Wunsch nach Behavior-Wechsel
    default String requestNextBehavior(LivingEntity boss, BossPhase phase) {
        return null;
    }
}
