package org.plugin.theMob.boss.phase;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.plugin.theMob.boss.BossPhase;
import org.plugin.theMob.boss.BossTemplate;

public final class BossPhaseResolver {

    public BossPhase resolve(LivingEntity boss, BossTemplate tpl) {
        if (boss == null || tpl == null) return null;
        if (!boss.isValid() || boss.isDead()) return null;

        AttributeInstance maxAttr = boss.getAttribute(Attribute.MAX_HEALTH);
        if (maxAttr == null) return null;

        double max = maxAttr.getValue();
        if (max <= 0.0) return null;

        double hp = Math.max(0.0, boss.getHealth());
        double hpPercent = (hp / max) * 100.0;

        return tpl.resolvePhase(hpPercent);
    }
}
