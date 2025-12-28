package org.plugin.theMob.boss.behavior.core;

import org.bukkit.entity.LivingEntity;
import org.plugin.theMob.boss.BossPhase;
import org.plugin.theMob.boss.behavior.BossBehavior;

public final class AggressiveBehavior implements BossBehavior {

    @Override
    public String id() {
        return "aggressive";
    }

    @Override
    public void tick(LivingEntity boss, BossPhase phase) {
        // Vanilla AI does everything
    }
}
