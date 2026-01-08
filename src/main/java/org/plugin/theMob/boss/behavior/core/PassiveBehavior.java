package org.plugin.theMob.boss.behavior.core;

import org.bukkit.entity.LivingEntity;
import org.plugin.theMob.boss.BossPhase;
import org.plugin.theMob.boss.behavior.BossBehavior;

public final class PassiveBehavior implements BossBehavior {

    @Override
    public String id() {
        return "passive";
    }

    @Override
    public void onEnter(LivingEntity boss, BossPhase phase) {
        if (boss == null || phase == null || !boss.isValid() || boss.isDead()) return;
        boss.setAI(false);
    }

    @Override
    public void onExit(LivingEntity boss, BossPhase phase) {
        if (boss == null || phase == null || !boss.isValid() || boss.isDead()) return;
        boss.setAI(true);
    }
}
