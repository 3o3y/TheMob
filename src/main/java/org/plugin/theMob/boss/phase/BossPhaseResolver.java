package org.plugin.theMob.boss.phase;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.plugin.theMob.boss.BossPhase;
import org.plugin.theMob.boss.BossTemplate;

public final class BossPhaseResolver {

    public BossPhase resolve(LivingEntity boss, BossTemplate tpl) {
        if (boss == null || tpl == null) return null;
        AttributeInstance maxAttr = boss.getAttribute(Attribute.MAX_HEALTH);
        if (maxAttr == null || maxAttr.getValue() <= 0) return null;
        double hpPercent = (boss.getHealth() / maxAttr.getValue()) * 100.0;
        return tpl.findPhase(hpPercent);
    }
}
